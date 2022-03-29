package net.gauntletmc.adventure.serializer.binary;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/*package-private*/ final class BinaryComponentSerializerImpl implements BinaryComponentSerializer {

    private static final byte VERSION = 1;

    private static final byte COMPONENT_TEXT =         0;
    private static final byte COMPONENT_TRANSLATABLE = 1;
    private static final byte COMPONENT_SCORE =        2;
    private static final byte COMPONENT_SELECTOR =     3;
    private static final byte COMPONENT_KEYBIND =      4;
    private static final byte COMPONENT_BLOCK_NBT =    5;
    private static final byte COMPONENT_ENTITY_NBT =   6;
    private static final byte COMPONENT_STORAGE_NBT =  7;

    private static final byte STYLE_COLOR_SHIFT =      0;
    private static final byte STYLE_FONT_SHIFT =       1;
    private static final byte STYLE_INSERTION_SHIFT =  2;
    private static final byte STYLE_CLICK_EVENT_SHIFT;
    private static final byte STYLE_HOVER_EVENT_SHIFT;

    private static final byte STYLE_COLOR_MASK = 1 << STYLE_COLOR_SHIFT;
    private static final byte STYLE_FONT_MASK = 1 << STYLE_FONT_SHIFT;
    private static final byte STYLE_INSERTION_MASK = 1 << STYLE_INSERTION_SHIFT;
    private static final byte STYLE_CLICK_EVENT_MASK;
    private static final byte STYLE_HOVER_EVENT_MASK;

    private static int log2(int in) {
        int r = 0;
        while ((in >>>= 1) != 0) r++;
        return r;
    }

    static {
        STYLE_CLICK_EVENT_SHIFT = STYLE_INSERTION_SHIFT + 1;
        int clickBits = log2(ClickEvent.Action.values().length) + 1;
        STYLE_CLICK_EVENT_MASK = (byte)(((1 << clickBits) - 1) << STYLE_CLICK_EVENT_SHIFT);

        STYLE_HOVER_EVENT_SHIFT = (byte)(STYLE_CLICK_EVENT_SHIFT + clickBits);
        int hoverBits = log2(HoverEvent.Action.NAMES.keys().size()) + 1;
        STYLE_HOVER_EVENT_MASK = (byte)(((1 << hoverBits) - 1) << STYLE_HOVER_EVENT_SHIFT);

        // Ensure everything fits into a byte
        assert STYLE_CLICK_EVENT_SHIFT + clickBits + hoverBits <= 8;
    }

    private static final TextDecoration[] DECORATIONS = {
            TextDecoration.BOLD,
            TextDecoration.ITALIC,
            TextDecoration.UNDERLINED,
            TextDecoration.STRIKETHROUGH,
            TextDecoration.OBFUSCATED
    };

    // region [Serialize]

    @Override
    public void serializeComponent(Component value, DataOutputStream output) throws IOException {
        this.serializeComponent(value, output, true);
    }

    public void serializeComponent(Component value, DataOutputStream output, boolean header) throws IOException {
        if (header) {
            output.writeByte(VERSION);
        }

        if (value instanceof TextComponent text) {
            output.writeByte(COMPONENT_TEXT);
            serializeString(text.content(), output);
        } else if (value instanceof TranslatableComponent translatable) {
            output.writeByte(COMPONENT_TRANSLATABLE);
            serializeString(translatable.key(), output);

            output.writeByte((byte) translatable.args().size());
            for (Component arg : translatable.args()) {
                serializeComponent(arg, output, false);
            }
        } else if (value instanceof ScoreComponent score) {
            output.writeByte(COMPONENT_SCORE);
            serializeString(score.name(), output);
            serializeString(score.objective(), output);
        } else if (value instanceof SelectorComponent selector) {
            output.writeByte(COMPONENT_SELECTOR);
            serializeString(selector.pattern(), output);

            serializeOptional(selector.separator(), output);
        } else if (value instanceof KeybindComponent keybind) {
            output.writeByte(COMPONENT_KEYBIND);
            serializeString(keybind.keybind(), output);
        } else if (value instanceof BlockNBTComponent nbt) {
            output.writeByte(COMPONENT_BLOCK_NBT);

            serializeString(nbt.nbtPath(), output);
            output.writeBoolean(nbt.interpret());
            serializeOptional(nbt.separator(), output);

            serializeBlockNbtPos(nbt.pos(), output);
        } else if (value instanceof EntityNBTComponent nbt) {
            output.writeByte(COMPONENT_ENTITY_NBT);

            serializeString(nbt.nbtPath(), output);
            output.writeBoolean(nbt.interpret());
            serializeOptional(nbt.separator(), output);

            serializeString(nbt.selector(), output);
        } else if (value instanceof StorageNBTComponent nbt) {
            output.writeByte(COMPONENT_STORAGE_NBT);

            serializeString(nbt.nbtPath(), output);
            output.writeBoolean(nbt.interpret());
            serializeOptional(nbt.separator(), output);

            serializeKey(nbt.storage(), output);
        } else {
            throw notSureHowToSerialize(value);
        }

        int data = (value.children().size() << 1) | (value.hasStyling() ? 1 : 0);
        serializeVarInt(data, output);

        if (value.hasStyling()) {
            serializeStyle(value.style(), output);
        }

        for (Component child : value.children()) {
            serializeComponent(child, output, false);
        }
    }

    private void serializeOptional(Component value, DataOutputStream output) throws IOException {
        if (value != null) {
            output.writeBoolean(true);
            serializeComponent(value, output, false);
        } else {
            output.writeBoolean(false);
        }
    }

    private void serializeStyle(Style value, DataOutputStream output) throws IOException {
        int decorationValue = 0;

        for (TextDecoration decoration : DECORATIONS) {
            decorationValue *= 3;

            final TextDecoration.State state = value.decoration(decoration);
            decorationValue += state.ordinal();
        }

        output.writeByte(decorationValue);

        final @Nullable TextColor color = value.color();
        final @Nullable Key font = value.font();
        final @Nullable String insertion = value.insertion();
        final @Nullable HoverEvent<?> hoverEvent = value.hoverEvent();
        final @Nullable ClickEvent clickEvent = value.clickEvent();

        byte state = 0;
        if (color != null) state |= STYLE_COLOR_MASK;
        if (font != null) state |= STYLE_FONT_MASK;
        if (insertion != null) state |= STYLE_INSERTION_MASK;
        if (clickEvent != null) {
            state |= (clickEvent.action().ordinal() + 1) << STYLE_CLICK_EVENT_SHIFT;
        }
        if (hoverEvent != null) {
            Object hoverValue = hoverEvent.value();
            if (hoverValue instanceof HoverEvent.ShowItem)        state |= 0b01 << STYLE_HOVER_EVENT_SHIFT;
            else if (hoverValue instanceof HoverEvent.ShowEntity) state |= 0b10 << STYLE_HOVER_EVENT_SHIFT;
            else if (hoverValue instanceof Component)             state |= 0b11 << STYLE_HOVER_EVENT_SHIFT;
            else throw new IllegalArgumentException("Don't know how to serialize " + hoverEvent);
        }

        output.writeByte(state);

        if (color != null) {
            output.writeByte(color.red());
            output.writeByte(color.green());
            output.writeByte(color.blue());
        }

        if (font != null) {
            serializeKey(font, output);
        }

        if (insertion != null) {
            serializeString(insertion, output);
        }

        if (clickEvent != null) {
            serializeString(clickEvent.value(), output);
        }

        if (hoverEvent != null) {
            Object hoverValue = hoverEvent.value();
            if (hoverValue instanceof HoverEvent.ShowItem showItem) {
                serializeShowItem(showItem, output);
            } else if (hoverValue instanceof HoverEvent.ShowEntity showEntity) {
                serializeShowEntity(showEntity, output);
            } else if (hoverValue instanceof Component component) {
                serializeComponent(component, output, false);
            } else {
                throw new IllegalArgumentException("Don't know how to serialize " + hoverEvent);
            }
        }
    }

    private void serializeShowItem(HoverEvent.ShowItem value, DataOutputStream output) throws IOException {
        serializeKey(value.item(), output);

        output.writeByte((byte) value.count());

        final @Nullable BinaryTagHolder nbt = value.nbt();
        if (nbt != null) {
            serializeString(nbt.string(), output);
        } else {
            serializeString("", output);
        }
    }

    private void serializeShowEntity(HoverEvent.ShowEntity value, DataOutputStream output) throws IOException {
        serializeKey(value.type(), output);

        output.writeLong(value.id().getMostSignificantBits());
        output.writeLong(value.id().getLeastSignificantBits());

        final @Nullable Component name = value.name();
        if (name != null) {
            output.writeBoolean(true);
            serializeComponent(name, output, false);
        } else {
            output.writeBoolean(false);
        }
    }

    private void serializeBlockNbtPos(BlockNBTComponent.Pos pos, DataOutputStream output) throws IOException {
        if (pos instanceof BlockNBTComponent.WorldPos world) {
            output.writeByte(0);
            serializeCoordinate(world.x(), output);
            serializeCoordinate(world.y(), output);
            serializeCoordinate(world.z(), output);
        } else if (pos instanceof BlockNBTComponent.LocalPos local) {
            output.writeByte(1);
            output.writeDouble(local.left());
            output.writeDouble(local.up());
            output.writeDouble(local.forwards());
        }
    }

    private void serializeCoordinate(BlockNBTComponent.WorldPos.Coordinate coordinate, DataOutputStream output) throws IOException {
        serializeSignedInt(coordinate.value(), output);
        output.writeByte((byte) coordinate.type().ordinal());
    }

    private void serializeKey(Key key, DataOutputStream output) throws IOException {
        serializeString(key.namespace(), output);
        serializeString(key.value(), output);
    }

    // endregion [Serialize]

    @Override
    public Component deserializeComponent(DataInputStream input) throws IOException {
        return deserializeComponent(input, true);
    }

    public Component deserializeComponent(DataInputStream input, boolean header) throws IOException {
        if (header) {
            if (VERSION != input.readByte()) {
                throw new IllegalStateException("Wrong version! Can't deserialize");
            }
        }

        byte componentType = input.readByte();
        ComponentBuilder<?, ?> builder = switch (componentType) {
            case COMPONENT_TEXT -> Component.text()
                    .content(deserializeString(input));
            case COMPONENT_TRANSLATABLE -> {
                var translatable = Component.translatable()
                        .key(deserializeString(input));

                byte argsCount = input.readByte();

                List<Component> args = new ArrayList<>(argsCount);
                for (int i = 0; i < argsCount; i++) {
                    args.add(deserializeComponent(input, false));
                }
                translatable.args(args);

                yield translatable;
            }
            case COMPONENT_SCORE -> Component.score()
                    .name(deserializeString(input))
                    .objective(deserializeString(input));
            case COMPONENT_SELECTOR -> {
                var selector = Component.selector()
                        .pattern(deserializeString(input));

                if (input.readBoolean()) {
                    selector.separator(deserializeComponent(input, false));
                }

                yield selector;
            }
            case COMPONENT_KEYBIND -> Component.keybind()
                    .keybind(deserializeString(input));
            case COMPONENT_BLOCK_NBT -> {
                var block = Component.blockNBT()
                        .nbtPath(deserializeString(input))
                        .interpret(input.readBoolean());

                if (input.readBoolean()) {
                    block.separator(deserializeComponent(input, false));
                }

                block.pos(deserializeBlockNbtPos(input));

                yield block;
            }
            case COMPONENT_ENTITY_NBT -> {
                var entity = Component.entityNBT()
                        .nbtPath(deserializeString(input))
                        .interpret(input.readBoolean());

                if (input.readBoolean()) {
                    entity.separator(deserializeComponent(input, false));
                }

                entity.selector(deserializeString(input));

                yield entity;
            }
            case COMPONENT_STORAGE_NBT -> {
                var storage = Component.storageNBT()
                        .nbtPath(deserializeString(input))
                        .interpret(input.readBoolean());

                if (input.readBoolean()) {
                    storage.separator(deserializeComponent(input, false));
                }

                storage.storage(deserializeKey(input));

                yield storage;
            }
            default -> throw notSureHowToDeserialize();
        };

        int data = deserializeVarInt(input);

        if ((data & 1) != 0) {
            builder.style(deserializeStyle(input));
        }

        int childrenCount = data >> 1;
        for (int i = 0; i < childrenCount; i++) {
            builder.append(deserializeComponent(input, false));
        }

        return builder.build();
    }

    private Style deserializeStyle(DataInputStream input) throws IOException {
        final var builder = Style.style();

        int decorationValue = input.readByte() & 0xFF;

        for (int i = DECORATIONS.length-1; i >= 0; i--) {
            TextDecoration decoration = DECORATIONS[i];
            builder.decoration(decoration, TextDecoration.State.values()[decorationValue % 3]);
            decorationValue /= 3;
        }

        int state = input.readByte() & 0xFF;

        if ((state & STYLE_COLOR_MASK) != 0) {
            int color = ((input.readByte() & 0xFF) << 16) | ((input.readByte() & 0xFF) << 8) | (input.readByte() & 0xFF);
            builder.color(TextColor.color(color));
        }

        if ((state & STYLE_FONT_MASK) != 0) {
            builder.font(deserializeKey(input));
        }

        if ((state & STYLE_INSERTION_MASK) != 0) {
            builder.insertion(deserializeString(input));
        }

        if ((state & STYLE_CLICK_EVENT_MASK) != 0) {
            int actionId = ((state & STYLE_CLICK_EVENT_MASK) >>> STYLE_CLICK_EVENT_SHIFT) - 1;
            builder.clickEvent(ClickEvent.clickEvent(
                    ClickEvent.Action.values()[actionId],
                    deserializeString(input)
            ));
        }

        if ((state & STYLE_HOVER_EVENT_MASK) != 0) {
            int hoverActionId = ((state & STYLE_HOVER_EVENT_MASK) >>> STYLE_HOVER_EVENT_SHIFT) - 1;

            var hover = switch (hoverActionId) {
                case 0 -> HoverEvent.showItem(deserializeShowItem(input));
                case 1 -> HoverEvent.showEntity(deserializeShowEntity(input));
                case 2 -> HoverEvent.showText(deserializeComponent(input, false));
                default -> throw notSureHowToDeserialize();
            };

            builder.hoverEvent(hover);
        }

        return builder.build();
    }

    private HoverEvent.ShowItem deserializeShowItem(DataInputStream input) throws IOException {
        Key item = deserializeKey(input);
        byte count = input.readByte();
        String nbtString = deserializeString(input);

        if (nbtString.isEmpty()) {
            return HoverEvent.ShowItem.of(item, count);
        } else {
            return HoverEvent.ShowItem.of(item, count, BinaryTagHolder.binaryTagHolder(nbtString));
        }
    }

    private HoverEvent.ShowEntity deserializeShowEntity(DataInputStream input) throws IOException {
        Key type = deserializeKey(input);
        UUID id = new UUID(input.readLong(), input.readLong());


        if (input.readBoolean()) {
            return HoverEvent.ShowEntity.of(type, id, deserializeComponent(input, false));
        } else {
            return HoverEvent.ShowEntity.of(type, id);
        }
    }

    private BlockNBTComponent.Pos deserializeBlockNbtPos(DataInputStream input) throws IOException {
        return switch (input.readByte()) {
            case 0 -> BlockNBTComponent.WorldPos.worldPos(
                deserializeCoordinate(input),
                deserializeCoordinate(input),
                deserializeCoordinate(input)
            );
            case 1 -> BlockNBTComponent.LocalPos.localPos(
                input.readDouble(),
                input.readDouble(),
                input.readDouble()
            );
            default -> throw notSureHowToDeserialize();
        };
    }

    private BlockNBTComponent.WorldPos.Coordinate deserializeCoordinate(DataInputStream input) throws IOException {
        return BlockNBTComponent.WorldPos.Coordinate.coordinate(
            deserializeSignedInt(input),
            BlockNBTComponent.WorldPos.Coordinate.Type.values()[input.readByte()]
        );
    }

    private Key deserializeKey(DataInputStream input) throws IOException {
        return Key.key(
                deserializeString(input),
                deserializeString(input)
        );
    }

    public void serializeString(String value, DataOutputStream output) throws IOException {
        serializeBytes(value.getBytes(StandardCharsets.UTF_8), output);
    }

    public String deserializeString(DataInputStream input) throws IOException {
        return new String(deserializeBytes(input), StandardCharsets.UTF_8);
    }

    public void serializeBytes(byte[] value, DataOutputStream output) throws IOException {
        serializeVarInt(value.length, output);
        output.write(value);
    }

    public byte[] deserializeBytes(DataInputStream input) throws IOException {
        int length = deserializeVarInt(input);
        return input.readNBytes(length);
    }

    public static final byte SIGN_MASK = (byte) 0b01000000;
    public static final byte MORE_MASK = (byte) 0b10000000;

    static int deserializeSignedInt(DataInputStream input) throws IOException {
        byte b = input.readByte();
        if (b == SIGN_MASK) {
            return input.readInt();
        }

        int sign = ((b & SIGN_MASK) == SIGN_MASK) ? -1 : 1;

        int result = b & 0x3f;
        if (b >= 0) return result * sign;

        for (int shift = 6; ; shift += 7) {
            b = input.readByte();
            if (shift == 20) {
                result |= (b & 0xFF) << shift;
                return result * sign;
            } else {
                result |= (b & 0x7f) << shift;
                if (b >= 0) return result * sign;
            }
        }
    }

    static void serializeSignedInt(int value, DataOutputStream output) throws IOException {
        if (value == Integer.MIN_VALUE) {
            output.writeByte(SIGN_MASK);
            output.writeInt(value);
            return;
        }

        int neg = 0;
        if (value < 0) {
            value = -value;
            neg = SIGN_MASK;
        }

        if (value < 0x3f) {
            output.writeByte(value | neg);
        } else if (value < 0x1fff) {
            output.writeByte((value & 0x3f) | neg | MORE_MASK);
            output.writeByte( (value & 0x1fff) >>> 6);
        } else if (value < 0xfffff) {
            output.writeByte((value & 0x3f) | neg | MORE_MASK);
            output.writeByte(((value & 0x1fff) >>> 6) | MORE_MASK);
            output.writeByte((value & 0xfffff) >>> 13);
        } else if (value < 0xfffffff) {
            output.writeByte((value & 0x3f) | neg | MORE_MASK);
            output.writeByte(((value & 0x1fff) >>> 6) | MORE_MASK);
            output.writeByte(((value & 0xfffff) >>> 13) | MORE_MASK);
            output.writeByte((value & 0xfffffff) >>> 20);
        } else {
            output.writeByte(SIGN_MASK);
            output.writeInt(neg == 0 ? value : -value);
        }
    }

    static int deserializeVarInt(DataInputStream input) throws IOException {
        // https://github.com/jvm-profiling-tools/async-profiler/blob/a38a375dc62b31a8109f3af97366a307abb0fe6f/src/converter/one/jfr/JfrReader.java#L393
        int result = 0;
        for (int shift = 0; ; shift += 7) {
            byte b = input.readByte();
            result |= (b & 0x7f) << shift;
            if (b >= 0) {
                return result;
            }
        }
    }

    static void serializeVarInt(@Range(from=0, to=Integer.MAX_VALUE) int value, DataOutputStream output) throws IOException {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            output.writeByte((byte) value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            output.writeShort((short) ((value & 0x7F | 0x80) << 8 | (value >>> 7)));
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            output.writeByte((byte) (value & 0x7F | 0x80));
            output.writeByte((byte) ((value >>> 7) & 0x7F | 0x80));
            output.writeByte((byte) (value >>> 14));
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            output.writeInt((value & 0x7F | 0x80) << 24 | (((value >>> 7) & 0x7F | 0x80) << 16)
                    | ((value >>> 14) & 0x7F | 0x80) << 8 | (value >>> 21));
        } else {
            output.writeInt((value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16
                    | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80));
            output.writeByte((byte) (value >>> 28));
        }
    }

    private static IllegalArgumentException notSureHowToDeserialize() {
        return new IllegalArgumentException("Don't know how to turn data into a Component");
    }

    private static IllegalArgumentException notSureHowToSerialize(final Component component) {
        return new IllegalArgumentException("Don't know how to serialize " + component + " as a Component");
    }

}

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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/*package-private*/ final class BinaryComponentSerializerImpl implements BinaryComponentSerializer {

    private static final byte VERSION = 0;

    private static final byte COMPONENT_TEXT = 0;
    private static final byte COMPONENT_TRANSLATABLE = 1;
    private static final byte COMPONENT_SCORE = 2;
    private static final byte COMPONENT_SELECTOR = 3;
    private static final byte COMPONENT_KEYBIND = 4;
    private static final byte COMPONENT_BLOCK_NBT = 5;
    private static final byte COMPONENT_ENTITY_NBT = 6;
    private static final byte COMPONENT_STORAGE_NBT = 7;

    private static final TextDecoration[] DECORATIONS = {
            // The order here is important -- Minecraft does string comparisons of some
            // serialized components so we have to make sure our order matches Vanilla
            TextDecoration.BOLD,
            TextDecoration.ITALIC,
            TextDecoration.UNDERLINED,
            TextDecoration.STRIKETHROUGH,
            TextDecoration.OBFUSCATED
    };

    // region [Serialize]

    public void serializeComponent(Component value, DataOutputStream output) throws IOException {
        output.writeByte(VERSION);

        if (value instanceof TextComponent text) {
            output.writeByte(COMPONENT_TEXT);
            output.writeUTF(text.content());
        } else if (value instanceof TranslatableComponent translatable) {
            output.writeByte(COMPONENT_TRANSLATABLE);
            output.writeUTF(translatable.key());

            output.writeByte((byte) translatable.args().size());
            for (Component arg : translatable.args()) {
                serializeComponent(arg, output);
            }
        } else if (value instanceof ScoreComponent score) {
            output.writeByte(COMPONENT_SCORE);
            output.writeUTF(score.name());
            output.writeUTF(score.objective());
        } else if (value instanceof SelectorComponent selector) {
            output.writeByte(COMPONENT_SELECTOR);
            output.writeUTF(selector.pattern());

            serializeOptional(selector.separator(), output);
        } else if (value instanceof KeybindComponent keybind) {
            output.writeByte(COMPONENT_KEYBIND);
            output.writeUTF(keybind.keybind());
        } else if (value instanceof BlockNBTComponent nbt) {
            output.writeByte(COMPONENT_BLOCK_NBT);

            output.writeUTF(nbt.nbtPath());
            output.writeBoolean(nbt.interpret());
            serializeOptional(nbt.separator(), output);

            serializeBlockNbtPos(nbt.pos(), output);
        } else if (value instanceof EntityNBTComponent nbt) {
            output.writeByte(COMPONENT_ENTITY_NBT);

            output.writeUTF(nbt.nbtPath());
            output.writeBoolean(nbt.interpret());
            serializeOptional(nbt.separator(), output);

            output.writeUTF(nbt.selector());
        } else if (value instanceof StorageNBTComponent nbt) {
            output.writeByte(COMPONENT_STORAGE_NBT);

            output.writeUTF(nbt.nbtPath());
            output.writeBoolean(nbt.interpret());
            serializeOptional(nbt.separator(), output);

            serializeKey(nbt.storage(), output);
        } else {
            throw notSureHowToSerialize(value);
        }

        if (value.hasStyling()) {
            output.writeBoolean(true);
            serializeStyle(value.style(), output);
        } else {
            output.writeBoolean(false);
        }

        int children = value.children().size();
        output.writeInt(children);
        for (Component child : value.children()) {
            serializeComponent(child, output);
        }
    }

    private void serializeOptional(Component value, DataOutputStream output) throws IOException {
        if (value != null) {
            output.writeBoolean(true);
            serializeComponent(value, output);
        } else {
            output.writeBoolean(false);
        }
    }

    private void serializeStyle(Style value, DataOutputStream output) throws IOException {
        for (TextDecoration decoration : DECORATIONS) {
            final TextDecoration.State state = value.decoration(decoration);
            output.writeByte(state.ordinal());
        }

        final @Nullable TextColor color = value.color();
        if (color != null) {
            output.writeInt(color.value() | 0xFF000000);
        } else {
            output.writeInt(0);
        }

        final @Nullable String insertion = value.insertion();
        output.writeUTF(Objects.requireNonNullElse(insertion, ""));

        final @Nullable Key font = value.font();
        if (font != null) {
            output.writeBoolean(true);
            serializeKey(font, output);
        } else {
            output.writeBoolean(false);
        }

        final @Nullable ClickEvent clickEvent = value.clickEvent();
        if (clickEvent != null) {
            output.writeByte((byte) clickEvent.action().ordinal());
            output.writeUTF(clickEvent.value());
        } else {
            output.writeByte(-1);
        }

        final @Nullable HoverEvent<?> hoverEvent = value.hoverEvent();
        if (hoverEvent != null) {
            Object hoverValue = hoverEvent.value();

            if (hoverValue instanceof HoverEvent.ShowItem showItem) {
                output.writeByte(0);
                serializeShowItem(showItem, output);
            } else if (hoverValue instanceof HoverEvent.ShowEntity showEntity) {
                output.writeByte(1);
                serializeShowEntity(showEntity, output);
            } else if (hoverValue instanceof Component component) {
                output.writeByte(2);
                serializeComponent(component, output);
            } else {
                throw new IllegalArgumentException("Don't know how to serialize " + hoverEvent.value());
            }
        } else {
            output.writeByte(-1);
        }
    }

    private void serializeShowItem(HoverEvent.ShowItem value, DataOutputStream output) throws IOException {
        serializeKey(value.item(), output);

        output.writeByte((byte) value.count());

        final @Nullable BinaryTagHolder nbt = value.nbt();
        if (nbt != null) {
            output.writeUTF(nbt.string());
        } else {
            output.writeUTF("");
        }
    }

    private void serializeShowEntity(HoverEvent.ShowEntity value, DataOutputStream output) throws IOException {
        serializeKey(value.type(), output);

        output.writeLong(value.id().getMostSignificantBits());
        output.writeLong(value.id().getLeastSignificantBits());

        final @Nullable Component name = value.name();
        if (name != null) {
            output.writeBoolean(true);
            serializeComponent(name, output);
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
        output.writeInt(coordinate.value());
        output.writeByte((byte) coordinate.type().ordinal());
    }

    private void serializeKey(Key key, DataOutputStream output) throws IOException {
        output.writeUTF(key.namespace());
        output.writeUTF(key.value());
    }

    // endregion [Serialize]

    public Component deserializeComponent(DataInputStream input) throws IOException {
        if (VERSION != input.readByte()) {
            throw new IllegalStateException("Wrong version! Can't deserialize");
        }

        byte componentType = input.readByte();
        ComponentBuilder<?, ?> builder = switch (componentType) {
            case COMPONENT_TEXT -> Component.text()
                    .content(input.readUTF());
            case COMPONENT_TRANSLATABLE -> {
                var translatable = Component.translatable()
                        .key(input.readUTF());

                byte argsCount = input.readByte();

                List<Component> args = new ArrayList<>(argsCount);
                for (int i = 0; i < argsCount; i++) {
                    args.add(deserializeComponent(input));
                }
                translatable.args(args);

                yield translatable;
            }
            case COMPONENT_SCORE -> Component.score()
                    .name(input.readUTF())
                    .objective(input.readUTF());
            case COMPONENT_SELECTOR -> {
                var selector = Component.selector()
                        .pattern(input.readUTF());

                if (input.readBoolean()) {
                    selector.separator(deserializeComponent(input));
                }

                yield selector;
            }
            case COMPONENT_KEYBIND -> Component.keybind()
                    .keybind(input.readUTF());
            case COMPONENT_BLOCK_NBT -> {
                var block = Component.blockNBT()
                        .nbtPath(input.readUTF())
                        .interpret(input.readBoolean());

                if (input.readBoolean()) {
                    block.separator(deserializeComponent(input));
                }

                block.pos(deserializeBlockNbtPos(input));

                yield block;
            }
            case COMPONENT_ENTITY_NBT -> {
                var entity = Component.entityNBT()
                        .nbtPath(input.readUTF())
                        .interpret(input.readBoolean());

                if (input.readBoolean()) {
                    entity.separator(deserializeComponent(input));
                }

                entity.selector(input.readUTF());

                yield entity;
            }
            case COMPONENT_STORAGE_NBT -> {
                var storage = Component.storageNBT()
                        .nbtPath(input.readUTF())
                        .interpret(input.readBoolean());

                if (input.readBoolean()) {
                    storage.separator(deserializeComponent(input));
                }

                storage.storage(deserializeKey(input));

                yield storage;
            }
            default -> throw notSureHowToDeserialize();
        };

        if (input.readBoolean()) {
            builder.style(deserializeStyle(input));
        }

        int childrenCount = input.readInt();
        for (int i = 0; i < childrenCount; i++) {
            builder.append(deserializeComponent(input));
        }

        return builder.build();
    }

    private Style deserializeStyle(DataInputStream input) throws IOException {
        final var builder = Style.style();

        for (TextDecoration decoration : DECORATIONS) {
            builder.decoration(decoration, TextDecoration.State.values()[input.readByte()]);
        }

        final int color = input.readInt();
        if (color != 0) {
            builder.color(TextColor.color(color));
        }

        final String insertion = input.readUTF();
        if (!insertion.isEmpty()) {
            builder.insertion(insertion);
        }

        if (input.readBoolean()) {
            builder.font(deserializeKey(input));
        }

        byte clickAction = input.readByte();
        if (clickAction >= 0) {
            builder.clickEvent(ClickEvent.clickEvent(
                ClickEvent.Action.values()[clickAction],
                input.readUTF()
            ));
        }

        byte hoverAction = input.readByte();
        if (hoverAction >= 0) {
            var hover = switch (hoverAction) {
                case 0 -> HoverEvent.showItem(deserializeShowItem(input));
                case 1 -> HoverEvent.showEntity(deserializeShowEntity(input));
                case 2 -> HoverEvent.showText(deserializeComponent(input));
                default -> throw notSureHowToDeserialize();
            };

            builder.hoverEvent(hover);
        }

        return builder.build();
    }

    private HoverEvent.ShowItem deserializeShowItem(DataInputStream input) throws IOException {
        Key item = deserializeKey(input);
        byte count = input.readByte();
        String nbtString = input.readUTF();

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
            return HoverEvent.ShowEntity.of(type, id, deserializeComponent(input));
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
            input.readInt(),
            BlockNBTComponent.WorldPos.Coordinate.Type.values()[input.readByte()]
        );
    }

    private Key deserializeKey(DataInputStream input) throws IOException {
        return Key.key(
                input.readUTF(),
                input.readUTF()
        );
    }

    private static IllegalArgumentException notSureHowToDeserialize() {
        return new IllegalArgumentException("Don't know how to turn data into a Component");
    }

    private static IllegalArgumentException notSureHowToSerialize(final Component component) {
        return new IllegalArgumentException("Don't know how to serialize " + component + " as a Component");
    }

}

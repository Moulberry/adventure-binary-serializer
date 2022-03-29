/*
 * This file is part of adventure, licensed under the MIT License.
 *
 * Copyright (c) 2017-2022 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.gauntletmc.adventure.serializer.binary;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class StyleTest extends ComponentTest {
    private static final Key FANCY_FONT = Key.key("kyori", "kittens");

    private void test(Style style) {
        this.test(Component.text("hjhagsd").style(style));
    }

    @Test
    void testEmpty() {
        final Style s0 = Style.style().build();
        this.test(s0);
    }

    @Test
    void testHexColor() {
        final Style s0 = Style.style().color(TextColor.color(0x0a1ab9)).build();
        this.test(s0);
    }

    @Test
    void testNamedColor() {
        final Style s0 = Style.style().color(NamedTextColor.LIGHT_PURPLE).build();
        this.test(s0);
    }

    @Test
    void testDecoration() {
        for (TextDecoration decoration : TextDecoration.values()) {
            for (TextDecoration.State state : TextDecoration.State.values()) {
                this.test(Style.style().decoration(decoration, state).build());
            }
        }
    }

    @Test
    void testInsertion() {
        this.test(Style.style().insertion("honk").build());
    }

    @Test
    void testMixedFontColorDecorationClickEvent() {
        this.test(
                Style.style()
                        .font(FANCY_FONT)
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true)
                        .clickEvent(ClickEvent.openUrl("https://github.com"))
                        .build()
        );
    }

    @Test
    void testShowEntityHoverEvent() {
        final UUID dolores = UUID.randomUUID();
        this.test(
                Style.style()
                        .hoverEvent(HoverEvent.showEntity(HoverEvent.ShowEntity.of(
                                Key.key(Key.MINECRAFT_NAMESPACE, "pig"),
                                dolores,
                                Component.text("Dolores", TextColor.color(0x0a1ab9))
                        )))
                        .build()
        );
    }

    @Test
    void testShowItemHoverEvent() {
        this.test(showItemStyle(1));
        this.test(showItemStyle(2));
    }

    private static Style showItemStyle(final int count) {
        return Style.style()
                .hoverEvent(HoverEvent.showItem(HoverEvent.ShowItem.of(
                        Key.key(Key.MINECRAFT_NAMESPACE, "stone"),
                        count,
                        null // TODO: test for NBT?
                )))
                .build();
    }

}


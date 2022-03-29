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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class TextComponentTest extends ComponentTest {
    private static final String KEY = "multiplayer.player.left";

    @Test
    void testSimple() {
        this.test(Component.text("Hello, world."));
    }

    @Test
    void testComplex1() {
        this.test(
                Component.text().content("c")
                        .color(NamedTextColor.GOLD)
                        .append(Component.text("o", NamedTextColor.DARK_AQUA))
                        .append(Component.text("l", NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text("o", NamedTextColor.DARK_PURPLE))
                        .append(Component.text("u", NamedTextColor.BLUE))
                        .append(Component.text("r", NamedTextColor.DARK_GREEN))
                        .append(Component.text("s", NamedTextColor.RED))
                        .build()
        );
    }

    @Test
    void testComplex2() {
        this.test(
                Component.text().content("This is a test.")
                        .color(NamedTextColor.DARK_PURPLE)
                        .hoverEvent(HoverEvent.showText(Component.text("A test.")))
                        .append(Component.text(" "))
                        .append(Component.text("A what?", NamedTextColor.DARK_AQUA))
                        .build()
        );
    }
}


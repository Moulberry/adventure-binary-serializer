package net.gauntletmc.adventure.serializer.binary;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;

public class ComponentTest {

    public void test(Component component) {
        try {
            byte[] serialized = BinaryComponentSerializer.INSTANCE.serializeComponent(component);
            Component result = BinaryComponentSerializer.INSTANCE.deserializeComponent(serialized);

            Assertions.assertEquals(component, result);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

}

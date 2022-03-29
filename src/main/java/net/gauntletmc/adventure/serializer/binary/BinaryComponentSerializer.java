package net.gauntletmc.adventure.serializer.binary;

import net.kyori.adventure.text.Component;

import java.io.*;

public sealed interface BinaryComponentSerializer permits BinaryComponentSerializerImpl {

    BinaryComponentSerializer INSTANCE = new BinaryComponentSerializerImpl();

    default byte[] serializeComponent(Component value) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializeComponent(value, new DataOutputStream(baos));
        return baos.toByteArray();
    }

    default Component deserializeComponent(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        return deserializeComponent(new DataInputStream(bais));
    }

    void serializeComponent(Component value, DataOutputStream output) throws IOException;

    Component deserializeComponent(DataInputStream input) throws IOException;

}

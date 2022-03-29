package net.gauntletmc.adventure.serializer.binary;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;

public class IntSerializeTest {

    @Test
    public void signedTest() throws IOException {
        signedTestForInt(Integer.MIN_VALUE);
        signedTestForInt(Integer.MAX_VALUE);

        for (int i = Integer.MIN_VALUE; i < 0; i += 1000) {
            signedTestForInt(i);
        }

        for (int i = Integer.MAX_VALUE; i > 0; i -= 1000) {
            signedTestForInt(i);
        }

        for (int i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++) {
            signedTestForInt(i);
        }
    }

    public void signedTestForInt(int i) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        BinaryComponentSerializerImpl.serializeSignedInt(i, out);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream in = new DataInputStream(bais);

        Assertions.assertEquals(i, BinaryComponentSerializerImpl.deserializeSignedInt(in));
    }

    @Test
    public void varIntTest() throws IOException {
        varIntTestForInt(Integer.MIN_VALUE);
        varIntTestForInt(Integer.MAX_VALUE);

        for (int i = Integer.MIN_VALUE; i < 0; i += 1000) {
            varIntTestForInt(i);
        }

        for (int i = Integer.MAX_VALUE; i > 0; i -= 1000) {
            varIntTestForInt(i);
        }

        for (int i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++) {
            varIntTestForInt(i);
        }
    }

    public void varIntTestForInt(int i) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        BinaryComponentSerializerImpl.serializeVarInt(i, out);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream in = new DataInputStream(bais);

        Assertions.assertEquals(i, BinaryComponentSerializerImpl.deserializeVarInt(in));
    }

}

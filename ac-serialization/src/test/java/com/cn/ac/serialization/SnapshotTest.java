package com.cn.ac.serialization;

import com.cn.ac.AcAutomaton;
import com.cn.ac.AcMatch;
import com.cn.ac.exception.CorruptAutomatonException;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SnapshotTest {

    private static final PayloadCodec<String> STRING_CODEC = new PayloadCodec<String>() {
        @Override
        public String codecId() {
            return "STRING_UTF";
        }

        @Override
        public void encode(String payload, DataOutput out) throws IOException {
            out.writeUTF(payload);
        }

        @Override
        public String decode(DataInput in) throws IOException {
            return in.readUTF();
        }
    };

    @Test
    public void testSnapshotRoundTrip() throws Exception {
        AcAutomaton<String> original = AcAutomaton.<String>builder()
                .addKeyword(10, "apple", "FRUIT_A")
                .addKeyword(20, "banana", "FRUIT_B")
                .addKeyword(30, "app", "APP")
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AcAutomatonSnapshot.save(original, baos, STRING_CODEC);
        byte[] bytes = baos.toByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        AcAutomaton<String> loaded = AcAutomatonSnapshot.load(bais, STRING_CODEC);

        String text = "I eat an apple and an app today";
        List<AcMatch<String>> origMatches = original.findAll(text);
        List<AcMatch<String>> loadedMatches = loaded.findAll(text);

        assertEquals(origMatches.size(), loadedMatches.size());
        for (int i = 0; i < origMatches.size(); i++) {
            assertEquals(origMatches.get(i).startUtf16(), loadedMatches.get(i).startUtf16());
            assertEquals(origMatches.get(i).endUtf16(), loadedMatches.get(i).endUtf16());
            assertEquals(origMatches.get(i).keywordId(), loadedMatches.get(i).keywordId());
            assertEquals(origMatches.get(i).payload(), loadedMatches.get(i).payload());
        }
    }

    @Test
    public void testCorruptedSnapshotChecksumFailure() throws Exception {
        AcAutomaton<String> original = AcAutomaton.<String>builder()
                .addKeyword(1, "test", "PAYLOAD")
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AcAutomatonSnapshot.save(original, baos, STRING_CODEC);
        byte[] bytes = baos.toByteArray();

        // Corrupt one byte in the body
        bytes[20] ^= 0xFF;

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        assertThrows(CorruptAutomatonException.class, () -> {
            AcAutomatonSnapshot.load(bais, STRING_CODEC);
        });
    }
}

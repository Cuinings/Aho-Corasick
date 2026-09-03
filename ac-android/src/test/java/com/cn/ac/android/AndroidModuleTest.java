package com.cn.ac.android;

import com.cn.ac.AcAutomaton;
import com.cn.ac.serialization.AcAutomatonSnapshot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class AndroidModuleTest {

    @Test
    public void testRepositoryAndLoader() throws Exception {
        AcAutomaton<String> a1 = AcAutomaton.<String>builder()
                .addKeyword(1, "test1", "P1")
                .build();
        AcAutomaton<String> a2 = AcAutomaton.<String>builder()
                .addKeyword(2, "test2", "P2")
                .build();

        AcAutomatonRepository<String> repo = new AcAutomatonRepository<>(a1);
        assertEquals("P1", repo.current().findAny("test1").payload());

        repo.replace(a2);
        assertEquals("P2", repo.current().findAny("test2").payload());

        // Test loader with stream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AcAutomatonSnapshot.save(a1, baos, null);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        AcAutomaton<Void> loaded = AcAutomatonLoader.loadFromStream(bais, null);
        assertTrue(loaded.contains("test1"));
    }
}

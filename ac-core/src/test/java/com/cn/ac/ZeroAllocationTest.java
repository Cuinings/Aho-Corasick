package com.cn.ac;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.*;

public class ZeroAllocationTest {

    @Test
    public void testContainsZeroAllocation() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("apple", null)
                .addKeyword("banana", null)
                .addKeyword("orange", null)
                .build();

        String textWithMatch = "I like to eat an apple every single day!";
        String textNoMatch = "I like to eat a peach every single day!";

        java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
        if (!(baseBean instanceof ThreadMXBean)) {
            System.out.println("ThreadMXBean not supported, skipping strict allocation check.");
            return;
        }
        ThreadMXBean bean = (ThreadMXBean) baseBean;
        if (!bean.isThreadAllocatedMemorySupported() || !bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }

        long threadId = Thread.currentThread().getId();

        // 1. Warmup for JIT
        for (int i = 0; i < 50_000; i++) {
            automaton.contains(textWithMatch);
            automaton.contains(textNoMatch);
        }

        // 2. Measure contains with match
        long before = bean.getThreadAllocatedBytes(threadId);
        boolean matched = automaton.contains(textWithMatch);
        long after = bean.getThreadAllocatedBytes(threadId);
        assertTrue(matched);
        assertEquals(0, after - before, "contains(match) allocated memory: " + (after - before) + " bytes");

        // 3. Measure contains without match
        before = bean.getThreadAllocatedBytes(threadId);
        matched = automaton.contains(textNoMatch);
        after = bean.getThreadAllocatedBytes(threadId);
        assertFalse(matched);
        assertEquals(0, after - before, "contains(no match) allocated memory: " + (after - before) + " bytes");
    }

    @Test
    public void testScanCallbackZeroAllocation() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("apple", null)
                .addKeyword("banana", null)
                .build();

        String text = "I like apple and banana!";

        java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
        if (!(baseBean instanceof ThreadMXBean)) {
            return;
        }
        ThreadMXBean bean = (ThreadMXBean) baseBean;
        if (!bean.isThreadAllocatedMemorySupported() || !bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }

        long threadId = Thread.currentThread().getId();
        AcScanOptions options = AcScanOptions.ALL;
        AcMatchConsumer<Void> consumer = (start, end, id, payload) -> MatchDecision.CONTINUE;

        // Warmup
        for (int i = 0; i < 50_000; i++) {
            automaton.scan(text, options, consumer);
        }

        // Measure
        long before = bean.getThreadAllocatedBytes(threadId);
        int count = automaton.scan(text, options, consumer);
        long after = bean.getThreadAllocatedBytes(threadId);

        assertEquals(2, count);
        assertEquals(0, after - before, "scan callback allocated memory: " + (after - before) + " bytes");
    }
}

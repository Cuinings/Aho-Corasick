package com.cn.ac.benchmark;

import com.cn.ac.AcAutomaton;
import com.cn.ac.AcMatchConsumer;
import com.cn.ac.AcScanOptions;
import com.cn.ac.MatchDecision;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JvmBenchmarkTest {

    @Test
    public void compareAgainstOldLibrary() {
        System.out.println("=== Benchmark Comparison: New AcAutomaton vs org.ahocorasick:0.6.3 ===");

        // Prepare 10,000 keywords
        int keywordCount = 10_000;
        List<String> keywords = new ArrayList<>(keywordCount);
        for (int i = 0; i < keywordCount; i++) {
            keywords.add("keyword_" + i);
        }

        // Prepare text of 100,000 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2_000; i++) {
            sb.append("This is some dummy text before keyword_").append(i % 100).append(" and text after ");
        }
        String text = sb.toString();

        // 1. Measure Build Time & Memory for Old Library (0.6.3)
        System.gc();
        long memBeforeOld = getUsedMemory();
        long startBuildOld = System.nanoTime();

        Trie.TrieBuilder oldBuilder = Trie.builder();
        for (String kw : keywords) {
            oldBuilder.addKeyword(kw);
        }
        Trie oldTrie = oldBuilder.build();

        long buildOldMs = (System.nanoTime() - startBuildOld) / 1_000_000;
        System.gc();
        long memAfterOld = getUsedMemory();
        long heapOld = Math.max(0, memAfterOld - memBeforeOld);

        // 2. Measure Build Time & Memory for New AcAutomaton
        System.gc();
        long memBeforeNew = getUsedMemory();
        long startBuildNew = System.nanoTime();

        com.cn.ac.AcBuilder<Void> newBuilder = AcAutomaton.builder();
        for (int i = 0; i < keywords.size(); i++) {
            newBuilder.addKeyword(i, keywords.get(i), null);
        }
        AcAutomaton<Void> newAutomaton = newBuilder.build();

        long buildNewMs = (System.nanoTime() - startBuildNew) / 1_000_000;
        System.gc();
        long memAfterNew = getUsedMemory();
        long heapNew = Math.max(0, memAfterNew - memBeforeNew);

        System.out.println(String.format("Build 10,000 keywords: Old Library = %d ms, New Engine = %d ms",
                buildOldMs, buildNewMs));
        System.out.println(String.format("New Automaton primitive data estimate: %d KB",
                newAutomaton.stats().estimatedPrimitiveBytes() / 1024));

        AcScanOptions options = AcScanOptions.ALL;
        AcMatchConsumer<Void> consumer = (start, end, id, payload) -> MatchDecision.CONTINUE;

        // 3. Warmup scan
        for (int i = 0; i < 100; i++) {
            oldTrie.parseText(text);
            newAutomaton.scan(text, options, consumer);
        }

        // 4. Benchmark Old Library Scan
        long startScanOld = System.nanoTime();
        int oldMatches = 0;
        for (int i = 0; i < 100; i++) {
            Collection<Emit> emits = oldTrie.parseText(text);
            oldMatches += emits.size();
        }
        long scanOldMs = (System.nanoTime() - startScanOld) / 1_000_000;

        // 5. Benchmark New AcAutomaton Callback Scan (0 allocation)
        long startScanNew = System.nanoTime();
        int newMatches = 0;
        for (int i = 0; i < 100; i++) {
            newMatches += newAutomaton.scan(text, options, consumer);
        }
        long scanNewMs = (System.nanoTime() - startScanNew) / 1_000_000;

        System.out.println(String.format("Scan 100 iterations of 100KB text:"));
        System.out.println(String.format("  Old Library (parseText): %d ms (matches=%d)", scanOldMs, oldMatches));
        System.out.println(String.format("  New Engine (scan callback): %d ms (matches=%d)", scanNewMs, newMatches));

        double speedup = (double) scanOldMs / (double) Math.max(1, scanNewMs);
        System.out.println(String.format("Scan Speedup: %.2fx faster", speedup));

        assertTrue(newMatches > 0);
    }

    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}

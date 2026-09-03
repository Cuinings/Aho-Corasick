package com.cn.ac.testkit;

import com.cn.ac.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DifferentialTest {

    @Test
    public void testRandomDifferentialBatches() {
        String[][] alphabets = new String[][] {
                RandomCorpusGenerator.ALPHABET_BINARY,
                RandomCorpusGenerator.ALPHABET_DNA,
                RandomCorpusGenerator.ALPHABET_LOWER,
                RandomCorpusGenerator.ALPHABET_CJK,
                RandomCorpusGenerator.ALPHABET_EMOJI
        };

        TextTransformConfig[] configs = new TextTransformConfig[] {
                TextTransformConfig.exact(),
                TextTransformConfig.builder().caseFold(CaseFoldMode.SIMPLE).build()
        };

        OverlapPolicy[] policies = new OverlapPolicy[] {
                OverlapPolicy.ALL,
                OverlapPolicy.LEFTMOST_LONGEST
        };

        long masterSeed = 20260903L;
        Random masterRnd = new Random(masterSeed);

        // Run 500 comprehensive randomized seed trials
        for (int trial = 0; trial < 500; trial++) {
            long seed = masterRnd.nextLong();
            Random rnd = new Random(seed);

            String[] alphabet = alphabets[rnd.nextInt(alphabets.length)];
            TextTransformConfig config = configs[rnd.nextInt(configs.length)];
            OverlapPolicy policy = policies[rnd.nextInt(policies.length)];

            int kwCount = 1 + rnd.nextInt(30);
            int minLen = 1;
            int maxLen = 1 + rnd.nextInt(8);
            List<String> rawKwStrings = RandomCorpusGenerator.generateKeywords(rnd, alphabet, kwCount, minLen, maxLen);

            AcBuilder<Integer> builder = AcAutomaton.<Integer>builder()
                    .textTransform(config)
                    .duplicatePolicy(DuplicateKeywordPolicy.KEEP_ALL)
                    .deterministicBuild(true);

            List<AcKeyword<Integer>> rawKeywords = new ArrayList<>();
            for (int i = 0; i < rawKwStrings.size(); i++) {
                String kwStr = rawKwStrings.get(i);
                builder.addKeyword(i, kwStr, i);
                rawKeywords.add(new AcKeyword<>(i, kwStr, i));
            }

            AcAutomaton<Integer> automaton = builder.build();
            NaiveReferenceMatcher<Integer> reference = new NaiveReferenceMatcher<>(rawKeywords, config);

            int textLen = rnd.nextInt(200);
            String text = RandomCorpusGenerator.generateText(rnd, alphabet, rawKwStrings, textLen);

            AcScanOptions options = AcScanOptions.builder()
                    .overlapPolicy(policy)
                    .build();

            // 1. Compare findAll
            List<AcMatch<Integer>> actualMatches = automaton.findAll(text, options);
            List<AcMatch<Integer>> expectedMatches = reference.findAll(text, options);

            final int currentTrial = trial;
            final long currentSeed = seed;
            final String currentText = text;
            final List<String> currentKws = rawKwStrings;

            assertEquals(expectedMatches.size(), actualMatches.size(),
                    () -> String.format("Trial %d (seed=%d): match count mismatch! text='%s', kw=%s",
                            currentTrial, currentSeed, currentText, currentKws));

            for (int i = 0; i < expectedMatches.size(); i++) {
                final int matchIdx = i;
                AcMatch<Integer> exp = expectedMatches.get(i);
                AcMatch<Integer> act = actualMatches.get(i);
                assertEquals(exp.startUtf16(), act.startUtf16(),
                        () -> "Start mismatch at index " + matchIdx + ", seed=" + currentSeed);
                assertEquals(exp.endUtf16(), act.endUtf16(),
                        () -> "End mismatch at index " + matchIdx + ", seed=" + currentSeed);
                assertEquals(exp.keywordId(), act.keywordId(),
                        () -> "KeywordId mismatch at index " + matchIdx + ", seed=" + currentSeed);
            }

            // 2. Compare contains
            boolean actContains = automaton.contains(text, options);
            boolean expContains = reference.contains(text, options);
            assertEquals(expContains, actContains, () -> "Contains mismatch on seed=" + currentSeed);

            // 3. Compare findFirst
            AcMatch<Integer> actFirst = automaton.findFirst(text, options);
            AcMatch<Integer> expFirst = reference.findFirst(text, options);
            if (expFirst == null) {
                assertNull(actFirst, "Expected null first match on seed=" + seed);
            } else {
                assertNotNull(actFirst, "Expected non-null first match on seed=" + seed);
                assertEquals(expFirst.startUtf16(), actFirst.startUtf16());
                assertEquals(expFirst.endUtf16(), actFirst.endUtf16());
                assertEquals(expFirst.keywordId(), actFirst.keywordId());
            }
        }
    }
}

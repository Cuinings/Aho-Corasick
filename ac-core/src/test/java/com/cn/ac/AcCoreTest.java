package com.cn.ac;

import com.cn.ac.exception.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AcCoreTest {

    // ==========================================
    // 24.2 核心确定性用例 (AC-001 ~ AC-010)
    // ==========================================

    @Test
    public void testAC001_Ushers() {
        // he, she, his, hers on ushers
        AcAutomaton<String> automaton = AcAutomaton.<String>builder()
                .addKeyword(1, "he", "HE")
                .addKeyword(2, "she", "SHE")
                .addKeyword(3, "his", "HIS")
                .addKeyword(4, "hers", "HERS")
                .build();

        List<AcMatch<String>> matches = automaton.findAll("ushers");
        // "ushers" contains "she" at [1, 4), "he" at [2, 4), "hers" at [2, 6)
        // With start ASC, length DESC, keywordId ASC:
        // matches[0] = "she" [1, 4) (len 3)
        // matches[1] = "hers" [2, 6) (len 4)
        // matches[2] = "he" [2, 4) (len 2)
        assertEquals(3, matches.size());
        assertEquals("she", "ushers".substring(matches.get(0).startUtf16(), matches.get(0).endUtf16()));
        assertEquals(1, matches.get(0).startUtf16());
        assertEquals(4, matches.get(0).endUtf16());

        assertEquals("hers", "ushers".substring(matches.get(1).startUtf16(), matches.get(1).endUtf16()));
        assertEquals(2, matches.get(1).startUtf16());
        assertEquals(6, matches.get(1).endUtf16());

        assertEquals("he", "ushers".substring(matches.get(2).startUtf16(), matches.get(2).endUtf16()));
        assertEquals(2, matches.get(2).startUtf16());
        assertEquals(4, matches.get(2).endUtf16());
    }

    @Test
    public void testAC002_NoMatch() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("apple", null)
                .build();
        assertFalse(automaton.contains("banana"));
        assertTrue(automaton.findAll("banana").isEmpty());
        assertNull(automaton.findAny("banana"));
        assertNull(automaton.findFirst("banana"));
    }

    @Test
    public void testAC003_EmptyInput() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("apple", null)
                .build();
        assertFalse(automaton.contains(""));
        assertTrue(automaton.findAll("").isEmpty());
        assertNull(automaton.findAny(""));
        assertNull(automaton.findFirst(""));
        assertEquals(0, automaton.scan("", AcScanOptions.ALL, (s, e, id, p) -> MatchDecision.CONTINUE));
        assertTrue(automaton.tokenize("", AcScanOptions.LEFTMOST_LONGEST).isEmpty());
    }

    @Test
    public void testAC004_SingleKeywordFullText() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("hello", null)
                .build();
        List<AcMatch<Void>> matches = automaton.findAll("hello");
        assertEquals(1, matches.size());
        assertEquals(0, matches.get(0).startUtf16());
        assertEquals(5, matches.get(0).endUtf16());
    }

    @Test
    public void testAC005_Positions() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("start", null)
                .addKeyword("mid", null)
                .addKeyword("end", null)
                .build();
        String text = "start_mid_end";
        List<AcMatch<Void>> matches = automaton.findAll(text);
        assertEquals(3, matches.size());
        assertEquals("start", text.substring(matches.get(0).startUtf16(), matches.get(0).endUtf16()));
        assertEquals("mid", text.substring(matches.get(1).startUtf16(), matches.get(1).endUtf16()));
        assertEquals("end", text.substring(matches.get(2).startUtf16(), matches.get(2).endUtf16()));
    }

    @Test
    public void testAC006_SameEndOrder() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword(1, "ab", null)
                .addKeyword(2, "b", null)
                .build();
        // Scanning "ab": at end 2, "ab" (len 2) and "b" (len 1) match.
        // Detection order: end ASC, then length DESC, then id ASC
        List<AcMatch<Void>> matches = automaton.findAll("ab");
        assertEquals(2, matches.size());
        assertEquals(1, matches.get(0).keywordId()); // "ab"
        assertEquals(2, matches.get(1).keywordId()); // "b"
    }

    @Test
    public void testAC007_MultiLevelFailureFallback() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("aab", null)
                .addKeyword("ab", null)
                .addKeyword("b", null)
                .build();
        List<AcMatch<Void>> matches = automaton.findAll("aaab");
        assertEquals(3, matches.size());
        assertTrue(automaton.contains("aaab"));
    }

    @Test
    public void testAC008_RootHighOutDegree() {
        AcBuilder<Void> builder = AcAutomaton.builder();
        for (char c = 'a'; c <= 'z'; c++) {
            builder.addKeyword(String.valueOf(c) + "1", null);
        }
        AcAutomaton<Void> automaton = builder.build();
        assertTrue(automaton.contains("z1"));
        assertTrue(automaton.contains("a1"));
        assertTrue(automaton.contains("m1"));
        assertFalse(automaton.contains("z2"));
    }

    @Test
    public void testAC009_LongCommonPrefix() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) sb.append('x');
        String prefix = sb.toString();

        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword(prefix + "A", null)
                .addKeyword(prefix + "B", null)
                .build();

        assertTrue(automaton.contains(prefix + "A"));
        assertTrue(automaton.contains(prefix + "B"));
        assertFalse(automaton.contains(prefix + "C"));
    }

    @Test
    public void testAC010_OutputLinkSuffix() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("broadcast", null)
                .addKeyword("cast", null)
                .addKeyword("ast", null)
                .build();
        List<AcMatch<Void>> matches = automaton.findAll("broadcast");
        assertEquals(3, matches.size());
    }

    // ==========================================
    // 24.3 中文与重叠 (CN-001 ~ CN-005)
    // ==========================================

    @Test
    public void testCN001_ChineseOverlapAll() {
        AcAutomaton<String> automaton = AcAutomaton.<String>builder()
                .addKeyword(1, "微信", "WX")
                .addKeyword(2, "微信支付", "WXPAY")
                .addKeyword(3, "支付", "PAY")
                .build();

        String text = "微信支付支付";
        List<AcMatch<String>> matches = automaton.findAll(text, AcScanOptions.ALL);
        // Sorted: start ASC, length DESC, id ASC:
        // [0, 4) 微信支付
        // [0, 2) 微信
        // [2, 4) 支付
        // [4, 6) 支付
        assertEquals(4, matches.size());
        assertEquals("微信支付", text.substring(matches.get(0).startUtf16(), matches.get(0).endUtf16()));
        assertEquals("微信", text.substring(matches.get(1).startUtf16(), matches.get(1).endUtf16()));
        assertEquals("支付", text.substring(matches.get(2).startUtf16(), matches.get(2).endUtf16()));
        assertEquals("支付", text.substring(matches.get(3).startUtf16(), matches.get(3).endUtf16()));
    }

    @Test
    public void testCN002_ChineseOverlapLeftmostLongest() {
        AcAutomaton<String> automaton = AcAutomaton.<String>builder()
                .addKeyword(1, "微信", "WX")
                .addKeyword(2, "微信支付", "WXPAY")
                .addKeyword(3, "支付", "PAY")
                .build();

        String text = "微信支付支付";
        List<AcMatch<String>> matches = automaton.findAll(text, AcScanOptions.LEFTMOST_LONGEST);
        // LEFTMOST_LONGEST selects: [0, 4) 微信支付, and [4, 6) 支付
        assertEquals(2, matches.size());
        assertEquals("微信支付", text.substring(matches.get(0).startUtf16(), matches.get(0).endUtf16()));
        assertEquals(0, matches.get(0).startUtf16());
        assertEquals(4, matches.get(0).endUtf16());

        assertEquals("支付", text.substring(matches.get(1).startUtf16(), matches.get(1).endUtf16()));
        assertEquals(4, matches.get(1).startUtf16());
        assertEquals(6, matches.get(1).endUtf16());
    }

    @Test
    public void testCN004_ChineseBoundary() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("测试", null)
                .build();

        String text = "这是一次 测试 吗？";
        // WHITESPACE boundary
        AcScanOptions wsOpt = AcScanOptions.builder().boundaryPolicy(Boundaries.WHITESPACE).build();
        assertTrue(automaton.contains(text, wsOpt));

        String textNoWs = "这是一次测试吗？";
        assertFalse(automaton.contains(textNoWs, wsOpt));
        assertTrue(automaton.contains(textNoWs, AcScanOptions.ALL)); // NONE boundary matches
    }

    // ==========================================
    // 24.4 Unicode (UNI-001 ~ UNI-012)
    // ==========================================

    @Test
    public void testUNI002_SingleSupplementaryEmoji() {
        String emoji = "\uD83D\uDE00"; // 😀 code point U+1F600, UTF-16 length 2
        AcAutomaton<String> automaton = AcAutomaton.<String>builder()
                .addKeyword(10, emoji, "SMILE")
                .build();

        String text = "Hello 😀 world";
        assertTrue(automaton.contains(text));
        List<AcMatch<String>> matches = automaton.findAll(text);
        assertEquals(1, matches.size());
        assertEquals(6, matches.get(0).startUtf16());
        assertEquals(8, matches.get(0).endUtf16());
        assertEquals(2, matches.get(0).lengthUtf16());
        assertEquals(emoji, text.substring(matches.get(0).startUtf16(), matches.get(0).endUtf16()));
    }

    @Test
    public void testUNI004_KeywordAfterSupplementary() {
        String emoji = "\uD83D\uDE80"; // 🚀
        AcAutomaton<String> automaton = AcAutomaton.<String>builder()
                .addKeyword(1, "launch", "ROCKET")
                .build();

        String text = "3,2,1 " + emoji + " launch!";
        List<AcMatch<String>> matches = automaton.findAll(text);
        assertEquals(1, matches.size());
        int start = matches.get(0).startUtf16();
        int end = matches.get(0).endUtf16();
        assertEquals("launch", text.substring(start, end));
    }

    @Test
    public void testUNI005_UnpairedSurrogateReject() {
        String invalidKeyword = "abc" + "\uD83D"; // Unpaired high surrogate
        assertThrows(InvalidUnicodeException.class, () -> {
            AcAutomaton.<Void>builder()
                    .textTransform(TextTransformConfig.builder()
                            .invalidKeywordSurrogatePolicy(InvalidSurrogatePolicy.REJECT)
                            .build())
                    .addKeyword(invalidKeyword, null)
                    .build();
        });
    }

    @Test
    public void testUNI006_UnpairedSurrogateReplace() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .textTransform(TextTransformConfig.builder()
                        .invalidInputSurrogatePolicy(InvalidSurrogatePolicy.REPLACE)
                        .build())
                .addKeyword("test", null)
                .build();

        String text = "abc\uD83Dtest"; // Unpaired surrogate before test
        assertTrue(automaton.contains(text));
        List<AcMatch<Void>> matches = automaton.findAll(text);
        assertEquals(1, matches.size());
        assertEquals("test", text.substring(matches.get(0).startUtf16(), matches.get(0).endUtf16()));
    }

    @Test
    public void testUNI009_SimpleCaseFold() {
        AcAutomaton<String> automaton = AcAutomaton.<String>builder()
                .textTransform(TextTransformConfig.builder()
                        .caseFold(CaseFoldMode.SIMPLE)
                        .build())
                .addKeyword(1, "Apple", "FRUIT")
                .build();

        assertTrue(automaton.contains("APPLE"));
        assertTrue(automaton.contains("apple"));
        assertTrue(automaton.contains("ApPlE"));

        List<AcMatch<String>> matches = automaton.findAll("Eat an aPple please");
        assertEquals(1, matches.size());
        assertEquals("aPple", "Eat an aPple please".substring(matches.get(0).startUtf16(), matches.get(0).endUtf16()));
    }

    // ==========================================
    // 24.5 重复与 ID (DUP-001 ~ DUP-006)
    // ==========================================

    @Test
    public void testDUP001_DuplicateKeywordId() {
        assertThrows(IllegalArgumentException.class, () -> {
            AcAutomaton.<Void>builder()
                    .addKeyword(1, "apple", null)
                    .addKeyword(1, "banana", null)
                    .build();
        });
    }

    @Test
    public void testDUP003_DuplicateNormalizedReject() {
        assertThrows(DuplicateKeywordException.class, () -> {
            AcAutomaton.<Void>builder()
                    .textTransform(TextTransformConfig.builder().caseFold(CaseFoldMode.SIMPLE).build())
                    .duplicatePolicy(DuplicateKeywordPolicy.REJECT_NORMALIZED)
                    .addKeyword(1, "Apple", null)
                    .addKeyword(2, "apple", null)
                    .build();
        });
    }

    @Test
    public void testDUP004_KeepFirst() {
        AcAutomaton<String> automaton = AcAutomaton.<String>builder()
                .textTransform(TextTransformConfig.builder().caseFold(CaseFoldMode.SIMPLE).build())
                .duplicatePolicy(DuplicateKeywordPolicy.KEEP_FIRST)
                .addKeyword(1, "Apple", "FIRST")
                .addKeyword(2, "apple", "SECOND")
                .build();

        AcMatch<String> match = automaton.findAny("apple");
        assertNotNull(match);
        assertEquals(1, match.keywordId());
        assertEquals("FIRST", match.payload());
    }

    @Test
    public void testDUP005_KeepLast() {
        AcAutomaton<String> automaton = AcAutomaton.<String>builder()
                .textTransform(TextTransformConfig.builder().caseFold(CaseFoldMode.SIMPLE).build())
                .duplicatePolicy(DuplicateKeywordPolicy.KEEP_LAST)
                .addKeyword(1, "Apple", "FIRST")
                .addKeyword(2, "apple", "LAST")
                .build();

        AcMatch<String> match = automaton.findAny("apple");
        assertNotNull(match);
        assertEquals(2, match.keywordId());
        assertEquals("LAST", match.payload());
    }

    // ==========================================
    // 24.6 API 与异常 (API-001 ~ API-009)
    // ==========================================

    @Test
    public void testAPI001_NullTextNPE() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder().addKeyword("a", null).build();
        assertThrows(NullPointerException.class, () -> automaton.contains(null));
        assertThrows(NullPointerException.class, () -> automaton.findAll(null));
        assertThrows(NullPointerException.class, () -> automaton.scan(null, AcScanOptions.ALL, (s, e, id, p) -> MatchDecision.CONTINUE));
    }

    @Test
    public void testAPI002_NullCallbackNPE() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder().addKeyword("a", null).build();
        assertThrows(NullPointerException.class, () -> automaton.scan("a", AcScanOptions.ALL, null));
    }

    @Test
    public void testAPI003_EmptyKeyword() {
        assertThrows(IllegalArgumentException.class, () -> {
            AcAutomaton.<Void>builder().addKeyword("", null);
        });
    }

    @Test
    public void testAPI004_CallbackStop() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("a", null)
                .build();

        AtomicInteger count = new AtomicInteger();
        int emitted = automaton.scan("aaaaa", AcScanOptions.ALL, (s, e, id, p) -> {
            count.incrementAndGet();
            return MatchDecision.STOP; // Stop after first match
        });

        assertEquals(1, count.get());
        assertEquals(1, emitted);
    }

    @Test
    public void testAPI006_MaxMatchesStop() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("a", null)
                .build();

        AcScanOptions opt = AcScanOptions.builder()
                .maxMatches(2)
                .matchLimitAction(MatchLimitAction.STOP)
                .build();

        List<AcMatch<Void>> matches = automaton.findAll("aaaaa", opt);
        assertEquals(2, matches.size());
    }

    @Test
    public void testAPI007_MaxMatchesThrow() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("a", null)
                .build();

        AcScanOptions opt = AcScanOptions.builder()
                .maxMatches(2)
                .matchLimitAction(MatchLimitAction.THROW)
                .build();

        assertThrows(AcMatchLimitExceededException.class, () -> {
            automaton.findAll("aaaaa", opt);
        });
    }

    // ==========================================
    // 24.7 Tokenize (TOK-001 ~ TOK-005)
    // ==========================================

    @Test
    public void testTOK001_NoMatch() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("foo", null)
                .build();

        List<AcToken<Void>> tokens = automaton.tokenize("hello world", AcScanOptions.LEFTMOST_LONGEST);
        assertEquals(1, tokens.size());
        assertEquals(AcToken.Type.FRAGMENT, tokens.get(0).type());
        assertEquals(0, tokens.get(0).startUtf16());
        assertEquals(11, tokens.get(0).endUtf16());
    }

    @Test
    public void testTOK002_FullMatch() {
        AcAutomaton<Void> automaton = AcAutomaton.<Void>builder()
                .addKeyword("hello", null)
                .build();

        List<AcToken<Void>> tokens = automaton.tokenize("hello", AcScanOptions.LEFTMOST_LONGEST);
        assertEquals(1, tokens.size());
        assertEquals(AcToken.Type.MATCH, tokens.get(0).type());
        assertEquals(0, tokens.get(0).startUtf16());
        assertEquals(5, tokens.get(0).endUtf16());
    }

    @Test
    public void testTOK003_MixedFragments() {
        AcAutomaton<String> automaton = AcAutomaton.<String>builder()
                .addKeyword(1, "quick", "Q")
                .addKeyword(2, "fox", "F")
                .build();

        String text = "The quick brown fox jumps";
        List<AcToken<String>> tokens = automaton.tokenize(text, AcScanOptions.LEFTMOST_LONGEST);
        // FRAGMENT: "The " [0, 4)
        // MATCH: "quick" [4, 9)
        // FRAGMENT: " brown " [9, 16)
        // MATCH: "fox" [16, 19)
        // FRAGMENT: " jumps" [19, 25)
        assertEquals(5, tokens.size());
        assertEquals(AcToken.Type.FRAGMENT, tokens.get(0).type());
        assertEquals("The ", text.substring(tokens.get(0).startUtf16(), tokens.get(0).endUtf16()));

        assertEquals(AcToken.Type.MATCH, tokens.get(1).type());
        assertEquals("quick", text.substring(tokens.get(1).startUtf16(), tokens.get(1).endUtf16()));

        assertEquals(AcToken.Type.FRAGMENT, tokens.get(2).type());
        assertEquals(" brown ", text.substring(tokens.get(2).startUtf16(), tokens.get(2).endUtf16()));

        assertEquals(AcToken.Type.MATCH, tokens.get(3).type());
        assertEquals("fox", text.substring(tokens.get(3).startUtf16(), tokens.get(3).endUtf16()));

        assertEquals(AcToken.Type.FRAGMENT, tokens.get(4).type());
        assertEquals(" jumps", text.substring(tokens.get(4).startUtf16(), tokens.get(4).endUtf16()));
    }

    // ==========================================
    // 24.10 并发压力与原子快照替换
    // ==========================================

    @Test
    public void testConcurrencyAndAtomicSnapshotUpdate() throws Exception {
        AcAutomaton<String> v1 = AcAutomaton.<String>builder()
                .addKeyword(1, "apple", "V1")
                .build();
        AcAutomaton<String> v2 = AcAutomaton.<String>builder()
                .addKeyword(2, "banana", "V2")
                .build();

        AtomicReference<AcAutomaton<String>> repo = new AtomicReference<>(v1);
        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 5000; i++) {
                        AcAutomaton<String> current = repo.get();
                        List<AcMatch<String>> m1 = current.findAll("I have an apple and a banana");
                        for (AcMatch<String> m : m1) {
                            // Match must either belong cleanly to V1 or V2
                            if (m.keywordId() == 1) {
                                assertEquals("V1", m.payload());
                            } else if (m.keywordId() == 2) {
                                assertEquals("V2", m.payload());
                            }
                        }
                    }
                } catch (Exception e) {
                    fail("Exception in thread: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        // Simulate atomic update during query load
        Thread.sleep(50);
        repo.set(v2);

        assertTrue(finishLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();
    }
}

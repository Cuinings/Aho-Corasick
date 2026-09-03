package com.cn.ac.testkit;

import java.util.*;

public final class RandomCorpusGenerator {

    public static final String[] ALPHABET_BINARY = new String[] { "a", "b" };
    public static final String[] ALPHABET_DNA = new String[] { "a", "c", "g", "t" };
    public static final String[] ALPHABET_LOWER = new String[] {
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
    };
    public static final String[] ALPHABET_CJK = new String[] {
            "微", "信", "支", "付", "宝", "风", "险", "赌", "博", "彩",
            "安", "卓", "测", "试", "数", "据", "匹", "配", "搜", "索"
    };
    public static final String[] ALPHABET_EMOJI = new String[] {
            "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE09", "\uD83D\uDC4D", "\uD83C\uDF89",
            "\uD83D\uDD25", "\uD83D\uDE80", "\uD83D\uDCBB"
    };

    public static List<String> generateKeywords(Random rnd, String[] alphabet, int count, int minLen, int maxLen) {
        Set<String> set = new LinkedHashSet<>();
        int attempts = 0;
        while (set.size() < count && attempts < count * 50) {
            attempts++;
            int len = minLen + rnd.nextInt(maxLen - minLen + 1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                sb.append(alphabet[rnd.nextInt(alphabet.length)]);
            }
            String kw = sb.toString();
            if (!kw.isEmpty()) {
                set.add(kw);
            }
        }
        return new ArrayList<>(set);
    }

    public static String generateText(Random rnd, String[] alphabet, List<String> keywordsToInject, int totalCodePoints) {
        StringBuilder sb = new StringBuilder();
        int currentCps = 0;
        while (currentCps < totalCodePoints) {
            if (keywordsToInject != null && !keywordsToInject.isEmpty() && rnd.nextInt(10) < 3) {
                // 30% chance to inject a keyword
                String kw = keywordsToInject.get(rnd.nextInt(keywordsToInject.size()));
                sb.append(kw);
                currentCps += kw.codePointCount(0, kw.length());
            } else {
                String ch = alphabet[rnd.nextInt(alphabet.length)];
                sb.append(ch);
                currentCps++;
            }
        }
        return sb.toString();
    }

    private RandomCorpusGenerator() {}
}

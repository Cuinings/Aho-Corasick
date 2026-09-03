package com.cn.ac.testkit;

import com.cn.ac.*;
import com.cn.ac.internal.CodePointCursor;
import com.cn.ac.internal.SimpleCaseFoldData;

import java.util.*;

public final class NaiveReferenceMatcher<T> {

    public static final class NaiveKeyword<T> {
        public final int keywordId;
        public final String originalText;
        public final T payload;
        public final int[] normalizedCodePoints;

        public NaiveKeyword(int keywordId, String originalText, T payload, int[] normalizedCodePoints) {
            this.keywordId = keywordId;
            this.originalText = originalText;
            this.payload = payload;
            this.normalizedCodePoints = normalizedCodePoints;
        }
    }

    private final List<NaiveKeyword<T>> keywords;
    private final TextTransformConfig config;

    public NaiveReferenceMatcher(List<AcKeyword<T>> rawKeywords, TextTransformConfig config) {
        this.config = config;
        this.keywords = new ArrayList<>();
        for (AcKeyword<T> kw : rawKeywords) {
            int[] cps = normalize(kw.originalText(), config);
            if (cps.length > 0) {
                keywords.add(new NaiveKeyword<>(kw.keywordId(), kw.originalText(), kw.payload(), cps));
            }
        }
    }

    public boolean contains(CharSequence text, AcScanOptions options) {
        List<AcMatch<T>> all = findAll(text, options);
        return !all.isEmpty();
    }

    public AcMatch<T> findFirst(CharSequence text, AcScanOptions options) {
        AcScanOptions opt = (options != null) ? options : AcScanOptions.ALL;
        AcScanOptions allOpt = opt.toBuilder().overlapPolicy(OverlapPolicy.ALL).build();
        List<AcMatch<T>> all = findAll(text, allOpt);
        if (all.isEmpty()) {
            return null;
        }
        return all.get(0);
    }

    public List<AcMatch<T>> findAll(CharSequence text, AcScanOptions options) {
        if (text == null || text.length() == 0) {
            return Collections.emptyList();
        }
        AcScanOptions opt = (options != null) ? options : AcScanOptions.ALL;

        // 1. Transform text and record offsets
        List<Integer> textCps = new ArrayList<>();
        List<Integer> startOffsets = new ArrayList<>();
        List<Integer> endOffsets = new ArrayList<>();

        int len = text.length();
        int offset = 0;
        while (offset < len) {
            int start = offset;
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidInputSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;
            if (config.caseFold() == CaseFoldMode.SIMPLE) {
                cp = SimpleCaseFoldData.fold(cp);
            }
            textCps.add(cp);
            startOffsets.add(start);
            endOffsets.add(nextOffset);
            offset = nextOffset;
        }

        // 2. Find all occurrences
        List<AcMatch<T>> candidates = new ArrayList<>();
        int textCpLen = textCps.size();

        for (int i = 0; i < textCpLen; i++) {
            for (NaiveKeyword<T> kw : keywords) {
                int[] kwCps = kw.normalizedCodePoints;
                if (i + kwCps.length <= textCpLen) {
                    boolean match = true;
                    for (int j = 0; j < kwCps.length; j++) {
                        if (!textCps.get(i + j).equals(kwCps[j])) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        int startUtf16 = startOffsets.get(i);
                        int endUtf16 = endOffsets.get(i + kwCps.length - 1);
                        if (opt.boundaryPolicy().isValid(text, startUtf16, endUtf16)) {
                            candidates.add(new AcMatch<>(startUtf16, endUtf16, kw.keywordId, kw.payload));
                        }
                    }
                }
            }
        }

        // 3. Resolve overlap if needed
        if (opt.overlapPolicy() == OverlapPolicy.LEFTMOST_LONGEST) {
            // Sort: start ASC, length DESC, keywordId ASC
            candidates.sort((a, b) -> {
                int sc = Integer.compare(a.startUtf16(), b.startUtf16());
                if (sc != 0) return sc;
                int lc = Integer.compare(b.lengthUtf16(), a.lengthUtf16());
                if (lc != 0) return lc;
                return Integer.compare(a.keywordId(), b.keywordId());
            });

            List<AcMatch<T>> resolved = new ArrayList<>();
            int lastEnd = -1;
            for (AcMatch<T> c : candidates) {
                if (c.startUtf16() >= lastEnd) {
                    resolved.add(c);
                    lastEnd = c.endUtf16();
                }
            }
            candidates = resolved;
        } else {
            // ALL: default sort START_ASC_LENGTH_DESC_ID_ASC
            candidates.sort((a, b) -> {
                int sc = Integer.compare(a.startUtf16(), b.startUtf16());
                if (sc != 0) return sc;
                int lc = Integer.compare(b.lengthUtf16(), a.lengthUtf16());
                if (lc != 0) return lc;
                return Integer.compare(a.keywordId(), b.keywordId());
            });
        }

        if (candidates.size() > opt.maxMatches()) {
            candidates = new ArrayList<>(candidates.subList(0, opt.maxMatches()));
        }

        return candidates;
    }

    private static int[] normalize(String text, TextTransformConfig config) {
        List<Integer> list = new ArrayList<>();
        int len = text.length();
        int offset = 0;
        while (offset < len) {
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidKeywordSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;
            if (config.caseFold() == CaseFoldMode.SIMPLE) {
                cp = SimpleCaseFoldData.fold(cp);
            }
            list.add(cp);
            offset = nextOffset;
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}

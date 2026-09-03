package com.cn.ac.internal;

import com.cn.ac.*;
import com.cn.ac.exception.AcMatchLimitExceededException;

/**
 * 精确匹配扫描引擎（0 堆内存分配热路径）。
 *
 * <p>在无大小写折叠与无 Unicode 规范化场景下运行，直接在原文 UTF-16 文本上以 Code Point 步进。
 * 具有以下技术保证：
 * <ul>
 *   <li><b>0 字节堆内存分配</b>：整个匹配扫描循环不创建任何对象（无临时 Iterator、无装箱 Integer、无内部 List）。</li>
 *   <li><b>常数级回溯计算</b>：命中词起始偏移直接由 {@code start = nextOffset - keywords.lengthUtf16Exact(slot)} 计算得出。</li>
 *   <li><b>边界快捷旁路</b>：当边界规则为 {@link Boundaries#NONE} 时跳过虚方法调用，实现最高吞吐。</li>
 * </ul>
 */
public final class ExactScanEngine {

    /**
     * 存在性秒检（0 堆内存分配，遇首个合法命中立即返回 true）。
     */
    public static <T> boolean contains(
            CharSequence text,
            PackedAutomatonData data,
            KeywordTable<T> keywords,
            TextTransformConfig config,
            BoundaryPolicy boundary) {

        int len = text.length();
        int offset = 0;
        int state = 0;

        while (offset < len) {
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidInputSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;

            while (state != 0 && data.transition(state, cp) == -1) {
                state = data.failure[state];
            }
            int next = data.transition(state, cp);
            state = (next != -1) ? next : 0;

            if (data.ownOutputCount[state] > 0 || data.outputLink[state] != -1) {
                // Check own outputs
                int startOut = data.ownOutputStart[state];
                int countOut = data.ownOutputCount[state];
                for (int i = 0; i < countOut; i++) {
                    int slot = data.ownOutputKeywordSlot[startOut + i];
                    int kwLen = keywords.lengthUtf16Exact(slot);
                    int start = nextOffset - kwLen;
                    if (boundary.isValid(text, start, nextOffset)) {
                        return true;
                    }
                }
                // Check outputLink
                int outState = data.outputLink[state];
                while (outState != -1) {
                    int sOut = data.ownOutputStart[outState];
                    int cOut = data.ownOutputCount[outState];
                    for (int i = 0; i < cOut; i++) {
                        int slot = data.ownOutputKeywordSlot[sOut + i];
                        int kwLen = keywords.lengthUtf16Exact(slot);
                        int start = nextOffset - kwLen;
                        if (boundary.isValid(text, start, nextOffset)) {
                            return true;
                        }
                    }
                    outState = data.outputLink[outState];
                }
            }
            offset = nextOffset;
        }
        return false;
    }

    public static <T> AcMatch<T> findAny(
            CharSequence text,
            PackedAutomatonData data,
            KeywordTable<T> keywords,
            TextTransformConfig config,
            BoundaryPolicy boundary) {

        int len = text.length();
        int offset = 0;
        int state = 0;

        while (offset < len) {
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidInputSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;

            while (state != 0 && data.transition(state, cp) == -1) {
                state = data.failure[state];
            }
            int next = data.transition(state, cp);
            state = (next != -1) ? next : 0;

            if (data.ownOutputCount[state] > 0 || data.outputLink[state] != -1) {
                int startOut = data.ownOutputStart[state];
                int countOut = data.ownOutputCount[state];
                for (int i = 0; i < countOut; i++) {
                    int slot = data.ownOutputKeywordSlot[startOut + i];
                    int kwLen = keywords.lengthUtf16Exact(slot);
                    int start = nextOffset - kwLen;
                    if (boundary.isValid(text, start, nextOffset)) {
                        return new AcMatch<>(start, nextOffset, keywords.keywordId(slot), keywords.payload(slot));
                    }
                }
                int outState = data.outputLink[state];
                while (outState != -1) {
                    int sOut = data.ownOutputStart[outState];
                    int cOut = data.ownOutputCount[outState];
                    for (int i = 0; i < cOut; i++) {
                        int slot = data.ownOutputKeywordSlot[sOut + i];
                        int kwLen = keywords.lengthUtf16Exact(slot);
                        int start = nextOffset - kwLen;
                        if (boundary.isValid(text, start, nextOffset)) {
                            return new AcMatch<>(start, nextOffset, keywords.keywordId(slot), keywords.payload(slot));
                        }
                    }
                    outState = data.outputLink[outState];
                }
            }
            offset = nextOffset;
        }
        return null;
    }

    public static <T> int scan(
            CharSequence text,
            PackedAutomatonData data,
            KeywordTable<T> keywords,
            TextTransformConfig config,
            AcScanOptions options,
            AcMatchConsumer<? super T> consumer) {

        if (options.overlapPolicy() == OverlapPolicy.LEFTMOST_LONGEST) {
            return scanLeftmostLongest(text, data, keywords, config, options, consumer);
        }

        int len = text.length();
        int offset = 0;
        int state = 0;
        int emitted = 0;
        int maxMatches = options.maxMatches();
        BoundaryPolicy boundary = options.boundaryPolicy();

        boolean checkBoundary = (boundary != null && boundary != Boundaries.NONE);

        while (offset < len) {
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidInputSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;

            while (state != 0 && data.transition(state, cp) == -1) {
                state = data.failure[state];
            }
            int next = data.transition(state, cp);
            state = (next != -1) ? next : 0;

            if (data.ownOutputCount[state] > 0 || data.outputLink[state] != -1) {
                int startOut = data.ownOutputStart[state];
                int countOut = data.ownOutputCount[state];
                for (int i = 0; i < countOut; i++) {
                    int slot = data.ownOutputKeywordSlot[startOut + i];
                    int kwLen = keywords.lengthUtf16Exact(slot);
                    int start = nextOffset - kwLen;
                    if (!checkBoundary || boundary.isValid(text, start, nextOffset)) {
                        if (emitted >= maxMatches) {
                            if (options.matchLimitAction() == MatchLimitAction.THROW) {
                                throw new AcMatchLimitExceededException(maxMatches, "Max matches exceeded: " + maxMatches);
                            }
                            return emitted;
                        }
                        emitted++;
                        MatchDecision decision = consumer.onMatch(start, nextOffset, keywords.keywordId(slot), keywords.payload(slot));
                        if (decision == MatchDecision.STOP) {
                            return emitted;
                        }
                    }
                }

                int outState = data.outputLink[state];
                while (outState != -1) {
                    int sOut = data.ownOutputStart[outState];
                    int cOut = data.ownOutputCount[outState];
                    for (int i = 0; i < cOut; i++) {
                        int slot = data.ownOutputKeywordSlot[sOut + i];
                        int kwLen = keywords.lengthUtf16Exact(slot);
                        int start = nextOffset - kwLen;
                        if (!checkBoundary || boundary.isValid(text, start, nextOffset)) {
                            if (emitted >= maxMatches) {
                                if (options.matchLimitAction() == MatchLimitAction.THROW) {
                                    throw new AcMatchLimitExceededException(maxMatches, "Max matches exceeded: " + maxMatches);
                                }
                                return emitted;
                            }
                            emitted++;
                            MatchDecision decision = consumer.onMatch(start, nextOffset, keywords.keywordId(slot), keywords.payload(slot));
                            if (decision == MatchDecision.STOP) {
                                return emitted;
                            }
                        }
                    }
                    outState = data.outputLink[outState];
                }
            }
            offset = nextOffset;
        }
        return emitted;
    }

    private static <T> int scanLeftmostLongest(
            CharSequence text,
            PackedAutomatonData data,
            KeywordTable<T> keywords,
            TextTransformConfig config,
            AcScanOptions options,
            AcMatchConsumer<? super T> consumer) {

        LeftmostLongestResolver<T> resolver = new LeftmostLongestResolver<>();
        int len = text.length();
        int offset = 0;
        int state = 0;
        BoundaryPolicy boundary = options.boundaryPolicy();

        while (offset < len) {
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidInputSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;

            while (state != 0 && data.transition(state, cp) == -1) {
                state = data.failure[state];
            }
            int next = data.transition(state, cp);
            state = (next != -1) ? next : 0;

            if (data.ownOutputCount[state] > 0 || data.outputLink[state] != -1) {
                int startOut = data.ownOutputStart[state];
                int countOut = data.ownOutputCount[state];
                for (int i = 0; i < countOut; i++) {
                    int slot = data.ownOutputKeywordSlot[startOut + i];
                    int kwLen = keywords.lengthUtf16Exact(slot);
                    int start = nextOffset - kwLen;
                    if (boundary.isValid(text, start, nextOffset)) {
                        resolver.addCandidate(start, nextOffset, slot);
                    }
                }
                int outState = data.outputLink[state];
                while (outState != -1) {
                    int sOut = data.ownOutputStart[outState];
                    int cOut = data.ownOutputCount[outState];
                    for (int i = 0; i < cOut; i++) {
                        int slot = data.ownOutputKeywordSlot[sOut + i];
                        int kwLen = keywords.lengthUtf16Exact(slot);
                        int start = nextOffset - kwLen;
                        if (boundary.isValid(text, start, nextOffset)) {
                            resolver.addCandidate(start, nextOffset, slot);
                        }
                    }
                    outState = data.outputLink[outState];
                }
            }
            offset = nextOffset;
        }

        return resolver.resolve(keywords, (start, end, keywordId, payload) -> {
            return consumer.onMatch(start, end, keywordId, payload);
        });
    }

    private ExactScanEngine() {}
}

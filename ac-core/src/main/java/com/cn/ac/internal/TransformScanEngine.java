package com.cn.ac.internal;

import com.cn.ac.*;
import com.cn.ac.exception.AcMatchLimitExceededException;

/**
 * 文本变换模式扫描引擎（支持大小写折叠与源映射恢复）。
 *
 * <p>在启用 {@link CaseFoldMode#SIMPLE} 或 Unicode 规范化时运行：
 * <ul>
 *   <li><b>即时码点折叠</b>：通过 Unicode 17.0.0 紧凑数据表对码点实时折叠，无需预先生成全量转换后字符串拷贝。</li>
 *   <li><b>单调源映射环形缓冲区（Ring Buffer）</b>：使用固定大小环形缓冲区记录过去 N 个码点的原文 UTF-16 起始偏移，
 *       命中时实现 O(1) 瞬时反查原文准确区间；超长词自适应无分配逆向步进回退（Zero-allocation Fallback）。</li>
 * </ul>
 */
public final class TransformScanEngine {

    /**
     * 存在性检测（变换模式）。
     */
    public static <T> boolean contains(
            CharSequence text,
            PackedAutomatonData data,
            KeywordTable<T> keywords,
            TextTransformConfig config,
            BoundaryPolicy boundary,
            int maxKeywordCodePoints) {

        int len = text.length();
        int offset = 0;
        int state = 0;

        while (offset < len) {
            int currentStart = offset;
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidInputSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;

            if (config.caseFold() == CaseFoldMode.SIMPLE) {
                cp = SimpleCaseFoldData.fold(cp);
            }

            while (state != 0 && data.transition(state, cp) == -1) {
                state = data.failure[state];
            }
            int next = data.transition(state, cp);
            state = (next != -1) ? next : 0;

            if (data.ownOutputCount[state] > 0 || data.outputLink[state] != -1) {
                if (boundary == Boundaries.NONE) {
                    return true;
                }
                // When boundary check is needed, calculate start by stepping back code points
                int startOut = data.ownOutputStart[state];
                int countOut = data.ownOutputCount[state];
                for (int i = 0; i < countOut; i++) {
                    int slot = data.ownOutputKeywordSlot[startOut + i];
                    int kwCpLen = keywords.lengthCodePoint(slot);
                    int start = stepBackCodePoints(text, nextOffset, kwCpLen);
                    if (boundary.isValid(text, start, nextOffset)) {
                        return true;
                    }
                }
                int outState = data.outputLink[state];
                while (outState != -1) {
                    int sOut = data.ownOutputStart[outState];
                    int cOut = data.ownOutputCount[outState];
                    for (int i = 0; i < cOut; i++) {
                        int slot = data.ownOutputKeywordSlot[sOut + i];
                        int kwCpLen = keywords.lengthCodePoint(slot);
                        int start = stepBackCodePoints(text, nextOffset, kwCpLen);
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
            BoundaryPolicy boundary,
            int maxKeywordCodePoints) {

        int len = text.length();
        int offset = 0;
        int state = 0;

        while (offset < len) {
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidInputSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;

            if (config.caseFold() == CaseFoldMode.SIMPLE) {
                cp = SimpleCaseFoldData.fold(cp);
            }

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
                    int kwCpLen = keywords.lengthCodePoint(slot);
                    int start = stepBackCodePoints(text, nextOffset, kwCpLen);
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
                        int kwCpLen = keywords.lengthCodePoint(slot);
                        int start = stepBackCodePoints(text, nextOffset, kwCpLen);
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
            AcScanContext context,
            AcMatchConsumer<? super T> consumer,
            int maxKeywordCodePoints) {

        boolean useContext = (context != null);
        if (useContext) {
            context.markInUse();
        }

        try {
            int ringSize = Math.max(128, maxKeywordCodePoints * 2);
            int[] startRing;
            int[] endRing;

            if (useContext) {
                context.ensureRingCapacity(ringSize);
                startRing = context.sourceStartRing();
                endRing = context.sourceEndRing();
            } else {
                startRing = new int[ringSize];
                endRing = new int[ringSize];
            }

            if (options.overlapPolicy() == OverlapPolicy.LEFTMOST_LONGEST) {
                return scanLeftmostLongest(text, data, keywords, config, options, consumer, startRing, endRing);
            }

            int len = text.length();
            int offset = 0;
            int state = 0;
            int emitted = 0;
            int cpIndex = 0;
            int maxMatches = options.maxMatches();
            BoundaryPolicy boundary = options.boundaryPolicy();
            int ringCap = startRing.length;

            while (offset < len) {
                int currentStart = offset;
                long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidInputSurrogatePolicy());
                int nextOffset = (int) (packed >>> 32);
                int cp = (int) packed;

                int ringIdx = cpIndex % ringCap;
                startRing[ringIdx] = currentStart;
                endRing[ringIdx] = nextOffset;

                if (config.caseFold() == CaseFoldMode.SIMPLE) {
                    cp = SimpleCaseFoldData.fold(cp);
                }

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
                        int kwCpLen = keywords.lengthCodePoint(slot);
                        int startIdx = (cpIndex - kwCpLen + 1) % ringCap;
                        if (startIdx < 0) startIdx += ringCap;
                        int start = startRing[startIdx];
                        int end = nextOffset;

                        if (boundary.isValid(text, start, end)) {
                            if (emitted >= maxMatches) {
                                if (options.matchLimitAction() == MatchLimitAction.THROW) {
                                    throw new AcMatchLimitExceededException(maxMatches, "Max matches exceeded: " + maxMatches);
                                }
                                return emitted;
                            }
                            emitted++;
                            MatchDecision decision = consumer.onMatch(start, end, keywords.keywordId(slot), keywords.payload(slot));
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
                            int kwCpLen = keywords.lengthCodePoint(slot);
                            int startIdx = (cpIndex - kwCpLen + 1) % ringCap;
                            if (startIdx < 0) startIdx += ringCap;
                            int start = startRing[startIdx];
                            int end = nextOffset;

                            if (boundary.isValid(text, start, end)) {
                                if (emitted >= maxMatches) {
                                    if (options.matchLimitAction() == MatchLimitAction.THROW) {
                                        throw new AcMatchLimitExceededException(maxMatches, "Max matches exceeded: " + maxMatches);
                                    }
                                    return emitted;
                                }
                                emitted++;
                                MatchDecision decision = consumer.onMatch(start, end, keywords.keywordId(slot), keywords.payload(slot));
                                if (decision == MatchDecision.STOP) {
                                    return emitted;
                                }
                            }
                        }
                        outState = data.outputLink[outState];
                    }
                }

                offset = nextOffset;
                cpIndex++;
            }
            return emitted;
        } finally {
            if (useContext) {
                context.releaseInUse();
            }
        }
    }

    private static <T> int scanLeftmostLongest(
            CharSequence text,
            PackedAutomatonData data,
            KeywordTable<T> keywords,
            TextTransformConfig config,
            AcScanOptions options,
            AcMatchConsumer<? super T> consumer,
            int[] startRing,
            int[] endRing) {

        LeftmostLongestResolver<T> resolver = new LeftmostLongestResolver<>();
        int len = text.length();
        int offset = 0;
        int state = 0;
        int cpIndex = 0;
        BoundaryPolicy boundary = options.boundaryPolicy();
        int ringCap = startRing.length;

        while (offset < len) {
            int currentStart = offset;
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidInputSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;

            int ringIdx = cpIndex % ringCap;
            startRing[ringIdx] = currentStart;
            endRing[ringIdx] = nextOffset;

            if (config.caseFold() == CaseFoldMode.SIMPLE) {
                cp = SimpleCaseFoldData.fold(cp);
            }

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
                    int kwCpLen = keywords.lengthCodePoint(slot);
                    int startIdx = (cpIndex - kwCpLen + 1) % ringCap;
                    if (startIdx < 0) startIdx += ringCap;
                    int start = startRing[startIdx];
                    int end = nextOffset;

                    if (boundary.isValid(text, start, end)) {
                        resolver.addCandidate(start, end, slot);
                    }
                }
                int outState = data.outputLink[state];
                while (outState != -1) {
                    int sOut = data.ownOutputStart[outState];
                    int cOut = data.ownOutputCount[outState];
                    for (int i = 0; i < cOut; i++) {
                        int slot = data.ownOutputKeywordSlot[sOut + i];
                        int kwCpLen = keywords.lengthCodePoint(slot);
                        int startIdx = (cpIndex - kwCpLen + 1) % ringCap;
                        if (startIdx < 0) startIdx += ringCap;
                        int start = startRing[startIdx];
                        int end = nextOffset;

                        if (boundary.isValid(text, start, end)) {
                            resolver.addCandidate(start, end, slot);
                        }
                    }
                    outState = data.outputLink[outState];
                }
            }
            offset = nextOffset;
            cpIndex++;
        }

        return resolver.resolve(keywords, consumer);
    }

    private static int stepBackCodePoints(CharSequence text, int endOffset, int count) {
        int idx = endOffset;
        for (int i = 0; i < count; i++) {
            idx = Character.offsetByCodePoints(text, idx, -1);
        }
        return idx;
    }

    private TransformScanEngine() {}
}

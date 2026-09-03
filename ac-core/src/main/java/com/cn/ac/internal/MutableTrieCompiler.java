package com.cn.ac.internal;

import com.cn.ac.*;
import com.cn.ac.exception.*;

import java.util.*;

/**
 * Aho-Corasick 自动机编译构建器。
 *
 * <p>本编译器执行从用户原始关键词到不可变紧凑状态机的全套编译流程：
 * <ol>
 *   <li><b>关键词规范化与变换</b>：应用大小写折叠与 Unicode 码点转换，提取 Code Points。</li>
 *   <li><b>重词消解</b>：依据 {@link DuplicateKeywordPolicy} 处理重复词（KEEP_ALL、REJECT 等）。</li>
 *   <li><b>Trie 树构建</b>：利用无装箱哈希表 {@link LongIntTransitionTable} 构建单向树型前缀转移。</li>
 *   <li><b>BFS 失败指针推导</b>：使用基于原始整型数组的队列 {@link IntArrayQueue}，广度优先遍历计算每个状态的
 *       {@code failure[s]} 以及后缀输出链接 {@code outputLink[s]}。</li>
 *   <li><b>状态压缩平铺</b>：将动态转移边按 Code Point 升序平铺为紧凑 {@code int[]} 数组，并生成根节点 ASCII 快速转移表。</li>
 *   <li><b>不可变性校验</b>：通过 {@link AutomatonValidator} 执行拓扑与数据完整性校验。</li>
 * </ol>
 */
public final class MutableTrieCompiler {

    public static final class RawKeyword<T> {
        public final int keywordId;
        public final String originalText;
        public final T payload;

        public RawKeyword(int keywordId, String originalText, T payload) {
            this.keywordId = keywordId;
            this.originalText = originalText;
            this.payload = payload;
        }
    }

    private static final class TransformedKeyword<T> {
        final int keywordId;
        final String originalText;
        final T payload;
        final int[] codePoints;
        final int lengthUtf16Exact;

        TransformedKeyword(int keywordId, String originalText, T payload, int[] codePoints, int lengthUtf16Exact) {
            this.keywordId = keywordId;
            this.originalText = originalText;
            this.payload = payload;
            this.codePoints = codePoints;
            this.lengthUtf16Exact = lengthUtf16Exact;
        }
    }

    /**
     * 编译构建不可变的 {@link AcAutomaton} 实例。
     */
    public static <T> AcAutomaton<T> compile(
            List<RawKeyword<T>> rawKeywords,
            TextTransformConfig transformConfig,
            DuplicateKeywordPolicy duplicatePolicy,
            OriginalKeywordPolicy originalKeywordPolicy,
            OutputEncoding outputEncoding,
            BuildLimits limits,
            boolean deterministicBuild) {

        if (outputEncoding == OutputEncoding.FLATTENED) {
            throw new UnsupportedOperationException("FLATTENED output encoding is not supported in 1.0; use LINKED.");
        }

        if (rawKeywords.size() > limits.maxKeywords()) {
            throw new AcLimitExceededException(AcErrorCode.MAX_KEYWORDS_EXCEEDED,
                    "Keyword count " + rawKeywords.size() + " exceeds limit " + limits.maxKeywords());
        }

        // Validate keyword IDs uniqueness
        Set<Integer> idSet = new HashSet<>();
        for (RawKeyword<T> rk : rawKeywords) {
            if (rk.keywordId < 0) {
                throw new IllegalArgumentException("Negative keywordId: " + rk.keywordId);
            }
            if (!idSet.add(rk.keywordId)) {
                throw new DuplicateKeywordException(AcErrorCode.DUPLICATE_KEYWORD_ID, rk.keywordId, rk.originalText,
                        "Duplicate keywordId: " + rk.keywordId);
            }
        }

        // Transform keywords
        List<TransformedKeyword<T>> transformedList = new ArrayList<>(rawKeywords.size());
        long totalCodePoints = 0;
        int maxKeywordCodePointLength = 0;

        for (RawKeyword<T> rk : rawKeywords) {
            if (rk.originalText == null) {
                throw new NullPointerException("Keyword original text cannot be null");
            }
            if (rk.originalText.isEmpty()) {
                throw new IllegalArgumentException("Keyword text cannot be empty");
            }

            int[] cpArray = parseAndTransform(rk.originalText, transformConfig, limits.maxKeywordCodePoints());
            if (cpArray.length == 0) {
                throw new AcBuildException(AcErrorCode.EMPTY_AFTER_TRANSFORM,
                        "Keyword transformed to empty code points: " + rk.originalText);
            }

            totalCodePoints += cpArray.length;
            if (totalCodePoints > limits.maxTotalNormalizedCodePoints()) {
                throw new AcLimitExceededException(AcErrorCode.MAX_TOTAL_CODE_POINTS_EXCEEDED,
                        "Total normalized code points " + totalCodePoints + " exceeds limit " + limits.maxTotalNormalizedCodePoints());
            }

            if (cpArray.length > maxKeywordCodePointLength) {
                maxKeywordCodePointLength = cpArray.length;
            }

            transformedList.add(new TransformedKeyword<>(
                    rk.keywordId, rk.originalText, rk.payload, cpArray, rk.originalText.length()
            ));
        }

        // Handle duplicate keyword policy
        List<TransformedKeyword<T>> filteredList = handleDuplicates(transformedList, duplicatePolicy);

        // Deterministic sorting
        if (deterministicBuild) {
            filteredList.sort((a, b) -> {
                int cmp = compareCodePoints(a.codePoints, b.codePoints);
                if (cmp != 0) return cmp;
                return Integer.compare(a.keywordId, b.keywordId);
            });
        }

        int finalKeywordCount = filteredList.size();
        if (finalKeywordCount > limits.maxKeywords()) {
            throw new AcLimitExceededException(AcErrorCode.MAX_KEYWORDS_EXCEEDED,
                    "Keyword count exceeds limit: " + finalKeywordCount);
        }

        // Build Trie using primitive transition table
        int estimatedCapacity = Math.max(1024, (int) Math.min(totalCodePoints * 2, 1 << 26));
        LongIntTransitionTable transitionTable = new LongIntTransitionTable(estimatedCapacity);

        int stateCapacity = Math.max(1024, (int) Math.min(totalCodePoints + 1, limits.maxStates()));
        int[] stateDepth = new int[stateCapacity];
        IntVector[] stateOutputs = new IntVector[stateCapacity];

        int stateCount = 1; // root = 0
        stateOutputs[0] = new IntVector(2);

        for (int slot = 0; slot < finalKeywordCount; slot++) {
            TransformedKeyword<T> kw = filteredList.get(slot);
            int curr = 0;
            for (int cp : kw.codePoints) {
                long key = LongIntTransitionTable.makeKey(curr, cp);
                int next = transitionTable.get(key);
                if (next == -1) {
                    if (stateCount >= limits.maxStates()) {
                        throw new AcLimitExceededException(AcErrorCode.MAX_STATES_EXCEEDED,
                                "State count exceeded limit " + limits.maxStates());
                    }
                    next = stateCount++;
                    if (next >= stateDepth.length) {
                        int newCap = Math.max(stateDepth.length * 2, stateCount + 1024);
                        if (newCap > limits.maxStates() + 1) {
                            newCap = limits.maxStates() + 1;
                        }
                        int[] newDepth = new int[newCap];
                        System.arraycopy(stateDepth, 0, newDepth, 0, stateDepth.length);
                        stateDepth = newDepth;

                        IntVector[] newOutputs = new IntVector[newCap];
                        System.arraycopy(stateOutputs, 0, newOutputs, 0, stateOutputs.length);
                        stateOutputs = newOutputs;
                    }
                    stateDepth[next] = stateDepth[curr] + 1;
                    stateOutputs[next] = new IntVector(2);
                    transitionTable.put(key, next);
                }
                curr = next;
            }
            stateOutputs[curr].add(slot);
        }

        int maxDepth = 0;
        for (int s = 0; s < stateCount; s++) {
            if (stateDepth[s] > maxDepth) {
                maxDepth = stateDepth[s];
            }
        }

        // Pack outgoing edges
        int[] edgeCount = new int[stateCount];
        int totalEdges = transitionTable.size();
        if (totalEdges > limits.maxEdges()) {
            throw new AcLimitExceededException(AcErrorCode.MAX_EDGES_EXCEEDED,
                    "Edge count " + totalEdges + " exceeds limit " + limits.maxEdges());
        }

        // Count edges per state
        long[] rawKeys = transitionTable.rawKeys();
        int[] rawValues = transitionTable.rawValues();
        for (int i = 0; i < rawKeys.length; i++) {
            long k = rawKeys[i];
            if (k != -1L) {
                int s = LongIntTransitionTable.keyStateId(k);
                edgeCount[s]++;
            }
        }

        int[] firstEdge = new int[stateCount];
        firstEdge[0] = 0;
        for (int s = 1; s < stateCount; s++) {
            firstEdge[s] = firstEdge[s - 1] + edgeCount[s - 1];
        }

        int[] edgeCodePoint = new int[totalEdges];
        int[] edgeTarget = new int[totalEdges];
        int[] currentEdgePos = new int[stateCount];
        System.arraycopy(firstEdge, 0, currentEdgePos, 0, stateCount);

        for (int i = 0; i < rawKeys.length; i++) {
            long k = rawKeys[i];
            if (k != -1L) {
                int s = LongIntTransitionTable.keyStateId(k);
                int cp = LongIntTransitionTable.keyCodePoint(k);
                int target = rawValues[i];
                int pos = currentEdgePos[s]++;
                edgeCodePoint[pos] = cp;
                edgeTarget[pos] = target;
            }
        }

        // Sort edges of each state ascending by code point
        for (int s = 0; s < stateCount; s++) {
            int start = firstEdge[s];
            int count = edgeCount[s];
            if (count > 1) {
                sortEdges(edgeCodePoint, edgeTarget, start, start + count - 1);
            }
        }

        // Build rootAsciiTarget
        int[] rootAsciiTarget = new int[128];
        Arrays.fill(rootAsciiTarget, -1);
        int rootEdgeStart = firstEdge[0];
        int rootEdgeCount = edgeCount[0];
        for (int i = 0; i < rootEdgeCount; i++) {
            int cp = edgeCodePoint[rootEdgeStart + i];
            if (cp < 128) {
                rootAsciiTarget[cp] = edgeTarget[rootEdgeStart + i];
            }
        }

        // Pack outputs
        int totalOwnOutputs = 0;
        for (int s = 0; s < stateCount; s++) {
            IntVector out = stateOutputs[s];
            if (out != null) {
                totalOwnOutputs += out.size();
            }
        }
        if (totalOwnOutputs > limits.maxOutputs()) {
            throw new AcLimitExceededException(AcErrorCode.MAX_OUTPUTS_EXCEEDED,
                    "Total own outputs " + totalOwnOutputs + " exceeds limit " + limits.maxOutputs());
        }

        int[] ownOutputStart = new int[stateCount];
        int[] ownOutputCount = new int[stateCount];
        int[] ownOutputKeywordSlot = new int[totalOwnOutputs];
        int outputPos = 0;

        for (int s = 0; s < stateCount; s++) {
            IntVector out = stateOutputs[s];
            if (out != null && out.size() > 0) {
                ownOutputStart[s] = outputPos;
                ownOutputCount[s] = out.size();

                // Sort own outputs: lengthCodePoint DESC, then keywordId ASC
                int[] slots = out.toArray();
                sortOutputs(slots, filteredList);

                for (int slot : slots) {
                    ownOutputKeywordSlot[outputPos++] = slot;
                }
            } else {
                ownOutputStart[s] = 0;
                ownOutputCount[s] = 0;
            }
        }

        // Construct failure and outputLink via BFS
        int[] failure = new int[stateCount];
        int[] outputLink = new int[stateCount];
        Arrays.fill(outputLink, -1);
        failure[0] = 0;

        PackedAutomatonData dataStub = new PackedAutomatonData(
                failure, firstEdge, edgeCount, outputLink, ownOutputStart, ownOutputCount,
                edgeCodePoint, edgeTarget, ownOutputKeywordSlot, rootAsciiTarget
        );

        IntArrayQueue queue = new IntArrayQueue(stateCount);
        int rootEdges = edgeCount[0];
        for (int i = 0; i < rootEdges; i++) {
            int child = edgeTarget[firstEdge[0] + i];
            failure[child] = 0;
            outputLink[child] = -1;
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            int r = queue.remove();
            int start = firstEdge[r];
            int count = edgeCount[r];
            for (int i = 0; i < count; i++) {
                int cp = edgeCodePoint[start + i];
                int s = edgeTarget[start + i];
                queue.add(s);

                int f = failure[r];
                while (f != 0 && dataStub.transition(f, cp) == -1) {
                    f = failure[f];
                }

                int candidate = dataStub.transition(f, cp);
                if (candidate != -1 && candidate != s) {
                    failure[s] = candidate;
                } else {
                    failure[s] = 0;
                }

                int fs = failure[s];
                if (ownOutputCount[fs] > 0) {
                    outputLink[s] = fs;
                } else {
                    outputLink[s] = outputLink[fs];
                }
            }
        }

        // Build KeywordTable
        int[] keywordIdBySlot = new int[finalKeywordCount];
        int[] keywordLengthCodePoint = new int[finalKeywordCount];
        int[] keywordLengthUtf16Exact = (transformConfig.caseFold() == CaseFoldMode.NONE &&
                transformConfig.normalization() == NormalizationMode.NONE) ? new int[finalKeywordCount] : null;
        Object[] payloadBySlot = new Object[finalKeywordCount];
        String[] originalKeywordBySlot = (originalKeywordPolicy == OriginalKeywordPolicy.KEEP) ? new String[finalKeywordCount] : null;

        Integer[] sortIndices = new Integer[finalKeywordCount];
        for (int slot = 0; slot < finalKeywordCount; slot++) {
            TransformedKeyword<T> kw = filteredList.get(slot);
            keywordIdBySlot[slot] = kw.keywordId;
            keywordLengthCodePoint[slot] = kw.codePoints.length;
            if (keywordLengthUtf16Exact != null) {
                keywordLengthUtf16Exact[slot] = kw.lengthUtf16Exact;
            }
            payloadBySlot[slot] = kw.payload;
            if (originalKeywordBySlot != null) {
                originalKeywordBySlot[slot] = kw.originalText;
            }
            sortIndices[slot] = slot;
        }

        Arrays.sort(sortIndices, Comparator.comparingInt(slot -> keywordIdBySlot[slot]));
        int[] keywordIdSorted = new int[finalKeywordCount];
        int[] keywordSlotBySortedId = new int[finalKeywordCount];
        for (int i = 0; i < finalKeywordCount; i++) {
            int slot = sortIndices[i];
            keywordIdSorted[i] = keywordIdBySlot[slot];
            keywordSlotBySortedId[i] = slot;
        }

        KeywordTable<T> keywordTable = new KeywordTable<>(
                keywordIdBySlot, keywordLengthCodePoint, keywordLengthUtf16Exact,
                keywordIdSorted, keywordSlotBySortedId, payloadBySlot, originalKeywordBySlot
        );

        // Validate
        AutomatonValidator.validate(dataStub, stateCount, stateDepth, keywordTable);

        // Memory estimation
        long estimatedPrimitiveBytes = (long) stateCount * 24L +
                (long) totalEdges * 8L +
                (long) totalOwnOutputs * 4L +
                (long) finalKeywordCount * 20L +
                512L;

        AcStats stats = new AcStats(
                finalKeywordCount, stateCount, totalEdges, totalOwnOutputs,
                maxDepth, maxKeywordCodePointLength, estimatedPrimitiveBytes,
                transformConfig.unicodeVersion(), transformConfig.fingerprint()
        );

        return new AcAutomaton<>(dataStub, keywordTable, transformConfig, stats);
    }

    private static int[] parseAndTransform(String text, TextTransformConfig config, int maxKeywordCodePoints) {
        int len = text.length();
        int offset = 0;
        IntVector cps = new IntVector(Math.min(len, 16));

        while (offset < len) {
            long packed = CodePointCursor.nextCodePoint(text, offset, len, config.invalidKeywordSurrogatePolicy());
            int nextOffset = (int) (packed >>> 32);
            int cp = (int) packed;
            offset = nextOffset;

            // Apply case folding
            if (config.caseFold() == CaseFoldMode.SIMPLE) {
                cp = SimpleCaseFoldData.fold(cp);
            }

            cps.add(cp);
            if (cps.size() > maxKeywordCodePoints) {
                throw new AcLimitExceededException(AcErrorCode.MAX_KEYWORD_LENGTH_EXCEEDED,
                        "Keyword code points length exceeds limit: " + maxKeywordCodePoints);
            }
        }
        return cps.toArray();
    }

    private static <T> List<TransformedKeyword<T>> handleDuplicates(
            List<TransformedKeyword<T>> list, DuplicateKeywordPolicy policy) {

        if (policy == DuplicateKeywordPolicy.KEEP_ALL) {
            return list;
        }

        Map<CodePointsWrapper, TransformedKeyword<T>> map = new LinkedHashMap<>();
        for (TransformedKeyword<T> kw : list) {
            CodePointsWrapper wrapper = new CodePointsWrapper(kw.codePoints);
            if (policy == DuplicateKeywordPolicy.REJECT_NORMALIZED) {
                if (map.containsKey(wrapper)) {
                    throw new DuplicateKeywordException(AcErrorCode.DUPLICATE_NORMALIZED_KEYWORD,
                            kw.keywordId, kw.originalText,
                            "Duplicate normalized keyword: " + kw.originalText);
                }
                map.put(wrapper, kw);
            } else if (policy == DuplicateKeywordPolicy.KEEP_FIRST) {
                if (!map.containsKey(wrapper)) {
                    map.put(wrapper, kw);
                }
            } else if (policy == DuplicateKeywordPolicy.KEEP_LAST) {
                map.put(wrapper, kw);
            }
        }
        return new ArrayList<>(map.values());
    }

    private static final class CodePointsWrapper {
        final int[] codePoints;
        final int hash;

        CodePointsWrapper(int[] codePoints) {
            this.codePoints = codePoints;
            this.hash = Arrays.hashCode(codePoints);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CodePointsWrapper that = (CodePointsWrapper) o;
            return Arrays.equals(codePoints, that.codePoints);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static <T> void sortOutputs(int[] slots, List<TransformedKeyword<T>> list) {
        // Insertion sort for small array
        for (int i = 1; i < slots.length; i++) {
            int keySlot = slots[i];
            TransformedKeyword<T> keyKw = list.get(keySlot);
            int j = i - 1;
            while (j >= 0) {
                TransformedKeyword<T> prevKw = list.get(slots[j]);
                boolean shouldSwap = false;
                if (prevKw.codePoints.length < keyKw.codePoints.length) {
                    shouldSwap = true; // Length DESC
                } else if (prevKw.codePoints.length == keyKw.codePoints.length) {
                    if (prevKw.keywordId > keyKw.keywordId) {
                        shouldSwap = true; // keywordId ASC
                    }
                }
                if (shouldSwap) {
                    slots[j + 1] = slots[j];
                    j--;
                } else {
                    break;
                }
            }
            slots[j + 1] = keySlot;
        }
    }

    private static void sortEdges(int[] cps, int[] targets, int low, int high) {
        if (low < high) {
            int p = partition(cps, targets, low, high);
            sortEdges(cps, targets, low, p - 1);
            sortEdges(cps, targets, p + 1, high);
        }
    }

    private static int partition(int[] cps, int[] targets, int low, int high) {
        int pivot = cps[high];
        int i = low - 1;
        for (int j = low; j < high; j++) {
            if (cps[j] <= pivot) {
                i++;
                int tempCp = cps[i];
                cps[i] = cps[j];
                cps[j] = tempCp;

                int tempTarget = targets[i];
                targets[i] = targets[j];
                targets[j] = tempTarget;
            }
        }
        int tempCp = cps[i + 1];
        cps[i + 1] = cps[high];
        cps[high] = tempCp;

        int tempTarget = targets[i + 1];
        targets[i + 1] = targets[high];
        targets[high] = tempTarget;
        return i + 1;
    }

    private static int compareCodePoints(int[] a, int[] b) {
        int minLen = Math.min(a.length, b.length);
        for (int i = 0; i < minLen; i++) {
            int cmp = Integer.compare(a[i], b[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }

    private MutableTrieCompiler() {}
}

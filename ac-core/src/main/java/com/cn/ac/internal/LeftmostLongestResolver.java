package com.cn.ac.internal;

import com.cn.ac.AcMatchConsumer;
import com.cn.ac.MatchDecision;

/**
 * 最左优先最长（Leftmost-Longest）重叠消解器。
 *
 * <p>消解算法定义：
 * <ol>
 *   <li>收集所有通过边界过滤的候选匹配项。</li>
 *   <li>按照 {@code startUtf16 升序 -> lengthUtf16 降序 -> keywordId 升序} 规则排序候选集。</li>
 *   <li>线性贪心扫描候选集：若当前候选的 {@code start >= lastEnd}，则保留并发射该匹配，并更新 {@code lastEnd = end}；
 *       若发生重叠（{@code start < lastEnd}），则舍弃该候选。</li>
 * </ol>
 *
 * @param <T> 关键词自定义 Payload 类型
 */
public final class LeftmostLongestResolver<T> {
    private int[] starts = new int[32];
    private int[] ends = new int[32];
    private int[] slots = new int[32];
    private int size = 0;

    /** 清空候选缓冲区，以便在不同扫描调用间安全复用 */
    public void clear() {
        size = 0;
    }

    public void addCandidate(int start, int end, int slot) {
        if (size == starts.length) {
            int newCap = starts.length * 2;
            int[] ns = new int[newCap];
            int[] ne = new int[newCap];
            int[] nsl = new int[newCap];
            System.arraycopy(starts, 0, ns, 0, size);
            System.arraycopy(ends, 0, ne, 0, size);
            System.arraycopy(slots, 0, nsl, 0, size);
            starts = ns;
            ends = ne;
            slots = nsl;
        }
        starts[size] = start;
        ends[size] = end;
        slots[size] = slot;
        size++;
    }

    public int resolve(KeywordTable<T> keywords, AcMatchConsumer<? super T> consumer) {
        if (size == 0) {
            return 0;
        }

        // Sort candidates: start ASC, length DESC (i.e. end DESC), keywordId ASC
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int sa = starts[a];
            int sb = starts[b];
            if (sa != sb) return Integer.compare(sa, sb);

            int ea = ends[a];
            int eb = ends[b];
            if (ea != eb) return Integer.compare(eb, ea); // length DESC

            int ida = keywords.keywordId(slots[a]);
            int idb = keywords.keywordId(slots[b]);
            return Integer.compare(ida, idb); // id ASC
        });

        int emitted = 0;
        int lastEnd = -1;

        for (int i = 0; i < size; i++) {
            int idx = order[i];
            int s = starts[idx];
            int e = ends[idx];
            int slot = slots[idx];

            if (s >= lastEnd) {
                // Non-overlapping with previously accepted
                lastEnd = e;
                emitted++;
                MatchDecision decision = consumer.onMatch(s, e, keywords.keywordId(slot), keywords.payload(slot));
                if (decision == MatchDecision.STOP) {
                    break;
                }
            }
        }
        return emitted;
    }

    public int size() {
        return size;
    }
}

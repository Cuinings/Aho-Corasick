package com.cn.ac.internal;

/**
 * 平铺紧凑原始类型状态机数据核心。
 *
 * <p>本类将 Aho-Corasick 自动机的全部转移拓扑、失败指针及输出表编码为一维 {@code int[]} 数组，
 * 彻底消除了传统实现中每个 TrieNode 的对象头开销以及每节点引用 Map 的垃圾回收压力。
 *
 * <h3>数组布局规范：</h3>
 * <ul>
 *   <li>{@code failure[s]}：状态 {@code s} 的 BFS 失败指针目标状态 ID。</li>
 *   <li>{@code firstEdge[s]} 与 {@code edgeCount[s]}：状态 {@code s} 的所有出边在 {@code edgeCodePoint} / {@code edgeTarget} 数组中的起始下标与出边总数。</li>
 *   <li>{@code outputLink[s]}：沿失败指针链最近的有输出状态 ID（字典后缀链接），无输出则为 -1。</li>
 *   <li>{@code ownOutputStart[s]} 与 {@code ownOutputCount[s]}：状态 {@code s} 自身命中的关键词在 {@code ownOutputKeywordSlot} 中的起始位置与数量。</li>
 *   <li>{@code edgeCodePoint[i]} 与 {@code edgeTarget[i]}：连续平铺的转移字符（Code Point，升序排列）与转移目标状态 ID。</li>
 *   <li>{@code rootAsciiTarget[128]}：根节点 ASCII (0~127) 快速直接索引表，实现常数级 O(1) 转移判定。</li>
 * </ul>
 */
public final class PackedAutomatonData {
    /** 线性查找阈值：出度 {@code <= 16} 时采用连续局部性优异的线性查找，超出则使用二分查找 */
    public static final int LINEAR_THRESHOLD = 16;
    /** 无效状态或未命中常量 */
    public static final int NONE = -1;

    public final int[] failure;
    public final int[] firstEdge;
    public final int[] edgeCount;
    public final int[] outputLink;
    public final int[] ownOutputStart;
    public final int[] ownOutputCount;

    public final int[] edgeCodePoint;
    public final int[] edgeTarget;

    public final int[] ownOutputKeywordSlot;
    public final int[] rootAsciiTarget;

    public PackedAutomatonData(int[] failure, int[] firstEdge, int[] edgeCount,
                               int[] outputLink, int[] ownOutputStart, int[] ownOutputCount,
                               int[] edgeCodePoint, int[] edgeTarget,
                               int[] ownOutputKeywordSlot, int[] rootAsciiTarget) {
        this.failure = failure;
        this.firstEdge = firstEdge;
        this.edgeCount = edgeCount;
        this.outputLink = outputLink;
        this.ownOutputStart = ownOutputStart;
        this.ownOutputCount = ownOutputCount;
        this.edgeCodePoint = edgeCodePoint;
        this.edgeTarget = edgeTarget;
        this.ownOutputKeywordSlot = ownOutputKeywordSlot;
        this.rootAsciiTarget = rootAsciiTarget;
    }

    /**
     * 查询给定状态 {@code state} 在接收字符 {@code cp} 时的目标状态。
     *
     * @param state 当前状态 ID
     * @param cp    输入的 Unicode 码点 (Code Point)
     * @return 目标状态 ID；若无对应出边则返回 {@link #NONE} (-1)
     */
    public int transition(int state, int cp) {
        // 1. 根状态 ASCII 字符直接数组索引 O(1)
        if (state == 0 && cp < 128 && rootAsciiTarget != null) {
            return rootAsciiTarget[cp];
        }

        int count = edgeCount[state];
        if (count == 0) {
            return NONE;
        }

        int start = firstEdge[state];
        // 2. 小出度节点：CPU 缓存局部性极高的线性紧凑扫描
        if (count <= LINEAR_THRESHOLD) {
            int end = start + count;
            for (int i = start; i < end; i++) {
                int ecp = edgeCodePoint[i];
                if (ecp == cp) {
                    return edgeTarget[i];
                }
                if (ecp > cp) {
                    return NONE; // 出边严格按 Code Point 升序排列，可提前剪枝退出
                }
            }
            return NONE;
        }

        // 3. 大出度节点：对严格升序的转移边进行二分查找
        int low = start;
        int high = start + count - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = edgeCodePoint[mid];

            if (midVal < cp) {
                low = mid + 1;
            } else if (midVal > cp) {
                high = mid - 1;
            } else {
                return edgeTarget[mid];
            }
        }
        return NONE;
    }
}

package com.cn.ac.internal;

import com.cn.ac.exception.AcBuildException;
import com.cn.ac.exception.AcErrorCode;

import java.util.Arrays;

/**
 * 构建阶段使用的低开销长整型-整型开放寻址哈希表。
 *
 * <p>本结构专为 Trie 树构建阶段的转移边存储设计，替代传统实现中为每个状态节点分配一个 {@code HashMap} 的做法。
 * 具有以下技术优势：
 * <ul>
 *   <li><b>零装箱开销</b>：底层由连续的原始类型数组 {@code long[] keys} 和 {@code int[] values} 组成。</li>
 *   <li><b>复合 Key 紧凑打包</b>：利用 64 位 long 紧凑编码 {@code (stateId << 21) | codePoint}。
 *       其中 Unicode Code Point 仅需 21 位（0x0 ~ 0x10FFFF），高 43 位存放 stateId（支持高达数万亿状态）。</li>
 *   <li><b>开放寻址与快速探测</b>：线性探测（Linear Probing）结合 Staff9 高位散列函数，缓存命中率极高。</li>
 * </ul>
 */
public final class LongIntTransitionTable {
    private static final long EMPTY_KEY = -1L;
    private static final float LOAD_FACTOR = 0.70f;

    private long[] keys;
    private int[] values;
    private int size;
    private int threshold;
    private int mask;

    public LongIntTransitionTable(int initialCapacity) {
        int cap = 1;
        while (cap < initialCapacity) {
            cap <<= 1;
        }
        if (cap < 16) cap = 16;
        allocate(cap);
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        Arrays.fill(keys, EMPTY_KEY);
        values = new int[capacity];
        mask = capacity - 1;
        threshold = (int) (capacity * LOAD_FACTOR);
    }

    /**
     * 将 (stateId, codePoint) 紧凑打包为 64 位 long key。
     */
    public static long makeKey(int stateId, int codePoint) {
        return (((long) stateId) << 21) | (codePoint & 0x1FFFFFL);
    }

    /** 从打包 key 中还原 stateId */
    public static int keyStateId(long key) {
        return (int) (key >>> 21);
    }

    /** 从打包 key 中还原 codePoint */
    public static int keyCodePoint(long key) {
        return (int) (key & 0x1FFFFFL);
    }

    /** 获取当前表中键值对总数 */
    public int size() {
        return size;
    }

    /**
     * 根据 key 查找目标状态 ID。
     *
     * @param key 打包后的 64 位键
     * @return 目标状态 ID；若未找到返回 -1
     */
    public int get(long key) {
        int idx = (int) (mix(key) & mask);
        while (true) {
            long k = keys[idx];
            if (k == key) {
                return values[idx];
            }
            if (k == EMPTY_KEY) {
                return -1;
            }
            idx = (idx + 1) & mask;
        }
    }

    /**
     * 写入键值对。
     *
     * @param key   打包后的 64 位键
     * @param value 目标状态 ID
     */
    public void put(long key, int value) {
        if (size >= threshold) {
            rehash();
        }
        int idx = (int) (mix(key) & mask);
        while (true) {
            long k = keys[idx];
            if (k == key) {
                values[idx] = value;
                return;
            }
            if (k == EMPTY_KEY) {
                keys[idx] = key;
                values[idx] = value;
                size++;
                return;
            }
            idx = (idx + 1) & mask;
        }
    }

    private void rehash() {
        int newCap = keys.length << 1;
        if (newCap < 0) {
            throw new AcBuildException(AcErrorCode.INTEGER_OVERFLOW, "Transition table size exceeded 32-bit limit");
        }
        long[] oldKeys = keys;
        int[] oldValues = values;

        allocate(newCap);
        size = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            long k = oldKeys[i];
            if (k != EMPTY_KEY) {
                put(k, oldValues[i]);
            }
        }
    }

    public long[] rawKeys() {
        return keys;
    }

    public int[] rawValues() {
        return values;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}

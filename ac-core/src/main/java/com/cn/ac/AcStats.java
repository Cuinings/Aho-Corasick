package com.cn.ac;

/**
 * 自动机构建与运行时状态指标统计模型。
 *
 * <p>包含自动机的词库容量、状态机拓扑规模（节点数、转移边数）、最大码点深度、
 * 预估堆内存占用（基于平铺原始类型数组计算）以及匹配规则指纹。
 */
public final class AcStats {
    private final int keywordCount;
    private final int stateCount;
    private final int edgeCount;
    private final int ownOutputCount;
    private final int maxDepthCodePoint;
    private final int maxKeywordCodePointLength;
    private final long estimatedPrimitiveBytes;
    private final String unicodeVersion;
    private final long transformFingerprint;

    public AcStats(int keywordCount, int stateCount, int edgeCount, int ownOutputCount,
                   int maxDepthCodePoint, int maxKeywordCodePointLength,
                   long estimatedPrimitiveBytes, String unicodeVersion, long transformFingerprint) {
        this.keywordCount = keywordCount;
        this.stateCount = stateCount;
        this.edgeCount = edgeCount;
        this.ownOutputCount = ownOutputCount;
        this.maxDepthCodePoint = maxDepthCodePoint;
        this.maxKeywordCodePointLength = maxKeywordCodePointLength;
        this.estimatedPrimitiveBytes = estimatedPrimitiveBytes;
        this.unicodeVersion = unicodeVersion;
        this.transformFingerprint = transformFingerprint;
    }

    /** 词库中有效关键词总数 */
    public int keywordCount() {
        return keywordCount;
    }

    /** 自动机总状态（节点）数 */
    public int stateCount() {
        return stateCount;
    }

    /** 自动机总有效转移边数量 */
    public int edgeCount() {
        return edgeCount;
    }

    /** 自动机各状态直接关联的自身输出项数量 */
    public int ownOutputCount() {
        return ownOutputCount;
    }

    /** 状态机 Trie 树最大 Code Point 深度 */
    public int maxDepthCodePoint() {
        return maxDepthCodePoint;
    }

    /** 词库中最长关键词的 Code Point 长度（决定回退缓冲区大小） */
    public int maxKeywordCodePointLength() {
        return maxKeywordCodePointLength;
    }

    /** 底层平铺原始类型数组占用的预估堆内存字节数（bytes） */
    public long estimatedPrimitiveBytes() {
        return estimatedPrimitiveBytes;
    }

    /** 构建时绑定的 Unicode 规范版本号（如 "17.0.0"） */
    public String unicodeVersion() {
        return unicodeVersion;
    }

    /** 文本变换配置生成的唯一规则指纹（用于跨快照一致性校验） */
    public long transformFingerprint() {
        return transformFingerprint;
    }

    @Override
    public String toString() {
        return "AcStats{" +
                "keywords=" + keywordCount +
                ", states=" + stateCount +
                ", edges=" + edgeCount +
                ", ownOutputs=" + ownOutputCount +
                ", maxDepth=" + maxDepthCodePoint +
                ", maxKwCpLen=" + maxKeywordCodePointLength +
                ", bytes=" + estimatedPrimitiveBytes +
                ", unicode=" + unicodeVersion +
                '}';
    }
}

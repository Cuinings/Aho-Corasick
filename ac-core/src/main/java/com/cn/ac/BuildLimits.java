package com.cn.ac;

/**
 * 自动机构建阶段的资源上限配置（构建门限防御）。
 *
 * <p>用于限制词库大小、单词长度、状态总数与边总数，防止异常词库导致内存溢出（OOM）或耗尽系统资源。
 */
public final class BuildLimits {
    private final int maxKeywords;
    private final int maxKeywordCodePoints;
    private final long maxTotalNormalizedCodePoints;
    private final int maxStates;
    private final int maxEdges;
    private final int maxOutputs;

    private BuildLimits(Builder builder) {
        this.maxKeywords = builder.maxKeywords;
        this.maxKeywordCodePoints = builder.maxKeywordCodePoints;
        this.maxTotalNormalizedCodePoints = builder.maxTotalNormalizedCodePoints;
        this.maxStates = builder.maxStates;
        this.maxEdges = builder.maxEdges;
        this.maxOutputs = builder.maxOutputs;
    }

    /** 最大允许的关键词数量 */
    public int maxKeywords() {
        return maxKeywords;
    }

    /** 单个关键词的最大 Code Point 码点长度 */
    public int maxKeywordCodePoints() {
        return maxKeywordCodePoints;
    }

    /** 所有关键词规范化后的码点总数上限 */
    public long maxTotalNormalizedCodePoints() {
        return maxTotalNormalizedCodePoints;
    }

    /** 状态机允许生成的最大状态节点数 */
    public int maxStates() {
        return maxStates;
    }

    /** 状态机允许生成的最大转移边数量 */
    public int maxEdges() {
        return maxEdges;
    }

    /** 状态机输出项总数上限 */
    public int maxOutputs() {
        return maxOutputs;
    }

    /** 创建 Builder 实例 */
    public static Builder builder() {
        return new Builder();
    }

    /** 针对服务器/标准 JVM 的宽松安全默认配置 */
    public static BuildLimits integerSafeDefaults() {
        return builder()
                .maxKeywords(10_000_000)
                .maxKeywordCodePoints(65_535)
                .maxTotalNormalizedCodePoints(100_000_000L)
                .maxStates(50_000_000)
                .maxEdges(50_000_000)
                .maxOutputs(20_000_000)
                .build();
    }

    /** 针对 Android 移动端推荐的防御性资源配置（防止移动设备 OOM） */
    public static BuildLimits androidRecommended() {
        return builder()
                .maxKeywords(500_000)
                .maxKeywordCodePoints(2_048)
                .maxTotalNormalizedCodePoints(10_000_000L)
                .maxStates(2_000_000)
                .maxEdges(3_000_000)
                .maxOutputs(1_000_000)
                .build();
    }

    /**
     * {@link BuildLimits} 构建器。
     */
    public static final class Builder {
        private int maxKeywords = 1_000_000;
        private int maxKeywordCodePoints = 4_096;
        private long maxTotalNormalizedCodePoints = 20_000_000L;
        private int maxStates = 5_000_000;
        private int maxEdges = 10_000_000;
        private int maxOutputs = 2_000_000;

        public Builder maxKeywords(int value) {
            this.maxKeywords = value;
            return this;
        }

        public Builder maxKeywordCodePoints(int value) {
            this.maxKeywordCodePoints = value;
            return this;
        }

        public Builder maxTotalNormalizedCodePoints(long value) {
            this.maxTotalNormalizedCodePoints = value;
            return this;
        }

        public Builder maxStates(int value) {
            this.maxStates = value;
            return this;
        }

        public Builder maxEdges(int value) {
            this.maxEdges = value;
            return this;
        }

        public Builder maxOutputs(int value) {
            this.maxOutputs = value;
            return this;
        }

        public BuildLimits build() {
            return new BuildLimits(this);
        }
    }
}

package com.cn.ac;

import java.util.Objects;

/**
 * 文本扫描运行时选项配置。
 *
 * <p>控制匹配重叠消解策略、边界判断规则、结果发射排序、最大匹配命中上限及超出上限时的响应动作。
 */
public final class AcScanOptions {

    /**
     * 全量匹配预设选项（保留所有重叠、无边界过滤、按 {@code START_ASC_LENGTH_DESC_ID_ASC} 排序）。
     */
    public static final AcScanOptions ALL = builder()
            .overlapPolicy(OverlapPolicy.ALL)
            .boundaryPolicy(Boundaries.NONE)
            .emissionOrder(EmissionOrder.START_ASC_LENGTH_DESC_ID_ASC)
            .maxMatches(Integer.MAX_VALUE)
            .matchLimitAction(MatchLimitAction.STOP)
            .build();

    /**
     * 最左优先最长重叠消除预设选项（贪心非重叠消解、无边界过滤、按 {@code START_ASC_LENGTH_DESC_ID_ASC} 排序）。
     */
    public static final AcScanOptions LEFTMOST_LONGEST = builder()
            .overlapPolicy(OverlapPolicy.LEFTMOST_LONGEST)
            .boundaryPolicy(Boundaries.NONE)
            .emissionOrder(EmissionOrder.START_ASC_LENGTH_DESC_ID_ASC)
            .maxMatches(Integer.MAX_VALUE)
            .matchLimitAction(MatchLimitAction.STOP)
            .build();

    private final OverlapPolicy overlapPolicy;
    private final BoundaryPolicy boundaryPolicy;
    private final EmissionOrder emissionOrder;
    private final int maxMatches;
    private final MatchLimitAction matchLimitAction;

    private AcScanOptions(Builder builder) {
        this.overlapPolicy = builder.overlapPolicy;
        this.boundaryPolicy = builder.boundaryPolicy;
        this.emissionOrder = builder.emissionOrder;
        this.maxMatches = builder.maxMatches;
        this.matchLimitAction = builder.matchLimitAction;
    }

    /** 获取重叠消除策略 */
    public OverlapPolicy overlapPolicy() {
        return overlapPolicy;
    }

    /** 获取词边界判断策略 */
    public BoundaryPolicy boundaryPolicy() {
        return boundaryPolicy;
    }

    /** 获取结果发射顺序 */
    public EmissionOrder emissionOrder() {
        return emissionOrder;
    }

    /** 获取最大允许命中的匹配项数量 */
    public int maxMatches() {
        return maxMatches;
    }

    /** 获取达到最大命中上限时的处理行为（STOP 或 THROW） */
    public MatchLimitAction matchLimitAction() {
        return matchLimitAction;
    }

    /** 创建 Builder 实例 */
    public static Builder builder() {
        return new Builder();
    }

    /** 基于当前配置复制并衍生新的 Builder */
    public Builder toBuilder() {
        return new Builder()
                .overlapPolicy(this.overlapPolicy)
                .boundaryPolicy(this.boundaryPolicy)
                .emissionOrder(this.emissionOrder)
                .maxMatches(this.maxMatches)
                .matchLimitAction(this.matchLimitAction);
    }

    /**
     * {@link AcScanOptions} 构建器。
     */
    public static final class Builder {
        private OverlapPolicy overlapPolicy = OverlapPolicy.ALL;
        private BoundaryPolicy boundaryPolicy = Boundaries.NONE;
        private EmissionOrder emissionOrder = EmissionOrder.START_ASC_LENGTH_DESC_ID_ASC;
        private int maxMatches = Integer.MAX_VALUE;
        private MatchLimitAction matchLimitAction = MatchLimitAction.STOP;

        /** 设置重叠策略（ALL 全量 或 LEFTMOST_LONGEST 最左最长） */
        public Builder overlapPolicy(OverlapPolicy value) {
            this.overlapPolicy = Objects.requireNonNull(value, "overlapPolicy");
            return this;
        }

        /** 设置边界过滤规则（如 Boundaries.WORD, Boundaries.NONE） */
        public Builder boundaryPolicy(BoundaryPolicy value) {
            this.boundaryPolicy = Objects.requireNonNull(value, "boundaryPolicy");
            return this;
        }

        /** 设置结果发射排序顺序 */
        public Builder emissionOrder(EmissionOrder value) {
            this.emissionOrder = Objects.requireNonNull(value, "emissionOrder");
            return this;
        }

        /** 设置最大命中匹配数上限 */
        public Builder maxMatches(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("maxMatches cannot be negative: " + value);
            }
            this.maxMatches = value;
            return this;
        }

        /** 设置达到最大命中上限时的处理动作（STOP 截断终止 或 THROW 抛异常） */
        public Builder matchLimitAction(MatchLimitAction value) {
            this.matchLimitAction = Objects.requireNonNull(value, "matchLimitAction");
            return this;
        }

        /** 构建不可变的 {@link AcScanOptions} 实例 */
        public AcScanOptions build() {
            return new AcScanOptions(this);
        }
    }
}

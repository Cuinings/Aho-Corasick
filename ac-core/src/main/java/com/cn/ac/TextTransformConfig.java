package com.cn.ac;

import java.util.Objects;

/**
 * 文本预处理与变换规则配置。
 *
 * <p>控制 Unicode 规范化（NFC/NFD）、大小写折叠（ASCII / SIMPLE）、孤立代理字符（Lone Surrogate）策略，
 * 并自动计算变换配置指纹（Fingerprint）以保障快照加载的一致性。
 */
public final class TextTransformConfig {
    /** 绑定的 Unicode 规范版本 */
    public static final String UNICODE_VERSION = "17.0.0";

    private final NormalizationMode normalization;
    private final CaseFoldMode caseFold;
    private final InvalidSurrogatePolicy invalidKeywordSurrogatePolicy;
    private final InvalidSurrogatePolicy invalidInputSurrogatePolicy;
    private final String unicodeVersion;
    private final long fingerprint;

    private TextTransformConfig(Builder builder) {
        this.normalization = builder.normalization;
        this.caseFold = builder.caseFold;
        this.invalidKeywordSurrogatePolicy = builder.invalidKeywordSurrogatePolicy;
        this.invalidInputSurrogatePolicy = builder.invalidInputSurrogatePolicy;
        this.unicodeVersion = UNICODE_VERSION;
        this.fingerprint = computeFingerprint(this.normalization, this.caseFold,
                this.invalidKeywordSurrogatePolicy, this.invalidInputSurrogatePolicy, this.unicodeVersion);
    }

    /** 创建精确匹配预设配置（无大小写折叠、无规范化、代理对错误抛异常） */
    public static TextTransformConfig exact() {
        return builder().build();
    }

    /** 创建 Builder 实例 */
    public static Builder builder() {
        return new Builder();
    }

    /** 获取 Unicode 规范化模式 */
    public NormalizationMode normalization() {
        return normalization;
    }

    /** 获取大小写折叠模式 */
    public CaseFoldMode caseFold() {
        return caseFold;
    }

    /** 获取构建阶段关键词中孤立代理字符的处理策略 */
    public InvalidSurrogatePolicy invalidKeywordSurrogatePolicy() {
        return invalidKeywordSurrogatePolicy;
    }

    /** 获取运行时待匹配输入文本中孤立代理字符的处理策略 */
    public InvalidSurrogatePolicy invalidInputSurrogatePolicy() {
        return invalidInputSurrogatePolicy;
    }

    /** 获取 Unicode 规范版本号 */
    public String unicodeVersion() {
        return unicodeVersion;
    }

    /** 获取变换配置唯一规则指纹 */
    public long fingerprint() {
        return fingerprint;
    }

    private static long computeFingerprint(NormalizationMode norm, CaseFoldMode fold,
                                           InvalidSurrogatePolicy kwSurr, InvalidSurrogatePolicy inSurr,
                                           String ver) {
        long h = 1125899906842597L;
        h = h * 31 + norm.ordinal();
        h = h * 31 + fold.ordinal();
        h = h * 31 + kwSurr.ordinal();
        h = h * 31 + inSurr.ordinal();
        h = h * 31 + ver.hashCode();
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextTransformConfig that = (TextTransformConfig) o;
        return fingerprint == that.fingerprint &&
                normalization == that.normalization &&
                caseFold == that.caseFold &&
                invalidKeywordSurrogatePolicy == that.invalidKeywordSurrogatePolicy &&
                invalidInputSurrogatePolicy == that.invalidInputSurrogatePolicy &&
                Objects.equals(unicodeVersion, that.unicodeVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalization, caseFold, invalidKeywordSurrogatePolicy, invalidInputSurrogatePolicy, unicodeVersion, fingerprint);
    }

    /**
     * {@link TextTransformConfig} 构建器。
     */
    public static final class Builder {
        private NormalizationMode normalization = NormalizationMode.NONE;
        private CaseFoldMode caseFold = CaseFoldMode.NONE;
        private InvalidSurrogatePolicy invalidKeywordSurrogatePolicy = InvalidSurrogatePolicy.REJECT;
        private InvalidSurrogatePolicy invalidInputSurrogatePolicy = InvalidSurrogatePolicy.REPLACE;

        /** 设置 Unicode 规范化模式（如 NONE, NFC, NFD） */
        public Builder normalization(NormalizationMode value) {
            this.normalization = Objects.requireNonNull(value, "normalization");
            return this;
        }

        /** 设置大小写折叠模式（如 NONE, ASCII, SIMPLE） */
        public Builder caseFold(CaseFoldMode value) {
            this.caseFold = Objects.requireNonNull(value, "caseFold");
            return this;
        }

        /** 设置构建关键词中遇到非法代理对时的策略（REJECT 抛异常 或 REPLACE 替换为 U+FFFD） */
        public Builder invalidKeywordSurrogatePolicy(InvalidSurrogatePolicy value) {
            this.invalidKeywordSurrogatePolicy = Objects.requireNonNull(value, "invalidKeywordSurrogatePolicy");
            return this;
        }

        /** 设置输入文本中遇到非法代理对时的策略 */
        public Builder invalidInputSurrogatePolicy(InvalidSurrogatePolicy value) {
            this.invalidInputSurrogatePolicy = Objects.requireNonNull(value, "invalidInputSurrogatePolicy");
            return this;
        }

        public TextTransformConfig build() {
            return new TextTransformConfig(this);
        }
    }
}

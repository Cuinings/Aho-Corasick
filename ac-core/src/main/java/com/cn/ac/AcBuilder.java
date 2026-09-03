package com.cn.ac;

import com.cn.ac.internal.MutableTrieCompiler;

import java.util.*;

/**
 * {@link AcAutomaton} 的一次性构建器。
 *
 * <p>设计原则：
 * <ul>
 *   <li><b>One-shot 模式</b>：构建器为单次使用，一旦调用 {@link #build()} 即被关闭（closed），
 *       再次调用任何配置或添加方法将抛出 {@link IllegalStateException}，以防状态混乱和内存泄漏。</li>
 *   <li><b>ID 冲突检测</b>：支持自动分配 ID 与显式指定 ID，重复添加相同显式 ID 将被拦截。</li>
 *   <li><b>构建限制保护</b>：遵循 {@link BuildLimits} 规则，对总关键词数、深度、状态数进行上限控制，防止 Android 端 OOM。</li>
 * </ul>
 *
 * @param <T> 关联在关键词上的用户自定义 Payload 类型
 */
public final class AcBuilder<T> {
    private boolean closed = false;

    private final List<MutableTrieCompiler.RawKeyword<T>> rawKeywords = new ArrayList<>();
    private final Set<Integer> explicitIds = new HashSet<>();
    private int nextAutoId = 0;

    private TextTransformConfig transformConfig = TextTransformConfig.exact();
    private DuplicateKeywordPolicy duplicatePolicy = DuplicateKeywordPolicy.KEEP_ALL;
    private OriginalKeywordPolicy originalKeywordPolicy = OriginalKeywordPolicy.KEEP;
    private OutputEncoding outputEncoding = OutputEncoding.LINKED;
    private BuildLimits limits = BuildLimits.androidRecommended();
    private boolean deterministicBuild = true;

    public AcBuilder() {}

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("AcBuilder is closed. Builders are one-shot and cannot be reused after build().");
        }
    }

    /**
     * 添加关键词（系统自动分配自增 keywordId）。
     *
     * @param keyword 关键词原文（不得为空或 null）
     * @param payload 关联的自定义数据（可为 null）
     * @return 当前 Builder 引用
     */
    public AcBuilder<T> addKeyword(String keyword, T payload) {
        checkOpen();
        Objects.requireNonNull(keyword, "keyword");
        if (keyword.isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }
        while (explicitIds.contains(nextAutoId)) {
            nextAutoId++;
        }
        int assignedId = nextAutoId++;
        rawKeywords.add(new MutableTrieCompiler.RawKeyword<>(assignedId, keyword, payload));
        return this;
    }

    /**
     * 添加关键词并显式指定 keywordId。
     *
     * @param keywordId 显式关键词 ID（必须非负且在当前 Builder 内唯一）
     * @param keyword   关键词原文（不得为空或 null）
     * @param payload   关联的自定义数据（可为 null）
     * @return 当前 Builder 引用
     */
    public AcBuilder<T> addKeyword(int keywordId, String keyword, T payload) {
        checkOpen();
        Objects.requireNonNull(keyword, "keyword");
        if (keyword.isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }
        if (keywordId < 0) {
            throw new IllegalArgumentException("keywordId must be non-negative: " + keywordId);
        }
        if (!explicitIds.add(keywordId)) {
            throw new IllegalArgumentException("Duplicate explicit keywordId in builder: " + keywordId);
        }
        rawKeywords.add(new MutableTrieCompiler.RawKeyword<>(keywordId, keyword, payload));
        return this;
    }

    /**
     * 批量添加关键词集合。
     *
     * @param keywords 关键词迭代器
     * @return 当前 Builder 引用
     */
    public AcBuilder<T> addKeywords(Iterable<? extends AcKeyword<T>> keywords) {
        checkOpen();
        Objects.requireNonNull(keywords, "keywords");
        for (AcKeyword<T> kw : keywords) {
            addKeyword(kw.keywordId(), kw.originalText(), kw.payload());
        }
        return this;
    }

    /**
     * 设置文本变换规则（大小写折叠、规范化等）。
     */
    public AcBuilder<T> textTransform(TextTransformConfig config) {
        checkOpen();
        this.transformConfig = Objects.requireNonNull(config, "config");
        return this;
    }

    /**
     * 设置重复关键词处理策略。
     */
    public AcBuilder<T> duplicatePolicy(DuplicateKeywordPolicy policy) {
        checkOpen();
        this.duplicatePolicy = Objects.requireNonNull(policy, "policy");
        return this;
    }

    /**
     * 设置关键词原文保留策略（KEEP 保留用于反查，DISCARD 降低内存）。
     */
    public AcBuilder<T> originalKeywordPolicy(OriginalKeywordPolicy policy) {
        checkOpen();
        this.originalKeywordPolicy = Objects.requireNonNull(policy, "policy");
        return this;
    }

    /**
     * 设置输出表编码策略（FLAT 全量展平 或 LINKED 沿失败链跳转）。
     */
    public AcBuilder<T> outputEncoding(OutputEncoding encoding) {
        checkOpen();
        this.outputEncoding = Objects.requireNonNull(encoding, "encoding");
        return this;
    }

    /**
     * 设置构建阶段的保护阈值（防 OOM 与恶意输入）。
     */
    public AcBuilder<T> limits(BuildLimits limits) {
        checkOpen();
        this.limits = Objects.requireNonNull(limits, "limits");
        return this;
    }

    /**
     * 是否启用确定性构建（对相同关键词集产生绝对一致的内部节点与边顺序）。
     */
    public AcBuilder<T> deterministicBuild(boolean enabled) {
        checkOpen();
        this.deterministicBuild = enabled;
        return this;
    }

    /**
     * 执行编译并生成不可变的 {@link AcAutomaton} 实例。
     *
     * <p>构建完成后当前 Builder 将被永久关闭，不可再次调用。
     *
     * @return 编译就绪的不可变 Aho-Corasick 自动机
     */
    public AcAutomaton<T> build() {
        checkOpen();
        closed = true;
        return MutableTrieCompiler.compile(
                rawKeywords,
                transformConfig,
                duplicatePolicy,
                originalKeywordPolicy,
                outputEncoding,
                limits,
                deterministicBuild
        );
    }
}

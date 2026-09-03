package com.cn.ac;

import com.cn.ac.internal.*;

import java.util.*;

/**
 * 高性能 Aho-Corasick 多模式匹配自动机。
 *
 * <p>本实现专为 Android 与高性能 JVM 场景设计，具有以下核心特性：
 * <ul>
 *   <li><b>不可变与并发安全</b>：构建完成后内部状态不可变，所有只读扫描方法均为完全无锁实现，支持高并发多线程读取。</li>
 *   <li><b>零堆内存分配（0 B/op）</b>：在精确匹配模式下，{@link #contains(CharSequence)} 以及基于回调的
 *       {@link #scan(CharSequence, AcScanOptions, AcMatchConsumer)} 在预热后保证引擎侧 0 byte 堆分配。</li>
 *   <li><b>平铺紧凑数组</b>：运行时状态机转移、失败指针及输出表均以原始类型数组（{@code int[]}）平铺存储，杜绝对象开销。</li>
 *   <li><b>Unicode 17.0.0 原生支持</b>：内部基于 Unicode Code Point 匹配，正确处理 Emoji、变音符号与辅助平面字符。</li>
 * </ul>
 *
 * @param <T> 关联在关键词上的用户自定义 Payload 类型
 */
public final class AcAutomaton<T> {

    private final PackedAutomatonData data;
    private final KeywordTable<T> keywords;
    private final TextTransformConfig transformConfig;
    private final AcStats stats;
    private final boolean isExact;

    /**
     * 构造完整的不可变 Aho-Corasick 自动机。
     *
     * @param data            平铺状态机核心数据（失败指针、转移边、输出表）
     * @param keywords        关键词元数据表（ID、长度、Payload、原文）
     * @param transformConfig 文本转换规则配置（大小写折叠、规范化、代理对策略）
     * @param stats           自动机构建统计信息（状态数、边数、内存估算等）
     */
    public AcAutomaton(
            PackedAutomatonData data,
            KeywordTable<T> keywords,
            TextTransformConfig transformConfig,
            AcStats stats) {
        this.data = Objects.requireNonNull(data, "data");
        this.keywords = Objects.requireNonNull(keywords, "keywords");
        this.transformConfig = Objects.requireNonNull(transformConfig, "transformConfig");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.isExact = (transformConfig.caseFold() == CaseFoldMode.NONE &&
                transformConfig.normalization() == NormalizationMode.NONE);
    }

    /**
     * 创建自动机构建器。
     *
     * @param <T> 关联在关键词上的用户自定义 Payload 类型
     * @return 全新的 {@link AcBuilder} 实例
     */
    public static <T> AcBuilder<T> builder() {
        return new AcBuilder<>();
    }

    /**
     * 检测目标文本中是否存在任意关键词匹配（默认匹配所有规则，遇首个匹配立即返回）。
     *
     * <p>在精确匹配模式下，本方法在引擎侧保证 0 byte 堆内存分配。
     *
     * @param text 待检测的目标文本
     * @return 若存在至少一个匹配项返回 {@code true}，否则返回 {@code false}
     */
    public boolean contains(CharSequence text) {
        return contains(text, AcScanOptions.ALL);
    }

    /**
     * 按照指定扫描选项，检测目标文本中是否存在匹配项。
     *
     * @param text    待检测的目标文本
     * @param options 扫描选项（边界策略等）
     * @return 若存在符合条件的匹配项返回 {@code true}，否则返回 {@code false}
     */
    public boolean contains(CharSequence text, AcScanOptions options) {
        Objects.requireNonNull(text, "text");
        if (text.length() == 0) {
            return false;
        }
        AcScanOptions opt = (options != null) ? options : AcScanOptions.ALL;
        if (isExact) {
            return ExactScanEngine.contains(text, data, keywords, transformConfig, opt.boundaryPolicy());
        } else {
            return TransformScanEngine.contains(text, data, keywords, transformConfig, opt.boundaryPolicy(), stats.maxKeywordCodePointLength());
        }
    }

    /**
     * 查找文本中出现的任意一个匹配项（以扫描过程最早检测到的为准）。
     *
     * @param text 待检测的目标文本
     * @return 首个检测到的匹配项；若无匹配则返回 {@code null}
     */
    public AcMatch<T> findAny(CharSequence text) {
        return findAny(text, AcScanOptions.ALL);
    }

    /**
     * 按照指定扫描选项，查找文本中出现的任意一个匹配项。
     *
     * @param text    待检测的目标文本
     * @param options 扫描选项
     * @return 首个检测到的匹配项；若无匹配则返回 {@code null}
     */
    public AcMatch<T> findAny(CharSequence text, AcScanOptions options) {
        Objects.requireNonNull(text, "text");
        if (text.length() == 0) {
            return null;
        }
        AcScanOptions opt = (options != null) ? options : AcScanOptions.ALL;
        if (isExact) {
            return ExactScanEngine.findAny(text, data, keywords, transformConfig, opt.boundaryPolicy());
        } else {
            return TransformScanEngine.findAny(text, data, keywords, transformConfig, opt.boundaryPolicy(), stats.maxKeywordCodePointLength());
        }
    }

    /**
     * 查找按文本起始位置排序（{@code startUtf16 ASC -> lengthUtf16 DESC -> keywordId ASC}）的最前匹配项。
     *
     * @param text 待检测的目标文本
     * @return 最左优先的第一个匹配项；若无匹配则返回 {@code null}
     */
    public AcMatch<T> findFirst(CharSequence text) {
        return findFirst(text, AcScanOptions.ALL);
    }

    /**
     * 按照指定选项查找最左优先的第一个匹配项。
     *
     * @param text    待检测的目标文本
     * @param options 扫描选项
     * @return 最左优先的第一个匹配项；若无匹配则返回 {@code null}
     */
    public AcMatch<T> findFirst(CharSequence text, AcScanOptions options) {
        Objects.requireNonNull(text, "text");
        if (text.length() == 0) {
            return null;
        }
        AcScanOptions opt = (options != null) ? options : AcScanOptions.ALL;
        AcScanOptions allOptions = opt.toBuilder()
                .overlapPolicy(OverlapPolicy.ALL)
                .emissionOrder(EmissionOrder.START_ASC_LENGTH_DESC_ID_ASC)
                .build();

        List<AcMatch<T>> all = findAll(text, allOptions);
        if (all.isEmpty()) {
            return null;
        }
        return all.get(0);
    }

    /**
     * 扫描全文并返回所有匹配项列表（默认按 {@code START_ASC_LENGTH_DESC_ID_ASC} 排序）。
     *
     * @param text 待检测的目标文本
     * @return 包含所有命中的匹配列表，不会为 {@code null}
     */
    public List<AcMatch<T>> findAll(CharSequence text) {
        return findAll(text, AcScanOptions.ALL);
    }

    /**
     * 按照指定的重叠策略与边界选项，扫描全文并返回结果列表。
     *
     * @param text    待检测的目标文本
     * @param options 扫描选项（如重叠策略 ALL / LEFTMOST_LONGEST，限制匹配上限等）
     * @return 匹配结果列表
     */
    public List<AcMatch<T>> findAll(CharSequence text, AcScanOptions options) {
        Objects.requireNonNull(text, "text");
        if (text.length() == 0) {
            return Collections.emptyList();
        }
        AcScanOptions opt = (options != null) ? options : AcScanOptions.ALL;
        List<AcMatch<T>> matches = new ArrayList<>();
        scan(text, opt, (start, end, keywordId, payload) -> {
            matches.add(new AcMatch<>(start, end, keywordId, payload));
            return MatchDecision.CONTINUE;
        });

        if (opt.emissionOrder() == EmissionOrder.START_ASC_LENGTH_DESC_ID_ASC || opt == AcScanOptions.ALL) {
            matches.sort((a, b) -> {
                int sc = Integer.compare(a.startUtf16(), b.startUtf16());
                if (sc != 0) return sc;
                int lc = Integer.compare(b.lengthUtf16(), a.lengthUtf16()); // length DESC
                if (lc != 0) return lc;
                return Integer.compare(a.keywordId(), b.keywordId()); // id ASC
            });
        }
        return matches;
    }

    /**
     * 低开销回调式扫描。
     *
     * <p>匹配项通过原始类型入参回调传递给 {@code consumer}，避免在扫描循环中为每个命中创建包装对象。
     *
     * @param text     待检测的目标文本
     * @param options  扫描选项
     * @param consumer 命中结果回调接口，可返回 {@link MatchDecision#STOP} 提前终止扫描
     * @return 实际向 consumer 发送的匹配项总数
     */
    public int scan(
            CharSequence text,
            AcScanOptions options,
            AcMatchConsumer<? super T> consumer) {
        return scan(text, options, null, consumer);
    }

    /**
     * 支持上下文复用的回调式扫描。
     *
     * @param text     待检测的目标文本
     * @param options  扫描选项
     * @param context  扫描上下文（可重用内部缓冲区，进一步消除变换模式下的临时内存）
     * @param consumer 命中结果回调接口
     * @return 实际发送的匹配项总数
     */
    public int scan(
            CharSequence text,
            AcScanOptions options,
            AcScanContext context,
            AcMatchConsumer<? super T> consumer) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(consumer, "consumer");
        if (text.length() == 0) {
            return 0;
        }
        AcScanOptions opt = (options != null) ? options : AcScanOptions.ALL;
        if (isExact) {
            return ExactScanEngine.scan(text, data, keywords, transformConfig, opt, consumer);
        } else {
            return TransformScanEngine.scan(text, data, keywords, transformConfig, opt, context, consumer, stats.maxKeywordCodePointLength());
        }
    }

    /**
     * 将原文按最左优先最长（{@link OverlapPolicy#LEFTMOST_LONGEST}）规则无缝切割为连续切片列表。
     *
     * <p>切片包含 {@link AcToken.Type#MATCH}（匹配项）与 {@link AcToken.Type#FRAGMENT}（未命中原文），
     * 连续覆盖整个文本区间 {@code [0, text.length())}，切片仅记录偏移量，不执行字符串拷贝，
     * 专用于富文本高亮渲染与敏感词打码场景。
     *
     * @param text    待切分的目标文本
     * @param options 扫描选项（重叠策略必须为 {@link OverlapPolicy#LEFTMOST_LONGEST}）
     * @return 覆盖整个输入文本的连续 Token 列表
     * @throws IllegalArgumentException 若 options 中的重叠策略非 LEFTMOST_LONGEST
     */
    public List<AcToken<T>> tokenize(
            CharSequence text,
            AcScanOptions options) {
        Objects.requireNonNull(text, "text");
        if (options != null && options.overlapPolicy() != OverlapPolicy.LEFTMOST_LONGEST) {
            throw new IllegalArgumentException("tokenize requires OverlapPolicy.LEFTMOST_LONGEST, got: " + options.overlapPolicy());
        }

        int len = text.length();
        if (len == 0) {
            return Collections.emptyList();
        }

        AcScanOptions opt = (options != null) ? options : AcScanOptions.LEFTMOST_LONGEST;
        List<AcMatch<T>> matches = new ArrayList<>();
        scan(text, opt, (start, end, keywordId, payload) -> {
            matches.add(new AcMatch<>(start, end, keywordId, payload));
            return MatchDecision.CONTINUE;
        });

        List<AcToken<T>> tokens = new ArrayList<>();
        int lastPos = 0;

        for (AcMatch<T> match : matches) {
            if (match.startUtf16() > lastPos) {
                tokens.add(AcToken.fragment(lastPos, match.startUtf16()));
            }
            tokens.add(AcToken.match(match.startUtf16(), match.endUtf16(), match.keywordId(), match.payload()));
            lastPos = match.endUtf16();
        }

        if (lastPos < len) {
            tokens.add(AcToken.fragment(lastPos, len));
        }

        return tokens;
    }

    /**
     * 根据关键词 ID 查询对应的原始元数据。
     *
     * @param keywordId 关键词 ID
     * @return 关键词元数据对象；若不存在则返回 {@code null}
     */
    public AcKeyword<T> keywordById(int keywordId) {
        return keywords.findById(keywordId);
    }

    /**
     * 获取自动机构建与运行时状态统计指标。
     *
     * @return 统计信息快照
     */
    public AcStats stats() {
        return stats;
    }

    /**
     * 获取底层平铺状态机数组（用于序列化与底层测试）。
     */
    public PackedAutomatonData data() {
        return data;
    }

    /**
     * 获取关键词元数据内部表（用于序列化）。
     */
    public KeywordTable<T> keywords() {
        return keywords;
    }

    /**
     * 获取当前自动机的文本变换规则配置。
     */
    public TextTransformConfig transformConfig() {
        return transformConfig;
    }
}

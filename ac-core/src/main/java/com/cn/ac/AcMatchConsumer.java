package com.cn.ac;

/**
 * 低开销匹配结果回调函数接口。
 *
 * <p>用于在 {@link AcAutomaton#scan(CharSequence, AcScanOptions, AcMatchConsumer)} 扫描执行期间，
 * 直接通过原始类型入参传递命中结果，完全杜绝堆对象创建。
 *
 * @param <T> 关键词关联的自定义 Payload 类型
 */
@FunctionalInterface
public interface AcMatchConsumer<T> {

    /**
     * 当检测到一次关键词匹配时被调用。
     *
     * @param startUtf16 命中词在原文中的 UTF-16 起始偏移（包含）
     * @param endUtf16   命中词在原文中的 UTF-16 结束偏移（不包含）
     * @param keywordId  命中的关键词唯一 ID
     * @param payload    关键词关联的自定义 Payload 数据
     * @return {@link MatchDecision#CONTINUE} 继续扫描，或 {@link MatchDecision#STOP} 立即终止扫描
     */
    MatchDecision onMatch(
            int startUtf16,
            int endUtf16,
            int keywordId,
            T payload);
}

package com.cn.ac.stream;

import com.cn.ac.MatchDecision;

/**
 * 流式大文本匹配结果回调接口。
 *
 * <p>偏移量采用 64 位整型（{@code long startUtf16}, {@code long endUtf16}），
 * 适用于跨分块、跨 GB 级流式超大日志或文件的持续匹配场景。
 *
 * @param <T> 关键词自定义 Payload 类型
 */
@FunctionalInterface
public interface AcLongMatchConsumer<T> {

    /**
     * 流式检测到命中时回调。
     *
     * @param startUtf16 全局流式 UTF-16 起始偏移（包含）
     * @param endUtf16   全局流式 UTF-16 结束偏移（不包含）
     * @param keywordId  命中的关键词 ID
     * @param payload    关键词关联的自定义 Payload
     * @return 匹配决策，返回 STOP 可提前终止流处理
     */
    MatchDecision onMatch(
            long startUtf16,
            long endUtf16,
            int keywordId,
            T payload);
}

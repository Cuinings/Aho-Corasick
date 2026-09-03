package com.cn.ac;

/**
 * 匹配词边界校验策略接口。
 *
 * <p>在自动机检测到关键词候选后，通过此接口校验该匹配项在原文左右边界上的上下文合法性
 * （例如：要求关键词必须作为独立单词出现，避免 "apple" 在 "pineapple" 中被误判）。
 */
public interface BoundaryPolicy {

    /**
     * 校验匹配候选区间是否满足边界要求。
     *
     * @param originalText 原始输入文本
     * @param startUtf16   匹配候选起始偏移（包含）
     * @param endUtf16     匹配候选结束偏移（不包含）
     * @return 若边界校验通过返回 {@code true}，否则返回 {@code false} 将该候选过滤
     */
    boolean isValid(CharSequence originalText, int startUtf16, int endUtf16);
}

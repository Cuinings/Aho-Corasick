package com.cn.ac;

import java.util.Objects;

/**
 * 匹配命中的结果模型。
 *
 * <p>区间均严格遵循 Java/UTF-16 标准的左闭右开半开区间 {@code [startUtf16, endUtf16)}，
 * 业务侧可直接使用 {@code text.subSequence(match.startUtf16(), match.endUtf16())} 提取命中词汇。
 *
 * @param <T> 关联在关键词上的自定义 Payload 类型
 */
public final class AcMatch<T> {
    private final int startUtf16;
    private final int endUtf16;
    private final int keywordId;
    private final T payload;

    /**
     * 构造匹配项。
     *
     * @param startUtf16 原始输入字符串中的 UTF-16 起始索引（包含）
     * @param endUtf16   原始输入字符串中的 UTF-16 结束索引（不包含）
     * @param keywordId  命中的关键词唯一标识 ID
     * @param payload    命中的自定义 Payload 数据（可为 null）
     */
    public AcMatch(int startUtf16, int endUtf16, int keywordId, T payload) {
        if (startUtf16 < 0 || endUtf16 < startUtf16) {
            throw new IllegalArgumentException("Invalid offsets: [" + startUtf16 + ", " + endUtf16 + ")");
        }
        this.startUtf16 = startUtf16;
        this.endUtf16 = endUtf16;
        this.keywordId = keywordId;
        this.payload = payload;
    }

    /** 命中词在原文中的起始偏移（UTF-16 代码单元，包含） */
    public int startUtf16() {
        return startUtf16;
    }

    /** 命中词在原文中的结束偏移（UTF-16 代码单元，不包含） */
    public int endUtf16() {
        return endUtf16;
    }

    /** 命中词在原文中的 UTF-16 字符长度 */
    public int lengthUtf16() {
        return endUtf16 - startUtf16;
    }

    /** 命中的关键词唯一 ID */
    public int keywordId() {
        return keywordId;
    }

    /** 命中的关键词附加自定义 Payload */
    public T payload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AcMatch<?> acMatch = (AcMatch<?>) o;
        return startUtf16 == acMatch.startUtf16 &&
                endUtf16 == acMatch.endUtf16 &&
                keywordId == acMatch.keywordId &&
                Objects.equals(payload, acMatch.payload);
    }

    @Override
    public int hashCode() {
        int result = startUtf16;
        result = 31 * result + endUtf16;
        result = 31 * result + keywordId;
        result = 31 * result + (payload != null ? payload.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AcMatch{[" + startUtf16 + ", " + endUtf16 + "), id=" + keywordId + ", payload=" + payload + "}";
    }
}

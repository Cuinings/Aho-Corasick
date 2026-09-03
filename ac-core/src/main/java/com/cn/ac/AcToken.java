package com.cn.ac;

import java.util.Objects;

/**
 * 原文连续无损切片 Token。
 *
 * <p>由 {@link AcAutomaton#tokenize(CharSequence, AcScanOptions)} 生成，将整篇文本完整切分为：
 * <ul>
 *   <li>{@link Type#MATCH}：命中的敏感词或关键词片段，附带 keywordId 与 payload。</li>
 *   <li>{@link Type#FRAGMENT}：未命中关键词的普通文本碎片。</li>
 * </ul>
 * 所有切片无隙紧密拼接即为原始文本（{@code token[i].end == token[i+1].start}），不执行任何字符串拷贝。
 *
 * @param <T> 关键词关联的自定义 Payload 类型
 */
public final class AcToken<T> {

    /** 切片类型 */
    public enum Type {
        /** 关键词命中切片 */
        MATCH,
        /** 普通原文切片（未命中） */
        FRAGMENT
    }

    private final Type type;
    private final int startUtf16;
    private final int endUtf16;
    private final int keywordId;
    private final T payload;

    /** 创建关键词命中切片 */
    public static <T> AcToken<T> match(int startUtf16, int endUtf16, int keywordId, T payload) {
        return new AcToken<>(Type.MATCH, startUtf16, endUtf16, keywordId, payload);
    }

    /** 创建普通原文切片 */
    public static <T> AcToken<T> fragment(int startUtf16, int endUtf16) {
        return new AcToken<>(Type.FRAGMENT, startUtf16, endUtf16, -1, null);
    }

    public AcToken(Type type, int startUtf16, int endUtf16, int keywordId, T payload) {
        if (startUtf16 < 0 || endUtf16 < startUtf16) {
            throw new IllegalArgumentException("Invalid token offsets: [" + startUtf16 + ", " + endUtf16 + ")");
        }
        this.type = Objects.requireNonNull(type, "type");
        this.startUtf16 = startUtf16;
        this.endUtf16 = endUtf16;
        this.keywordId = keywordId;
        this.payload = payload;
    }

    /** 获取切片类型（MATCH 或 FRAGMENT） */
    public Type type() {
        return type;
    }

    /** 获取在原始文本中的 UTF-16 起始偏移（包含） */
    public int startUtf16() {
        return startUtf16;
    }

    /** 获取在原始文本中的 UTF-16 结束偏移（不包含） */
    public int endUtf16() {
        return endUtf16;
    }

    /** 获取切片的 UTF-16 字符跨度长度 */
    public int lengthUtf16() {
        return endUtf16 - startUtf16;
    }

    /** 若为 MATCH 类型，返回关键词 ID；否则返回 -1 */
    public int keywordId() {
        return keywordId;
    }

    /** 若为 MATCH 类型，返回关联 Payload；否则返回 null */
    public T payload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AcToken<?> acToken = (AcToken<?>) o;
        return startUtf16 == acToken.startUtf16 &&
                endUtf16 == acToken.endUtf16 &&
                keywordId == acToken.keywordId &&
                type == acToken.type &&
                Objects.equals(payload, acToken.payload);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + startUtf16;
        result = 31 * result + endUtf16;
        result = 31 * result + keywordId;
        result = 31 * result + (payload != null ? payload.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AcToken{" + type + ", [" + startUtf16 + ", " + endUtf16 + "), id=" + keywordId + ", payload=" + payload + "}";
    }
}

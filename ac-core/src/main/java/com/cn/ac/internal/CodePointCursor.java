package com.cn.ac.internal;

import com.cn.ac.InvalidSurrogatePolicy;
import com.cn.ac.exception.InvalidUnicodeException;

/**
 * 零内存分配的 Unicode 码点（Code Point）游标解析器。
 *
 * <p>本类负责从 UTF-16 文本（{@link CharSequence}）中无内存分配地提取下一个 32 位 Unicode Code Point，
 * 并正确处理辅助平面（Supplementary Characters，如 Emoji、罕见汉字）的双 char 代理对（Surrogate Pair）。
 *
 * <h3>返回值打包规范：</h3>
 * 返回一个 64 位基本类型 {@code long}，通过位移拆解两个关键信息：
 * <ul>
 *   <li>高 32 位（{@code result >>> 32}）：扫描步进后的下一个 UTF-16 偏移量 {@code nextOffset}。</li>
 *   <li>低 32 位（{@code (int) result}）：解码出的 32 位 Unicode 码点值 {@code codePoint}。</li>
 * </ul>
 */
public final class CodePointCursor {

    /** 替换字符 U+FFFD */
    public static final int REPLACEMENT_CHARACTER = 0xFFFD;

    /**
     * 从 {@code text} 的 {@code offset} 位置解析下一个 Code Point。
     *
     * @param text   待解析的字符序列
     * @param offset 当前 UTF-16 起始位置
     * @param length 字符序列总长度
     * @param policy 孤立代理字符（Lone Surrogate）的处理策略（REJECT 或 REPLACE）
     * @return 打包好的 64 位 long，高 32 位为 nextOffset，低 32 位为 codePoint
     * @throws InvalidUnicodeException 若遇到孤立代理对且策略为 {@link InvalidSurrogatePolicy#REJECT}
     */
    public static long nextCodePoint(CharSequence text, int offset, int length, InvalidSurrogatePolicy policy) {
        char c1 = text.charAt(offset);
        if (Character.isHighSurrogate(c1)) {
            if (offset + 1 < length) {
                char c2 = text.charAt(offset + 1);
                if (Character.isLowSurrogate(c2)) {
                    int cp = Character.toCodePoint(c1, c2);
                    return (((long) (offset + 2)) << 32) | (cp & 0xFFFFFFFFL);
                }
            }
            // 孤立高代理字符
            if (policy == InvalidSurrogatePolicy.REJECT) {
                throw new InvalidUnicodeException(offset, "Unpaired high surrogate at offset " + offset);
            }
            return (((long) (offset + 1)) << 32) | (REPLACEMENT_CHARACTER & 0xFFFFFFFFL);
        } else if (Character.isLowSurrogate(c1)) {
            // 孤立低代理字符
            if (policy == InvalidSurrogatePolicy.REJECT) {
                throw new InvalidUnicodeException(offset, "Unpaired low surrogate at offset " + offset);
            }
            return (((long) (offset + 1)) << 32) | (REPLACEMENT_CHARACTER & 0xFFFFFFFFL);
        } else {
            // 普通 BMP 字符（单 char 表达）
            return (((long) (offset + 1)) << 32) | (((int) c1) & 0xFFFFFFFFL);
        }
    }

    private CodePointCursor() {}
}

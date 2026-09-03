package com.cn.ac;

import com.cn.ac.internal.BoundaryChecker;

/**
 * 常用标准边界策略常量集合。
 */
public final class Boundaries {

    /** 不进行任何边界过滤（默认策略，子串匹配即命中） */
    public static final BoundaryPolicy NONE = (originalText, startUtf16, endUtf16) -> true;

    /** ASCII 单词边界（首尾两侧不得为 ASCII 字母、数字或下划线 `[a-zA-Z0-9_]`） */
    public static final BoundaryPolicy ASCII_WORD = BoundaryChecker::isAsciiWordBoundary;

    /** Unicode 字母/数字边界（首尾两侧不得为 Unicode 字母或数字） */
    public static final BoundaryPolicy UNICODE_ALNUM = BoundaryChecker::isUnicodeAlnumBoundary;

    /** 空白字符边界（首尾两侧必须紧邻空白符或处于文本首尾边界） */
    public static final BoundaryPolicy WHITESPACE = BoundaryChecker::isWhitespaceBoundary;

    /** UAX#29 标准词边界（遵循 Unicode 文本分词标准） */
    public static final BoundaryPolicy UAX29_WORD = BoundaryChecker::isUax29WordBoundary;

    private Boundaries() {}
}

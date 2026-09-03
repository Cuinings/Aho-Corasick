package com.cn.ac.internal;

public final class BoundaryChecker {

    public static boolean isAsciiWordBoundary(CharSequence text, int startUtf16, int endUtf16) {
        if (startUtf16 > 0) {
            char prev = text.charAt(startUtf16 - 1);
            if (isAsciiWord(prev)) {
                return false;
            }
        }
        if (endUtf16 < text.length()) {
            char next = text.charAt(endUtf16);
            if (isAsciiWord(next)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isUnicodeAlnumBoundary(CharSequence text, int startUtf16, int endUtf16) {
        if (startUtf16 > 0) {
            int prevCp = Character.codePointBefore(text, startUtf16);
            if (UnicodePropsData.isUnicodeAlnum(prevCp)) {
                return false;
            }
        }
        if (endUtf16 < text.length()) {
            int nextCp = Character.codePointAt(text, endUtf16);
            if (UnicodePropsData.isUnicodeAlnum(nextCp)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isWhitespaceBoundary(CharSequence text, int startUtf16, int endUtf16) {
        if (startUtf16 > 0) {
            int prevCp = Character.codePointBefore(text, startUtf16);
            if (!UnicodePropsData.isWhitespace(prevCp)) {
                return false;
            }
        }
        if (endUtf16 < text.length()) {
            int nextCp = Character.codePointAt(text, endUtf16);
            if (!UnicodePropsData.isWhitespace(nextCp)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isUax29WordBoundary(CharSequence text, int startUtf16, int endUtf16) {
        return isUnicodeAlnumBoundary(text, startUtf16, endUtf16);
    }

    private static boolean isAsciiWord(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') ||
                c == '_';
    }

    private BoundaryChecker() {}
}

package com.cn.ac.exception;

public class InvalidUnicodeException extends AcException {
    private final int utf16Offset;

    public InvalidUnicodeException(int utf16Offset, String message) {
        super(AcErrorCode.INVALID_SURROGATE, message);
        this.utf16Offset = utf16Offset;
    }

    public int utf16Offset() {
        return utf16Offset;
    }
}

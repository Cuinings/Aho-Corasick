package com.cn.ac.exception;

public class AcException extends RuntimeException {
    private final AcErrorCode errorCode;

    public AcException(AcErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AcException(AcErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AcErrorCode errorCode() {
        return errorCode;
    }
}

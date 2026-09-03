package com.cn.ac.exception;

public class CorruptAutomatonException extends AcException {
    public CorruptAutomatonException(AcErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public CorruptAutomatonException(AcErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

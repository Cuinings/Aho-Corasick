package com.cn.ac.exception;

public class AcBuildException extends AcException {
    public AcBuildException(AcErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AcBuildException(AcErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

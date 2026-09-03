package com.cn.ac.exception;

public class AcLimitExceededException extends AcBuildException {
    public AcLimitExceededException(AcErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

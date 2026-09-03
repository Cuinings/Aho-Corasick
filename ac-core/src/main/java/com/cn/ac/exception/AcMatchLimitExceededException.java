package com.cn.ac.exception;

public class AcMatchLimitExceededException extends AcException {
    private final int limit;

    public AcMatchLimitExceededException(int limit, String message) {
        super(AcErrorCode.MAX_OUTPUTS_EXCEEDED, message);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}

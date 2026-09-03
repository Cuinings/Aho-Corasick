package com.cn.ac.exception;

public class DuplicateKeywordException extends AcBuildException {
    private final int keywordId;
    private final String keyword;

    public DuplicateKeywordException(AcErrorCode errorCode, int keywordId, String keyword, String message) {
        super(errorCode, message);
        this.keywordId = keywordId;
        this.keyword = keyword;
    }

    public int keywordId() {
        return keywordId;
    }

    public String keyword() {
        return keyword;
    }
}

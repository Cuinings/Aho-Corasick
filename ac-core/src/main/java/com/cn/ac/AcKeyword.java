package com.cn.ac;

import java.util.Objects;

public final class AcKeyword<T> {
    private final int keywordId;
    private final String originalText;
    private final T payload;

    public AcKeyword(int keywordId, String originalText, T payload) {
        if (keywordId < 0) {
            throw new IllegalArgumentException("keywordId must be non-negative: " + keywordId);
        }
        this.keywordId = keywordId;
        this.originalText = originalText;
        this.payload = payload;
    }

    public int keywordId() {
        return keywordId;
    }

    public String originalText() {
        return originalText;
    }

    public T payload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AcKeyword<?> that = (AcKeyword<?>) o;
        return keywordId == that.keywordId &&
                Objects.equals(originalText, that.originalText) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = keywordId;
        result = 31 * result + (originalText != null ? originalText.hashCode() : 0);
        result = 31 * result + (payload != null ? payload.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AcKeyword{" +
                "id=" + keywordId +
                ", text='" + originalText + '\'' +
                ", payload=" + payload +
                '}';
    }
}

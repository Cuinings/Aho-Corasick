package com.cn.ac.internal;

import com.cn.ac.AcKeyword;

import java.util.Arrays;

public final class KeywordTable<T> {
    public final int[] keywordIdBySlot;
    public final int[] keywordLengthCodePoint;
    public final int[] keywordLengthUtf16Exact;
    public final int[] keywordIdSorted;
    public final int[] keywordSlotBySortedId;
    public final Object[] payloadBySlot;
    public final String[] originalKeywordBySlot;

    public KeywordTable(int[] keywordIdBySlot, int[] keywordLengthCodePoint,
                        int[] keywordLengthUtf16Exact, int[] keywordIdSorted,
                        int[] keywordSlotBySortedId, Object[] payloadBySlot,
                        String[] originalKeywordBySlot) {
        this.keywordIdBySlot = keywordIdBySlot;
        this.keywordLengthCodePoint = keywordLengthCodePoint;
        this.keywordLengthUtf16Exact = keywordLengthUtf16Exact;
        this.keywordIdSorted = keywordIdSorted;
        this.keywordSlotBySortedId = keywordSlotBySortedId;
        this.payloadBySlot = payloadBySlot;
        this.originalKeywordBySlot = originalKeywordBySlot;
    }

    public int slotCount() {
        return keywordIdBySlot.length;
    }

    @SuppressWarnings("unchecked")
    public T payload(int slot) {
        return (T) payloadBySlot[slot];
    }

    public int keywordId(int slot) {
        return keywordIdBySlot[slot];
    }

    public int lengthCodePoint(int slot) {
        return keywordLengthCodePoint[slot];
    }

    public int lengthUtf16Exact(int slot) {
        return keywordLengthUtf16Exact != null ? keywordLengthUtf16Exact[slot] : -1;
    }

    public String originalText(int slot) {
        return originalKeywordBySlot != null ? originalKeywordBySlot[slot] : null;
    }

    @SuppressWarnings("unchecked")
    public AcKeyword<T> findById(int keywordId) {
        int idx = Arrays.binarySearch(keywordIdSorted, keywordId);
        if (idx < 0) {
            return null;
        }
        int slot = keywordSlotBySortedId[idx];
        String text = originalKeywordBySlot != null ? originalKeywordBySlot[slot] : null;
        return new AcKeyword<>(keywordId, text, (T) payloadBySlot[slot]);
    }

    public int slotById(int keywordId) {
        int idx = Arrays.binarySearch(keywordIdSorted, keywordId);
        return idx >= 0 ? keywordSlotBySortedId[idx] : -1;
    }
}

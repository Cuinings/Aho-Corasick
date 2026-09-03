package com.cn.ac.internal;

import com.cn.ac.exception.AcErrorCode;
import com.cn.ac.exception.CorruptAutomatonException;

public final class AutomatonValidator {

    public static void validate(PackedAutomatonData data, int stateCount,
                                int[] stateDepth, KeywordTable<?> keywords) {
        if (data.failure.length != stateCount) {
            throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "failure array length mismatch");
        }
        if (data.failure[0] != 0) {
            throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "failure[0] must be 0");
        }

        for (int s = 1; s < stateCount; s++) {
            int f = data.failure[s];
            if (f < 0 || f >= stateCount) {
                throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "failure[" + s + "] out of bounds: " + f);
            }
            if (stateDepth != null && stateDepth[f] >= stateDepth[s]) {
                throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON,
                        "failure depth invariant violated: depth(f)=" + stateDepth[f] + " >= depth(s)=" + stateDepth[s]);
            }
        }

        for (int s = 0; s < stateCount; s++) {
            int first = data.firstEdge[s];
            int count = data.edgeCount[s];
            if (first < 0 || count < 0 || first + count > data.edgeCodePoint.length) {
                throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "Invalid edge range for state " + s);
            }
            for (int i = 0; i < count; i++) {
                int target = data.edgeTarget[first + i];
                if (target < 0 || target >= stateCount) {
                    throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "Edge target out of bounds: " + target);
                }
                if (i > 0) {
                    int prevCp = data.edgeCodePoint[first + i - 1];
                    int currCp = data.edgeCodePoint[first + i];
                    if (currCp <= prevCp) {
                        throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON,
                                "Edges not strictly sorted in state " + s + ": prev=" + prevCp + ", curr=" + currCp);
                    }
                }
            }

            int outLink = data.outputLink[s];
            if (outLink != -1 && (outLink < 0 || outLink >= stateCount)) {
                throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "outputLink out of bounds: " + outLink);
            }
        }

        int slotCount = keywords.slotCount();
        for (int s = 0; s < stateCount; s++) {
            int start = data.ownOutputStart[s];
            int count = data.ownOutputCount[s];
            if (count > 0) {
                if (start < 0 || start + count > data.ownOutputKeywordSlot.length) {
                    throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "ownOutput out of bounds in state " + s);
                }
                for (int i = 0; i < count; i++) {
                    int slot = data.ownOutputKeywordSlot[start + i];
                    if (slot < 0 || slot >= slotCount) {
                        throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "Output slot out of bounds: " + slot);
                    }
                }
            }
        }

        for (int i = 1; i < keywords.keywordIdSorted.length; i++) {
            if (keywords.keywordIdSorted[i] <= keywords.keywordIdSorted[i - 1]) {
                throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "keywordIdSorted not strictly increasing");
            }
        }
    }

    private AutomatonValidator() {}
}

package com.cn.ac.stream;

import com.cn.ac.*;
import com.cn.ac.internal.CodePointCursor;
import com.cn.ac.internal.PackedAutomatonData;
import com.cn.ac.internal.KeywordTable;

/**
 * 分块流式文本匹配会话接口。
 *
 * <p>用于处理无法一次性装入内存的大文件、网络数据流或长轮询文本分块。
 * 会话跨分块（chunk）维护内部状态机状态（state）与全局字符偏移计数（globalUtf16Offset），
 * 原生支持跨分块边界关键词的无缝识别。
 *
 * @param <T> 关键词自定义 Payload 类型
 */
public interface AcStreamingSession<T> {

    /**
     * 接收并处理一段流式文本块。
     *
     * @param chunk    当前数据块字符序列
     * @param consumer 命中回调接口
     * @return 当前分块内触发的匹配项数量
     */
    int accept(CharSequence chunk, AcLongMatchConsumer<? super T> consumer);

    /**
     * 结束当前流式会话。
     *
     * @param consumer 命中回调接口
     * @return 最终触发的匹配项数量
     */
    int finish(AcLongMatchConsumer<? super T> consumer);

    /**
     * 重置会话状态（状态回归根节点 0，全局偏移清零），以便复用当前会话。
     */
    void reset();

    /**
     * 创建基于指定自动机与选项的流式会话。
     */
    static <T> AcStreamingSession<T> create(AcAutomaton<T> automaton, AcScanOptions options) {
        return new DefaultStreamingSession<>(automaton, options != null ? options : AcScanOptions.ALL);
    }

    class DefaultStreamingSession<T> implements AcStreamingSession<T> {
        private final AcAutomaton<T> automaton;
        private final AcScanOptions options;
        private int state;
        private long globalUtf16Offset;
        private boolean finished;

        public DefaultStreamingSession(AcAutomaton<T> automaton, AcScanOptions options) {
            this.automaton = automaton;
            this.options = options;
            this.state = 0;
            this.globalUtf16Offset = 0;
            this.finished = false;
        }

        @Override
        public int accept(CharSequence chunk, AcLongMatchConsumer<? super T> consumer) {
            if (finished) {
                throw new IllegalStateException("Streaming session has already finished. Call reset() before accepting new chunks.");
            }
            if (chunk == null || chunk.length() == 0) {
                return 0;
            }

            int emitted = 0;
            int len = chunk.length();
            int offset = 0;
            long baseOffset = globalUtf16Offset;

            while (offset < len) {
                long packed = CodePointCursor.nextCodePoint(chunk, offset, len, InvalidSurrogatePolicy.REPLACE);
                int nextOffset = (int) (packed >>> 32);
                int cp = (int) packed;

                emitted += processCodePoint(cp, baseOffset + nextOffset, consumer);
                offset = nextOffset;
            }

            globalUtf16Offset += len;
            return emitted;
        }

        private int processCodePoint(int cp, long endOffset, AcLongMatchConsumer<? super T> consumer) {
            PackedAutomatonData data = automaton.data();
            KeywordTable<T> keywords = automaton.keywords();

            while (state != 0 && data.transition(state, cp) == -1) {
                state = data.failure[state];
            }
            int next = data.transition(state, cp);
            state = (next != -1) ? next : 0;

            int emitted = 0;
            if (data.ownOutputCount[state] > 0 || data.outputLink[state] != -1) {
                int startOut = data.ownOutputStart[state];
                int countOut = data.ownOutputCount[state];
                for (int i = 0; i < countOut; i++) {
                    int slot = data.ownOutputKeywordSlot[startOut + i];
                    int kwLen = keywords.lengthUtf16Exact(slot);
                    long start = endOffset - kwLen;
                    emitted++;
                    MatchDecision d = consumer.onMatch(start, endOffset, keywords.keywordId(slot), keywords.payload(slot));
                    if (d == MatchDecision.STOP) return emitted;
                }

                int outState = data.outputLink[state];
                while (outState != -1) {
                    int sOut = data.ownOutputStart[outState];
                    int cOut = data.ownOutputCount[outState];
                    for (int i = 0; i < cOut; i++) {
                        int slot = data.ownOutputKeywordSlot[sOut + i];
                        int kwLen = keywords.lengthUtf16Exact(slot);
                        long start = endOffset - kwLen;
                        emitted++;
                        MatchDecision d = consumer.onMatch(start, endOffset, keywords.keywordId(slot), keywords.payload(slot));
                        if (d == MatchDecision.STOP) return emitted;
                    }
                    outState = data.outputLink[outState];
                }
            }
            return emitted;
        }

        @Override
        public int finish(AcLongMatchConsumer<? super T> consumer) {
            finished = true;
            return 0;
        }

        @Override
        public void reset() {
            this.state = 0;
            this.globalUtf16Offset = 0;
            this.finished = false;
        }
    }
}

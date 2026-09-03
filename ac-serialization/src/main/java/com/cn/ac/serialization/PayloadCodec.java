package com.cn.ac.serialization;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 自定义关键词 Payload 序列化编解码器。
 *
 * <p>用于在将自动机持久化为 {@code ACAT} 格式二进制快照时，将用户自定义对象写入数据流或从数据流还原。
 *
 * @param <T> Payload 类型
 */
public interface PayloadCodec<T> {

    /**
     * 编解码器唯一标识（写入快照头部的 payloadCodecId，反序列化时比对校验）。
     */
    String codecId();

    /**
     * 将 Payload 实例编码写入输出流。
     *
     * @param payload 待序列化的对象
     * @param out     数据输出流
     * @throws IOException 写入 IO 异常
     */
    void encode(T payload, DataOutput out) throws IOException;

    /**
     * 从输入流中解码还原 Payload 实例。
     *
     * @param in 数据输入流
     * @return 还原出的 Payload 对象
     * @throws IOException 读取 IO 异常
     */
    T decode(DataInput in) throws IOException;
}

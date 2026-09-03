package com.cn.ac.android;

import android.content.Context;
import android.os.Looper;
import com.cn.ac.AcAutomaton;
import com.cn.ac.serialization.AcAutomatonSnapshot;
import com.cn.ac.serialization.PayloadCodec;

import java.io.*;

/**
 * Android 平台词库资产加载器。
 *
 * <p>提供从 Android {@code assets/} 目录、本地文件或输入流安全反序列化 {@code ACAT} 自动机快照的能力，
 * 并内置了 Android 主线程（UI 线程）防护检测，严防在主线程执行磁盘 IO 造成卡顿或 ANR。
 */
public final class AcAutomatonLoader {

    /**
     * 从 Android 应用 assets 目录加载预制快照文件。
     *
     * @param context   Android Context
     * @param assetPath assets 相对路径（如 "risk_words.acat"）
     * @param codec     自定义 Payload 解码器（无 Payload 可为 null）
     * @return 反序列化完成的自动机
     * @throws IOException           当读取或校验失败时
     * @throws IllegalStateException 若在 Android 主线程调用
     */
    public static <T> AcAutomaton<T> loadFromAsset(Context context, String assetPath, PayloadCodec<T> codec) throws IOException {
        checkNotMainThread();
        try (InputStream in = context.getAssets().open(assetPath)) {
            return loadFromStream(in, codec);
        }
    }

    /**
     * 从本地文件加载快照。
     *
     * @param file  本地快照文件
     * @param codec 自定义 Payload 解码器
     * @return 反序列化完成的自动机
     * @throws IOException           当读取或校验失败时
     * @throws IllegalStateException 若在 Android 主线程调用
     */
    public static <T> AcAutomaton<T> loadFromFile(File file, PayloadCodec<T> codec) throws IOException {
        checkNotMainThread();
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return AcAutomatonSnapshot.load(in, codec);
        }
    }

    /**
     * 从通用输入流中加载快照。
     *
     * @param in    输入流
     * @param codec 自定义 Payload 解码器
     * @return 反序列化完成的自动机
     * @throws IOException           当读取或校验失败时
     * @throws IllegalStateException 若在 Android 主线程调用
     */
    public static <T> AcAutomaton<T> loadFromStream(InputStream in, PayloadCodec<T> codec) throws IOException {
        checkNotMainThread();
        BufferedInputStream bis = (in instanceof BufferedInputStream) ? (BufferedInputStream) in : new BufferedInputStream(in);
        return AcAutomatonSnapshot.load(bis, codec);
    }

    /**
     * 校验当前执行线程非 Android 主线程（UI 线程）。
     *
     * @throws IllegalStateException 若当前处于 Android UI 主线程
     */
    public static void checkNotMainThread() {
        try {
            if (Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper()) {
                throw new IllegalStateException("AcAutomatonLoader must not be invoked on the Android main thread (UI thread).");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable ignored) {
            // 在未 Mock Android OS 的非真机纯单元测试环境下忽略
        }
    }

    private AcAutomatonLoader() {}
}

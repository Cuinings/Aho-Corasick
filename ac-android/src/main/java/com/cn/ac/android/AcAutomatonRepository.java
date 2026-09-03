package com.cn.ac.android;

import com.cn.ac.AcAutomaton;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Android 平台自动机动态快照持有仓库。
 *
 * <p>基于 {@link AtomicReference} 实现，用于应对客户端敏感词库的动态在线更新：
 * <ul>
 *   <li>后台工作线程完成新版本词库的拉取、构建或反序列化后，调用 {@link #replace(AcAutomaton)} 实现纳秒级原子无缝切换。</li>
 *   <li>主线程或扫描线程通过 {@link #current()} 永远获取到一致的、不可变的最新状态机，无需任何读写锁竞争。</li>
 * </ul>
 *
 * @param <T> 关键词自定义 Payload 类型
 */
public final class AcAutomatonRepository<T> {
    private final AtomicReference<AcAutomaton<T>> current;

    /**
     * 构造持有仓库，并注入初始自动机实例。
     *
     * @param initial 初始自动机（不得为 null）
     */
    public AcAutomatonRepository(AcAutomaton<T> initial) {
        this.current = new AtomicReference<>(Objects.requireNonNull(initial, "initial automaton"));
    }

    /**
     * 获取当前处于激活状态的不可变自动机。
     *
     * @return 当前自动机引用，保证非空
     */
    public AcAutomaton<T> current() {
        return current.get();
    }

    /**
     * 原子替换当前自动机为最新快照。
     *
     * @param next 新版本的自动机（不得为 null）
     */
    public void replace(AcAutomaton<T> next) {
        current.set(Objects.requireNonNull(next, "next automaton"));
    }

    /**
     * 条件原子更新。
     */
    public boolean compareAndSet(AcAutomaton<T> expect, AcAutomaton<T> update) {
        return current.compareAndSet(expect, Objects.requireNonNull(update, "update automaton"));
    }
}

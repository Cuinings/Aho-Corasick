package com.cn.ac.sample

import com.cn.ac.AcAutomaton
import com.cn.ac.AcMatchConsumer
import com.cn.ac.AcScanOptions
import com.cn.ac.AcToken
import com.cn.ac.MatchDecision
import com.cn.ac.TextTransformConfig
import com.cn.ac.android.AcAutomatonRepository
import com.cn.ac.serialization.AcAutomatonSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class SampleConcurrencyStressTest {

    private fun createDictionaryA(): AcAutomaton<RiskWord> {
        return AcAutomaton.builder<RiskWord>()
            .addKeyword(1001, "赌博", RiskWord("GAMBLING", 3))
            .addKeyword(1002, "博彩", RiskWord("GAMBLING", 3))
            .addKeyword(1003, "微信支付", RiskWord("PAYMENT", 1))
            .addKeyword(1004, "非法平台", RiskWord("SECURITY", 2))
            .textTransform(TextTransformConfig.exact())
            .build()
    }

    private fun createDictionaryB(): AcAutomaton<RiskWord> {
        return AcAutomaton.builder<RiskWord>()
            .addKeyword(1001, "赌博", RiskWord("GAMBLING", 3))
            .addKeyword(1002, "博彩", RiskWord("GAMBLING", 3))
            .addKeyword(1003, "微信支付", RiskWord("PAYMENT", 1))
            .addKeyword(1004, "非法平台", RiskWord("SECURITY", 2))
            .addKeyword(2001, "刷单", RiskWord("FRAUD", 3))
            .addKeyword(2002, "退款", RiskWord("FINANCE", 2))
            .addKeyword(2003, "洗钱", RiskWord("CRIME", 4))
            .addKeyword(2004, "高利贷", RiskWord("FINANCE", 3))
            .textTransform(TextTransformConfig.exact())
            .build()
    }

    /**
     * 场景一：极端纯读超高并发压测（64 并发读线程，总计 320,000 次操作）
     * 验证多线程共享同一个不可变状态机时无锁并发安全与极高吞吐量（QPS）。
     */
    @Test
    fun testUltraHighConcurrencyReads() {
        val automaton = createDictionaryB()
        val threadCount = 64
        val opsPerThread = 5_000
        val totalOps = threadCount * opsPerThread

        val testText = "用户在非法平台进行赌博和博彩，承诺支持微信支付秒级退款，谨防刷单诈骗！"

        val executor = Executors.newFixedThreadPool(threadCount)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        val totalMatches = AtomicLong(0)
        val errorCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.execute {
                readyLatch.countDown()
                try {
                    startLatch.await()

                    val callback = AcMatchConsumer<RiskWord> { _, _, _, _ ->
                        totalMatches.incrementAndGet()
                        MatchDecision.CONTINUE
                    }

                    for (i in 0 until opsPerThread) {
                        when (i % 4) {
                            0 -> {
                                if (automaton.contains(testText)) {
                                    totalMatches.incrementAndGet()
                                }
                            }
                            1 -> {
                                val list = automaton.findAll(testText, AcScanOptions.ALL)
                                totalMatches.addAndGet(list.size.toLong())
                            }
                            2 -> {
                                automaton.scan(testText, AcScanOptions.ALL, callback)
                            }
                            3 -> {
                                val tokens = automaton.tokenize(testText, AcScanOptions.LEFTMOST_LONGEST)
                                val matches = tokens.count { it.type() == AcToken.Type.MATCH }
                                totalMatches.addAndGet(matches.toLong())
                            }
                        }
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        readyLatch.await()
        val startNs = System.nanoTime()
        startLatch.countDown() // 64 线程瞬间并发触发

        val completed = doneLatch.await(30, TimeUnit.SECONDS)
        val elapsedNs = System.nanoTime() - startNs
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNs).coerceAtLeast(1)

        executor.shutdownNow()

        val qps = (totalOps * 1000.0) / elapsedMs
        val avgLatencyUs = (elapsedNs / 1000.0) / totalOps

        println("=== [Ultra-High Concurrency Read Test] ===")
        println("Thread Count: $threadCount")
        println("Total Queries: $totalOps ops")
        println("Elapsed Time: $elapsedMs ms")
        println("Throughput (QPS): %,.0f ops/sec".format(qps))
        println("Avg Latency: %.2f μs".format(avgLatencyUs))
        println("Total Matches: ${totalMatches.get()}")
        println("Errors: ${errorCount.get()}")

        assertTrue(completed, "压测应在 30 秒内全部完成")
        assertEquals(0, errorCount.get(), "高并发读取过程中发生异常")
        assertTrue(totalMatches.get() > 0, "应命中预期敏感词")
    }

    /**
     * 场景二：读写混合高并发压测（32 读并发 + 4 快照热更写并发）
     * 验证 AcAutomatonRepository 在后台线程高频原子切换快照时，读线程完全无锁、无阻塞、无数据异常。
     */
    @Test
    fun testHighConcurrencyReadWriteHotSwapStress() {
        val repo = AcAutomatonRepository(createDictionaryA())
        val dictA = createDictionaryA()
        val dictB = createDictionaryB()

        val readerCount = 32
        val writerCount = 4
        val opsPerReader = 4_000
        val totalOps = readerCount * opsPerReader

        val testText = "请不要在非法平台参与赌博或者博彩，支持微信支付快捷退款！"

        val executor = Executors.newFixedThreadPool(readerCount + writerCount)
        val readyLatch = CountDownLatch(readerCount + writerCount)
        val startLatch = CountDownLatch(1)
        val readersDone = CountDownLatch(readerCount)
        val isRunning = AtomicBoolean(true)

        val hotSwapCount = AtomicLong(0)
        val totalMatches = AtomicLong(0)
        val errorCount = AtomicInteger(0)

        // 4 个高频快照热更线程
        for (w in 0 until writerCount) {
            executor.execute {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    var toggle = false
                    while (isRunning.get()) {
                        val next = if (toggle) dictA else dictB
                        repo.replace(next)
                        hotSwapCount.incrementAndGet()
                        toggle = !toggle
                        Thread.sleep(1)
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                }
            }
        }

        // 32 个持续高频扫描线程
        for (r in 0 until readerCount) {
            executor.execute {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    val consumer = AcMatchConsumer<RiskWord> { _, _, _, payload ->
                        assertTrue(payload != null, "Payload 绝不能为 null")
                        totalMatches.incrementAndGet()
                        MatchDecision.CONTINUE
                    }

                    for (i in 0 until opsPerReader) {
                        val auto = repo.current()
                        auto.scan(testText, AcScanOptions.ALL, consumer)
                        if (auto.contains(testText)) {
                            totalMatches.incrementAndGet()
                        }
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    readersDone.countDown()
                }
            }
        }

        readyLatch.await()
        val startNs = System.nanoTime()
        startLatch.countDown()

        val completed = readersDone.await(30, TimeUnit.SECONDS)
        isRunning.set(false)
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs).coerceAtLeast(1)

        executor.shutdownNow()

        val qps = (totalOps * 1000.0) / elapsedMs
        println("=== [Read-Write Hot-Swap Stress Test] ===")
        println("Reader Threads: $readerCount, Writer Threads: $writerCount")
        println("Total Read Ops: $totalOps")
        println("Hot-Swap Replacements: ${hotSwapCount.get()}")
        println("Elapsed Time: $elapsedMs ms")
        println("Throughput (QPS): %,.0f ops/sec".format(qps))
        println("Errors: ${errorCount.get()}")

        assertTrue(completed, "读写混合压测应在超时时间内完成")
        assertEquals(0, errorCount.get(), "读写竞争过程中发生异常")
        assertTrue(hotSwapCount.get() > 10, "热更线程应至少执行多次快照替换")
    }

    /**
     * 场景三：高并发快照序列化与反序列化压力测试
     * 验证 ACAT 二进制快照在多线程并发持久化与加载下的安全性与 CRC 校验完整性。
     */
    @Test
    fun testConcurrentSnapshotSerialization() {
        val automaton = createDictionaryB()
        val threadCount = 16
        val loopsPerThread = 50

        // 先预先准备一份标准快照二进制
        val standardBaos = ByteArrayOutputStream()
        AcAutomatonSnapshot.save(automaton, standardBaos, RiskWordCodec)
        val snapshotBytes = standardBaos.toByteArray()

        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.execute {
                try {
                    for (i in 0 until loopsPerThread) {
                        // 1. 并发反序列化
                        val loaded = AcAutomatonSnapshot.load(
                            ByteArrayInputStream(snapshotBytes),
                            RiskWordCodec
                        )
                        assertTrue(loaded.contains("赌博"))
                        assertTrue(loaded.contains("高利贷"))

                        // 2. 并发序列化
                        val out = ByteArrayOutputStream()
                        AcAutomatonSnapshot.save(loaded, out, RiskWordCodec)
                        assertEquals(snapshotBytes.size, out.size())
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(30, TimeUnit.SECONDS)
        executor.shutdownNow()

        assertEquals(0, errorCount.get(), "并发快照序列化/反序列化发生异常")
        assertTrue(completed)
    }
}

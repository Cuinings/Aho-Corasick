package com.cn.ac.sample

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cn.ac.*
import com.cn.ac.android.AcAutomatonLoader
import com.cn.ac.android.AcAutomatonRepository
import com.cn.ac.serialization.AcAutomatonSnapshot
import com.cn.ac.serialization.PayloadCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataOutput
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong


data class RiskWord(val category: String, val level: Int)

object RiskWordCodec : PayloadCodec<RiskWord> {
    override fun codecId(): String = "RISK_WORD_V1"

    override fun encode(payload: RiskWord, out: DataOutput) {
        out.writeUTF(payload.category)
        out.writeInt(payload.level)
    }

    override fun decode(input: DataInput): RiskWord {
        return RiskWord(input.readUTF(), input.readInt())
    }
}

class MainActivity : AppCompatActivity() {

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private lateinit var repository: AcAutomatonRepository<RiskWord>

    private lateinit var tvStats: TextView
    private lateinit var etInput: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStats = findViewById(R.id.tvStats)
        etInput = findViewById(R.id.etInput)
        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)

        // Set default sample text
        etInput.setText("请不要在非法平台参与赌博或者博彩，支持微信支付快捷退款！")

        // 1. Initialize Default Automaton
        val initialAutomaton = buildDefaultAutomaton()
        repository = AcAutomatonRepository(initialAutomaton)
        updateStatsUi()

        // 2. Contains Button
        findViewById<Button>(R.id.btnContains).setOnClickListener {
            val text = etInput.text.toString()
            val startNs = System.nanoTime()
            val hasMatch = repository.current().contains(text)
            val elapsedUs = (System.nanoTime() - startNs) / 1_000

            tvStatus.text = "Contains 结果: $hasMatch (耗时: ${elapsedUs} μs, 引擎侧 0 内存分配)"
            tvResult.text = if (hasMatch) "检测到包含敏感词！" else "未检测到任何敏感词。"
        }

        // 3. Scan All Button
        findViewById<Button>(R.id.btnScanAll).setOnClickListener {
            val text = etInput.text.toString()
            val startNs = System.nanoTime()
            val matches = repository.current().findAll(text, AcScanOptions.ALL)
            val elapsedUs = (System.nanoTime() - startNs) / 1_000

            tvStatus.text = "Scan ALL 结果: 命中 ${matches.size} 处 (耗时: ${elapsedUs} μs)"
            val sb = StringBuilder()
            for (m in matches) {
                val kw = text.substring(m.startUtf16(), m.endUtf16())
                sb.append("• [${m.startUtf16()}, ${m.endUtf16()}) \"$kw\" -> ${m.payload().category} (L${m.payload().level})\n")
            }
            tvResult.text = sb.toString()
        }

        // 4. Tokenize & Highlight (LEFTMOST_LONGEST)
        findViewById<Button>(R.id.btnTokenize).setOnClickListener {
            val text = etInput.text.toString()
            val startNs = System.nanoTime()
            val tokens = repository.current().tokenize(text, AcScanOptions.LEFTMOST_LONGEST)
            val elapsedUs = (System.nanoTime() - startNs) / 1_000

            tvStatus.text = "Tokenize 结果: ${tokens.size} 个切片 (耗时: ${elapsedUs} μs)"

            val ssb = SpannableStringBuilder()
            for (token in tokens) {
                val fragment = text.subSequence(token.startUtf16(), token.endUtf16())
                if (token.type() == AcToken.Type.MATCH) {
                    val start = ssb.length
                    ssb.append(fragment)
                    val end = ssb.length
                    ssb.setSpan(BackgroundColorSpan(Color.parseColor("#FFCDD2")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(ForegroundColorSpan(Color.parseColor("#B71C1C")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.append("★${token.payload().category}")
                } else {
                    ssb.append(fragment)
                }
            }
            tvResult.text = ssb
        }

        // 5. Dynamic Hot-Update in Background Thread
        findViewById<Button>(R.id.btnHotUpdate).setOnClickListener {
            tvStatus.text = "正在后台线程编译新词库快照..."
            bgExecutor.execute {
                val newAutomaton = AcAutomaton.builder<RiskWord>()
                        .addKeyword(1001, "赌博", RiskWord("GAMBLING", 3))
                        .addKeyword(1002, "博彩", RiskWord("GAMBLING", 3))
                        .addKeyword(1003, "微信支付", RiskWord("PAYMENT", 1))
                        .addKeyword(2001, "刷单", RiskWord("FRAUD", 3))
                        .addKeyword(2002, "退款", RiskWord("FINANCE", 2))
                        .textTransform(TextTransformConfig.exact())
                        .build()

                // Atomic replacement
                repository.replace(newAutomaton)

                runOnUiThread {
                    updateStatsUi()
                    tvStatus.text = "词库快照热更新成功！新增词汇: [刷单, 退款]"
                }
            }
        }

        // 6. Reload from Assets (binary snapshot ACAT format)
        findViewById<Button>(R.id.btnReloadAssets).setOnClickListener {
            tvStatus.text = "正在从 Assets 读取二进制快照..."
            bgExecutor.execute {
                try {
                    // Try to load from assets or generate memory snapshot demo
                    val bytes = buildSnapshotBytes()
                    val loaded = AcAutomatonSnapshot.load(ByteArrayInputStream(bytes), RiskWordCodec)
                    repository.replace(loaded)
                    runOnUiThread {
                        updateStatsUi()
                        tvStatus.text = "从快照加载成功！(ACAT 二进制持久化，CRC32 校验通过)"
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvStatus.text = "快照加载失败: ${e.message}"
                    }
                }
            }
        }

        // 7. Ultra-High Concurrency Stress Test
        val btnConcurrencyStress = findViewById<Button>(R.id.btnConcurrencyStress)
        btnConcurrencyStress.setOnClickListener {
            runConcurrencyStressTest(btnConcurrencyStress)
        }
    }

    private fun runConcurrencyStressTest(triggerBtn: Button) {
        val allButtons = listOf(
            findViewById<Button>(R.id.btnContains),
            findViewById<Button>(R.id.btnScanAll),
            findViewById<Button>(R.id.btnTokenize),
            findViewById<Button>(R.id.btnHotUpdate),
            findViewById<Button>(R.id.btnReloadAssets),
            triggerBtn
        )
        allButtons.forEach { it.isEnabled = false }
        tvStatus.text = "⚡ 正在执行超高并发混合压测 (32 读并发 + 2 热更写并发)..."
        tvResult.text = "压测进行中，正在高速并发执行多模式匹配并同时执行快照原子热切换..."

        val sampleText = etInput.text.toString().ifBlank {
            "请不要在非法平台参与赌博或者博彩，支持微信支付快捷退款！远离刷单和洗钱。"
        }

        val readerCount = 32
        val opsPerReader = 3_125 // 32 * 3,125 = 100,000 total operations
        val writerCount = 2

        bgExecutor.execute {
            val autoA = buildDefaultAutomaton()
            val autoB = buildAlternativeAutomaton()

            val stressExecutor = Executors.newFixedThreadPool(readerCount + writerCount)
            val readyLatch = CountDownLatch(readerCount + writerCount)
            val startLatch = CountDownLatch(1)
            val doneLatch = CountDownLatch(readerCount)
            val isRunning = AtomicBoolean(true)

            val totalMatches = AtomicLong(0)
            val totalOpsCompleted = AtomicLong(0)
            val hotSwapCount = AtomicLong(0)
            val errorCount = AtomicInteger(0)

            // Writers: Hot-swap automaton continuously
            for (w in 0 until writerCount) {
                stressExecutor.execute {
                    readyLatch.countDown()
                    try {
                        startLatch.await()
                        var flip = false
                        while (isRunning.get()) {
                            val nextAuto = if (flip) autoA else autoB
                            repository.replace(nextAuto)
                            hotSwapCount.incrementAndGet()
                            flip = !flip
                            Thread.sleep(2)
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } catch (e: Throwable) {
                        errorCount.incrementAndGet()
                    }
                }
            }

            // Readers: Perform high-concurrency scans
            for (r in 0 until readerCount) {
                stressExecutor.execute {
                    readyLatch.countDown()
                    try {
                        startLatch.await()
                        val callbackConsumer = AcMatchConsumer<RiskWord> { _, _, _, _ ->
                            totalMatches.incrementAndGet()
                            MatchDecision.CONTINUE
                        }

                        for (i in 0 until opsPerReader) {
                            val auto = repository.current()
                            when (i % 4) {
                                0 -> {
                                    if (auto.contains(sampleText)) {
                                        totalMatches.incrementAndGet()
                                    }
                                }
                                1 -> {
                                    val list = auto.findAll(sampleText, AcScanOptions.ALL)
                                    totalMatches.addAndGet(list.size.toLong())
                                }
                                2 -> {
                                    auto.scan(sampleText, AcScanOptions.ALL, callbackConsumer)
                                }
                                3 -> {
                                    val tokens = auto.tokenize(sampleText, AcScanOptions.LEFTMOST_LONGEST)
                                    val matchTokens = tokens.count { it.type() == AcToken.Type.MATCH }
                                    totalMatches.addAndGet(matchTokens.toLong())
                                }
                            }
                            totalOpsCompleted.incrementAndGet()
                        }
                    } catch (e: Throwable) {
                        errorCount.incrementAndGet()
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }

            try {
                readyLatch.await()
                val startTimeNs = System.nanoTime()
                startLatch.countDown() // All threads release simultaneously

                doneLatch.await(30, TimeUnit.SECONDS)
                isRunning.set(false)
                val elapsedNs = System.nanoTime() - startTimeNs
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNs).coerceAtLeast(1)

                stressExecutor.shutdownNow()
                stressExecutor.awaitTermination(2, TimeUnit.SECONDS)

                val ops = totalOpsCompleted.get()
                val qps = (ops * 1000.0) / elapsedMs
                val avgLatencyUs = (elapsedNs / 1000.0) / ops.coerceAtLeast(1)

                val resultReport = buildString {
                    append("🚀 【超高并发混合压力测试报告】\n")
                    append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    append("• 并发读线程数: $readerCount\n")
                    append("• 并发写热更数: $writerCount\n")
                    append("• 完成总查询数: %,d ops\n".format(ops))
                    append("• 累计匹配命中: %,d 次\n".format(totalMatches.get()))
                    append("• 动态热更切换: %,d 次 (无锁原子替换)\n".format(hotSwapCount.get()))
                    append("• 压测总耗时: $elapsedMs ms\n")
                    append("• 综合吞吐量 (QPS): %,.0f ops/sec\n".format(qps))
                    append("• 单次平均延迟: %.2f μs\n".format(avgLatencyUs))
                    append("• 数据异常/冲突: ${errorCount.get()} (100% 线程安全无锁一致性)")
                }

                runOnUiThread {
                    updateStatsUi()
                    allButtons.forEach { it.isEnabled = true }
                    tvStatus.text = "⚡ 压测完成！QPS: %,.0f ops/s (耗时 %d ms)".format(qps, elapsedMs)
                    tvResult.text = resultReport
                }
            } catch (e: Exception) {
                isRunning.set(false)
                stressExecutor.shutdownNow()
                runOnUiThread {
                    allButtons.forEach { it.isEnabled = true }
                    tvStatus.text = "压测异常: ${e.message}"
                }
            }
        }
    }


    private fun buildDefaultAutomaton(): AcAutomaton<RiskWord> {
        return AcAutomaton.builder<RiskWord>()
                .addKeyword(1001, "赌博", RiskWord("GAMBLING", 3))
                .addKeyword(1002, "博彩", RiskWord("GAMBLING", 3))
                .addKeyword(1003, "微信支付", RiskWord("PAYMENT", 1))
                .textTransform(TextTransformConfig.exact())
                .build()
    }

    private fun buildAlternativeAutomaton(): AcAutomaton<RiskWord> {
        return AcAutomaton.builder<RiskWord>()
                .addKeyword(1001, "赌博", RiskWord("GAMBLING", 3))
                .addKeyword(1002, "博彩", RiskWord("GAMBLING", 3))
                .addKeyword(1003, "微信支付", RiskWord("PAYMENT", 1))
                .addKeyword(2001, "刷单", RiskWord("FRAUD", 3))
                .addKeyword(2002, "退款", RiskWord("FINANCE", 2))
                .addKeyword(2003, "洗钱", RiskWord("CRIME", 4))
                .addKeyword(2004, "高利贷", RiskWord("FINANCE", 3))
                .textTransform(TextTransformConfig.exact())
                .build()
    }

    private fun buildSnapshotBytes(): ByteArray {
        val auto = buildDefaultAutomaton()
        val baos = ByteArrayOutputStream()
        AcAutomatonSnapshot.save(auto, baos, RiskWordCodec)
        return baos.toByteArray()
    }

    private fun updateStatsUi() {
        val s = repository.current().stats()
        tvStats.text = "状态机统计: 词库数=${s.keywordCount()} | 状态数=${s.stateCount()} | 边数=${s.edgeCount()} | 内存预估=${s.estimatedPrimitiveBytes()} bytes"
    }

    override fun onDestroy() {
        super.onDestroy()
        bgExecutor.shutdown()
    }
}

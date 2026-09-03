package com.cn.ac.kotlin

import com.cn.ac.*

/**
 * Kotlin DSL 自动机构建器代理类。
 */
class AcBuilderDsl<T> {
    private val builder = AcAutomaton.builder<T>()

    /** 添加关键词（自动分配自增 ID） */
    fun keyword(keyword: String, payload: T? = null) {
        builder.addKeyword(keyword, payload)
    }

    /** 添加关键词并指定 ID */
    fun keyword(id: Int, keyword: String, payload: T? = null) {
        builder.addKeyword(id, keyword, payload)
    }

    /** 设置文本变换配置（大小写折叠、Unicode 规范化） */
    fun transform(config: TextTransformConfig) {
        builder.textTransform(config)
    }

    /** 设置重复词处理策略 */
    fun duplicatePolicy(policy: DuplicateKeywordPolicy) {
        builder.duplicatePolicy(policy)
    }

    /** 编译并构建不可变的 [AcAutomaton] */
    fun build(): AcAutomaton<T> = builder.build()
}

/**
 * 声明式构建 Aho-Corasick 自动机的 Kotlin 顶层 DSL 函数。
 *
 * 示例：
 * ```kotlin
 * val automaton = acAutomaton<String> {
 *     keyword("apple", "FRUIT")
 *     keyword(100, "banana", "FRUIT")
 * }
 * ```
 */
fun <T> acAutomaton(init: AcBuilderDsl<T>.() -> Unit): AcAutomaton<T> {
    val dsl = AcBuilderDsl<T>()
    dsl.init()
    return dsl.build()
}

/**
 * 查找文本中首个最左优先匹配项，若无匹配则返回 null（Kotlin 空安全扩展）。
 */
fun <T> AcAutomaton<T>.findFirstOrNull(text: CharSequence, options: AcScanOptions? = null): AcMatch<T>? {
    return this.findFirst(text, options)
}

/**
 * 将匹配结果转换为惰性求值的 Kotlin [Sequence]，便于使用 filter / map 等流式操作符。
 */
fun <T> AcAutomaton<T>.asSequence(text: CharSequence, options: AcScanOptions? = null): Sequence<AcMatch<T>> {
    return findAll(text, options ?: AcScanOptions.ALL).asSequence()
}

package com.cn.ac.kotlin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AcKotlinTest {

    @Test
    fun testKotlinDslAndSequence() {
        val automaton = acAutomaton<String> {
            keyword("kotlin", "KT")
            keyword(100, "java", "JV")
        }

        assertTrue(automaton.contains("I love kotlin and java"))

        val first = automaton.findFirstOrNull("I love kotlin and java")
        assertNotNull(first)
        assertEquals("kotlin", "I love kotlin and java".substring(first!!.startUtf16(), first.endUtf16()))

        val seqList = automaton.asSequence("I love kotlin and java").toList()
        assertEquals(2, seqList.size)
        assertEquals("kotlin", "I love kotlin and java".substring(seqList[0].startUtf16(), seqList[0].endUtf16()))
        assertEquals("java", "I love kotlin and java".substring(seqList[1].startUtf16(), seqList[1].endUtf16()))
    }
}

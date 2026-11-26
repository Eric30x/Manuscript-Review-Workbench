package com.newsreview.workbench.domain.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 多模式匹配的命中区间是左闭右开 [start, end)，界面直接用它画高亮。 */
class AhoCorasickTest {

    @Test
    fun findsOverlappingPatternsWithCorrectSpans() {
        val hits = AhoCorasick(listOf("he", "she", "hers")).findAll("ushers")

        assertEquals(3, hits.size)
        assertTrue(hits.any { it.pattern == "she" && it.start == 1 && it.end == 4 })
        assertTrue(hits.any { it.pattern == "he" && it.start == 2 && it.end == 4 })
        assertTrue(hits.any { it.pattern == "hers" && it.start == 2 && it.end == 6 })
    }

    @Test
    fun emptyTextProducesNoHits() {
        assertTrue(AhoCorasick(listOf("反应热烈")).findAll("").isEmpty())
    }
}

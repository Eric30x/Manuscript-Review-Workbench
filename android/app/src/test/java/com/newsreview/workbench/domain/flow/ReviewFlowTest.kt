package com.newsreview.workbench.domain.flow

import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.domain.model.ReviewStep
import com.newsreview.workbench.domain.model.Signers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 流程状态机与"文末三审署名行"的行为约束。 */
class ReviewFlowTest {

    @Test
    fun passingFirstSendsToSecondReview() {
        val (status, next) = ReviewFlow.afterPass(ReviewStep.FIRST)
        assertEquals(ArticleStatus.IN_REVIEW, status)
        assertEquals(ReviewStep.SECOND, next)
    }

    @Test
    fun passingFinalSignsTheArticle() {
        val (status, next) = ReviewFlow.afterPass(ReviewStep.FINAL)
        assertEquals(ArticleStatus.SIGNED, status)
        assertNull(next)
    }

    @Test
    fun threeStepsMapToOneRoleEach() {
        assertEquals(ReviewStep.FIRST, ReviewFlow.stepOf(com.newsreview.workbench.domain.model.Role.REVIEW_L1))
        assertEquals(ReviewStep.SECOND, ReviewFlow.stepOf(com.newsreview.workbench.domain.model.Role.REVIEW_L2))
        assertEquals(ReviewStep.FINAL, ReviewFlow.stepOf(com.newsreview.workbench.domain.model.Role.FINAL))
        assertNull(ReviewFlow.stepOf(com.newsreview.workbench.domain.model.Role.WRITER))
    }

    @Test
    fun signerLineListsAllThreeReviewers() {
        val line = ReviewFlow.signerLine(Signers(first = "甲", second = "乙", final = "丙"))
        assertTrue(line.contains("初审 甲"))
        assertTrue(line.contains("复审 乙"))
        assertTrue(line.contains("终审 丙"))
    }

    @Test
    fun signerLineIsAppendedExactlyOnce() {
        val signers = Signers(first = "甲", second = "乙", final = "丙")
        val once = ReviewFlow.appendSignerLine(listOf("正文第一段"), signers)
        val twice = ReviewFlow.appendSignerLine(once, signers)
        assertEquals(once, twice)
    }
}


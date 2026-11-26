package com.newsreview.workbench.domain.check

import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.FindingKind
import com.newsreview.workbench.domain.model.FindingLevel
import com.newsreview.workbench.domain.model.MaterialKind
import com.newsreview.workbench.domain.model.ReferenceMaterial
import com.newsreview.workbench.domain.model.TranscriptCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检查器的行为约束。两条不变量最值得钉住：
 *  ① 命中区间必须精确到字（界面靠它画红线、一键替换也靠它）；
 *  ② 把握度必须等于证据链增量之和（不能是拍出来的数）。
 *
 * 这些用例只依赖纯 Kotlin 的 domain 层，普通 JVM 单元测试即可跑，不需要模拟器。
 */
class ArticleCheckerTest {

    private val checker = ArticleChecker.default()

    private fun article(
        title: String = "测试标题",
        pushText: String = "",
        paragraphs: List<String>
    ) = Article(id = "a-test", title = title, pushText = pushText, paragraphs = paragraphs)

    @Test
    fun typoIsDetectedWithExactSpan() {
        val text = "活动结束，现场反应热烈。"
        val report = checker.check(article(paragraphs = listOf(text)), emptyList())

        val finding = report.findings.first { it.quote == "反应热烈" }
        assertEquals(FindingKind.TYPO, finding.kind)
        assertEquals(FindingLevel.L1, finding.level)
        assertEquals("反响热烈", finding.suggestions.first())
        assertEquals("反应热烈", text.substring(finding.start, finding.end))
    }

    @Test
    fun bannedWordIsReportedButNeverAutoResolved() {
        val report = checker.check(article(paragraphs = listOf("据学院内部通知，活动将继续。")), emptyList())

        val finding = report.findings.first { it.kind == FindingKind.BANNED_WORD }
        assertTrue("敏感词必须进待核实", finding.needsConfirm)
    }

    @Test
    fun confidenceEqualsSumOfEvidenceDeltas() {
        val report = checker.check(
            article(
                title = "校园开放日报名情况",
                pushText = "校园开放日顺利举行，200 人到场",
                paragraphs = listOf("本次开放日共有 200 人到场。")
            ),
            emptyList()
        )

        assertTrue(report.findings.isNotEmpty())
        report.findings.forEach { finding ->
            assertEquals(finding.evidence.sumOf { it.delta }, finding.confidence, 0.001)
        }
    }

    @Test
    fun inconsistentScopeAcrossTitleBodyAndPushTextIsFlagged() {
        val report = checker.check(
            article(
                title = "校园开放日报名情况",
                pushText = "校园开放日顺利举行，200 人到场",
                paragraphs = listOf("本次开放日共有 200 人到场。")
            ),
            emptyList()
        )

        assertTrue(report.findings.any { it.title.contains("统计口径不一致") })
    }

    @Test
    fun numberOfAttendeesIsCrossCheckedAgainstSignInSheet() {
        val materials = listOf(
            ReferenceMaterial(
                id = "m-signin",
                name = "签到表",
                kind = MaterialKind.SIGN_IN,
                lines = listOf("来访考生与家长签到 126 人", "报名系统导出：报名 200 人")
            )
        )
        val report = checker.check(article(paragraphs = listOf("据统计，本次开放日共有 200 人到场。")), materials)

        val finding = report.findings.first { it.title.contains("到场人数") }
        assertTrue(finding.evidence.any { it.source == "签到表" })
        assertTrue(finding.needsConfirm)
    }

    @Test
    fun quoteLosingItsConditionIsFlagged() {
        val transcript = listOf(
            TranscriptCue(
                id = "cue-1",
                startMs = 12 * 60_000L,
                endMs = 12 * 60_000L + 17_000L,
                speaker = "周宁",
                text = "明年我们会启动试点，先在三个学院推，如果评估通过，再考虑全校铺开。"
            )
        )
        val report = checker.check(
            article(paragraphs = listOf("周宁在交流中表示「明年将启动试点，先在三个学院推行」。")),
            emptyList(),
            transcript
        )

        assertTrue(report.findings.any { it.title.contains("限定条件") })
    }
}


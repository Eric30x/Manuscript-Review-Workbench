package com.newsreview.workbench.domain.flow

import com.newsreview.workbench.domain.model.ActorAction
import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.domain.model.ReviewStep
import com.newsreview.workbench.domain.model.Role
import com.newsreview.workbench.domain.model.Signers
import com.newsreview.workbench.domain.model.TimelineEntry

/**
 * 审核流程的纯函数部分。
 *
 * 口径（三份材料统一到这一套，别再改）：
 *   写稿人提交 → AI 预检（不是一级审核，是每一级都能看的辅助证据层）
 *            → 编辑初审 → 指导老师二审 → 负责人签发 → 发布归档
 *
 * 退回规则：任一级可退回，意见必填；退回时抄送此前已通过的各级；
 * 改稿后回到被打回的那一级，已经通过的环节不重审。
 */
object ReviewFlow {

    val order: List<ReviewStep> = listOf(ReviewStep.FIRST, ReviewStep.SECOND, ReviewStep.FINAL)

    fun next(step: ReviewStep): ReviewStep? = order.getOrNull(order.indexOf(step) + 1)

    fun stepOf(role: Role): ReviewStep? = order.firstOrNull { it.role == role }

    /** 通过后：还有下一级就继续流转，没有就变成"已签发"。 */
    fun afterPass(current: ReviewStep): Pair<ArticleStatus, ReviewStep?> =
        next(current)?.let { ArticleStatus.IN_REVIEW to it } ?: (ArticleStatus.SIGNED to null)

    fun afterReturn(): Pair<ArticleStatus, ReviewStep?> = ArticleStatus.RETURNED to null

    fun afterSubmit(): Pair<ArticleStatus, ReviewStep?> = ArticleStatus.IN_REVIEW to ReviewStep.FIRST

    /** 那位"被打回的环节"由退回时记录，重新送审后回到它。 */
    fun stepForResubmit(article: Article): ReviewStep =
        article.returnedAt ?: article.currentStep ?: ReviewStep.FIRST

    /** 从流转记录里取出三级审稿人，用于生成文末署名行。 */
    fun signersFrom(timeline: List<TimelineEntry>): Signers? {
        fun actorOf(step: ReviewStep): String? = timeline
            .lastOrNull { it.step == step && (it.action == ActorAction.PASS || it.action == ActorAction.SIGN) }
            ?.actor
        val first = actorOf(ReviewStep.FIRST) ?: return null
        val second = actorOf(ReviewStep.SECOND) ?: return null
        val third = actorOf(ReviewStep.FINAL) ?: return null
        return Signers(first = first, second = second, final = third)
    }

    /**
     * 学校通知的硬要求：三审人员姓名需明确标注在文末。
     * 这里把它做成签发时自动写入，而不是靠人记得加上去。
     */
    fun signerLine(signers: Signers): String =
        "三审审稿人：初审 ${signers.first}　复审 ${signers.second}　终审 ${signers.final}"

    fun appendSignerLine(paragraphs: List<String>, signers: Signers): List<String> {
        val line = signerLine(signers)
        if (paragraphs.any { it.contains(line) }) return paragraphs
        val body = if (paragraphs.isNotEmpty() && paragraphs.last().isBlank()) paragraphs.dropLast(1) else paragraphs
        return body + listOf("", line)
    }
}

package com.newsreview.workbench.data

import com.newsreview.workbench.domain.model.ActorAction
import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.ArticleImage
import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.domain.model.ArticleVersion
import com.newsreview.workbench.domain.model.Attachment
import com.newsreview.workbench.domain.model.Channel
import com.newsreview.workbench.domain.model.CommentStatus
import com.newsreview.workbench.domain.model.MaterialKind
import com.newsreview.workbench.domain.model.ReferenceMaterial
import com.newsreview.workbench.domain.model.ReviewComment
import com.newsreview.workbench.domain.model.ReviewStep
import com.newsreview.workbench.domain.model.Role
import com.newsreview.workbench.domain.model.Signers
import com.newsreview.workbench.domain.model.TimelineEntry
import com.newsreview.workbench.domain.model.TranscriptCue

/**
 * 演示数据。全部为虚构内容，不含任何真实单位与人员信息。
 * 演示脚本见项目根目录 README-构建与演示.md。
 */
object DemoData {

    const val MATERIAL_NOTICE = "m-notice"
    const val MATERIAL_SIGN_IN = "m-signin"
    const val MATERIAL_AGENDA = "m-agenda"
    const val MATERIAL_TRANSCRIPT = "m-transcript"

    const val ARTICLE_OPEN_DAY = "a-open-day"
    const val ARTICLE_TRAINING = "a-training"
    const val ARTICLE_CLUB = "a-club"
    const val ARTICLE_SPORTS = "a-sports"

    /** 演示用角色-姓名映射（"登录"后按角色显示）。 */
    val roleNames: Map<Role, String> = mapOf(
        Role.WRITER to "林小舟",
        Role.REVIEW_L1 to "沈佳",
        Role.REVIEW_L2 to "顾言",
        Role.FINAL to "郑毅",
        Role.PUBLISHER to "何书",
        Role.ADMIN to "管理员"
    )

    /** 演示用的一篇"导入 Word"样例，内容见 assets/demo/sample-article.docx。 */
    const val SAMPLE_DOCX_ASSET = "demo/sample-article.docx"

    fun materials(): List<ReferenceMaterial> = listOf(
        ReferenceMaterial(
            id = MATERIAL_NOTICE,
            name = "关于举办校园开放日的通知",
            kind = MaterialKind.NOTICE,
            lines = listOf(
                "时间：6月15日（周六）9:00-11:30",
                "地点：自动化学院教学实验室、科研实验室、创客中心",
                "面向对象：高三考生及家长",
                "注意事项：来访者由志愿者分组引导，实验室内不得触碰设备"
            )
        ),
        ReferenceMaterial(
            id = MATERIAL_SIGN_IN,
            name = "校园开放日签到表",
            kind = MaterialKind.SIGN_IN,
            lines = listOf(
                "志愿者签到 41 人",
                "来访考生与家长签到 126 人",
                "报名系统导出：报名 200 人",
                "备注：报名数为系统预约数，签到数为现场实际登记数"
            )
        ),
        ReferenceMaterial(
            id = MATERIAL_AGENDA,
            name = "校园开放日活动议程",
            kind = MaterialKind.AGENDA,
            lines = listOf(
                "1. 签到与分组（9:00-9:20）",
                "2. 实验室参观，讲解：实验中心 张明老师",
                "3. 交流环节，致辞：周宁 · 校团委组织部部长",
                "主持：学院宣传中心"
            )
        ),
        ReferenceMaterial(
            id = MATERIAL_TRANSCRIPT,
            name = "采访录音转写",
            kind = MaterialKind.TRANSCRIPT,
            lines = listOf(
                "[00:12:04] 周宁：明年我们会启动试点，先在三个学院推，如果评估通过，再考虑全校铺开。",
                "[00:13:41] 张明：这次开放了教学、科研和创客中心三处。",
                "[00:15:02] 家长：孩子对机器人方向比较感兴趣。"
            )
        )
    )

    fun transcript(): List<TranscriptCue> = listOf(
        TranscriptCue(
            id = "cue-1",
            startMs = 12 * 60_000L + 4_000L,
            endMs = 12 * 60_000L + 21_000L,
            speaker = "周宁",
            text = "明年我们会启动试点，先在三个学院推，如果评估通过，再考虑全校铺开。"
        ),
        TranscriptCue(
            id = "cue-2",
            startMs = 13 * 60_000L + 41_000L,
            endMs = 13 * 60_000L + 48_000L,
            speaker = "张明",
            text = "这次开放了教学、科研和创客中心三处。"
        ),
        TranscriptCue(
            id = "cue-3",
            startMs = 15 * 60_000L + 2_000L,
            endMs = 15 * 60_000L + 9_000L,
            speaker = "家长",
            text = "孩子对机器人方向比较感兴趣。"
        )
    )

    fun articles(): List<Article> = listOf(openDayArticle(), trainingArticle(), clubArticle(), sportsArticle())

    // ------------------------------------------------------------------ 主演示稿件

    private fun openDayArticle(): Article {
        val writer = roleNames.getValue(Role.WRITER)
        val l1 = roleNames.getValue(Role.REVIEW_L1)
        val l2 = roleNames.getValue(Role.REVIEW_L2)
        val paragraphs = listOf(
            "6月15日，本校举办校园开放日。自动化学院向考生和家长开放了教学实验室、科研实验室和创客中心。",
            "在大学生志愿者带领下，来访者走进实验场所，观看老师操作精密仪器，了解设备如何用于教学和研究。",
            "考生和家长围绕智能控制、机器人及电力电子技术等方向与现场老师交流，并通过演示了解相关专业的学习内容。",
            "活动邀请校团委副书记 周宁出席。周宁在致辞中介绍了活动安排。",
            "开放日为来访者提供了接触实验室的机会，也让他们对自动化专业的学习和实践有了更直观的认识。",
            "据统计，本次开放日共有 200 人到场，现场反应热烈。",
            "据学院内部通知，本次活动共投入志愿者 41 人。",
            "周宁在交流中表示「明年将启动试点，先在三个学院推行」。"
        )
        val comments = listOf(
            comment("c-1", l2, Role.REVIEW_L2, null, "校园开放日报名情况", "核实到场人数：标题写“报名情况”，正文写“200 人到场”，推送写“200 人到场”，口径不统一。", CommentStatus.OPEN, hoursAgo = 20),
            comment("c-2", l2, Role.REVIEW_L2, null, "校园开放日报名情况", "标题表述再精简，尽量压到 20 字以内。", CommentStatus.OPEN, hoursAgo = 20),
            comment("c-3", l2, Role.REVIEW_L2, 1, "在大学生志愿者带领下", "补充清晰的活动日期与时段，读者需要知道是哪一天哪一段。", CommentStatus.OPEN, hoursAgo = 20),
            comment("c-4", l2, Role.REVIEW_L2, 3, "校团委副书记 周宁", "确认嘉宾职务，以活动方确认的信息为准。", CommentStatus.OPEN, hoursAgo = 20),
            comment("c-5", l2, Role.REVIEW_L2, 5, "200 人到场", "统一报名人数口径，写明是报名还是签到。", CommentStatus.OPEN, hoursAgo = 20),
            comment("c-6", l2, Role.REVIEW_L2, 7, "明年将启动试点", "核对采访引语，原话里有限定条件，别漏。", CommentStatus.OPEN, hoursAgo = 20),
            comment("c-7", l2, Role.REVIEW_L2, 6, "据学院内部通知", "“内部通知”不宜出现在对外发布的稿件里，改为可公开表述。", CommentStatus.OPEN, hoursAgo = 20),
            comment("c-8", l2, Role.REVIEW_L2, null, "全篇", "标题、正文、推送文案三处口径需统一。", CommentStatus.OPEN, hoursAgo = 20),
            comment("c-9", l2, Role.REVIEW_L2, 4, "有了更直观的认识", "“更直观的认识”偏主观，改为可核查的表述。", CommentStatus.OPEN, hoursAgo = 20),
            comment("c-10", l1, Role.REVIEW_L1, 0, "创客中心", "场所名称统一为“创客中心”，与通知一致。", CommentStatus.DONE, hoursAgo = 30),
            comment("c-11", l1, Role.REVIEW_L1, null, "校园开放日报名情况", "标题去掉口语化表达。", CommentStatus.DONE, hoursAgo = 30),
            comment("c-12", l1, Role.REVIEW_L1, 2, "电力电子技术", "补上专业方向表述。", CommentStatus.DONE, hoursAgo = 30)
        )
        val timeline = listOf(
            TimelineEntry(hoursAgo(32), writer, Role.WRITER, ActorAction.SUBMIT, ReviewStep.FIRST, "首次送审"),
            TimelineEntry(hoursAgo(30), l1, Role.REVIEW_L1, ActorAction.PASS, ReviewStep.FIRST, "文字与格式已校，转二审"),
            TimelineEntry(hoursAgo(20), l2, Role.REVIEW_L2, ActorAction.RETURN, ReviewStep.SECOND, "标题人数待核实，核对后重新送审")
        )
        return Article(
            id = ARTICLE_OPEN_DAY,
            title = "校园开放日报名情况",
            pushText = "校园开放日顺利举行，200 人到场",
            paragraphs = paragraphs,
            images = listOf(ArticleImage(caption = "图 1 · 实验室参观现场", placeholder = "实验室参观现场")),
            status = ArticleStatus.RETURNED,
            channel = Channel.COLLEGE_WECHAT,
            currentStep = ReviewStep.SECOND,
            returnedAt = ReviewStep.SECOND,
            returnedBy = l2,
            returnNote = "标题人数与实际到场不一致，请核对后重新送审。",
            passedSteps = setOf(ReviewStep.FIRST),
            versions = listOf(
                ArticleVersion(
                    no = 1,
                    createdAt = hoursAgo(32),
                    author = writer,
                    note = "首次提交，由 Word 导入",
                    paragraphs = paragraphs
                )
            ),
            comments = comments,
            timeline = timeline,
            attachments = listOf(
                Attachment("采访录音.m4a", "录音", "6.8 MB"),
                Attachment("活动议程.pdf", "参考材料", "320 KB"),
                Attachment("签到表.xlsx", "参考材料", "18 KB")
            ),
            referenceIds = listOf(MATERIAL_NOTICE, MATERIAL_SIGN_IN, MATERIAL_AGENDA, MATERIAL_TRANSCRIPT),
            updatedAt = hoursAgo(20)
        )
    }

    // ------------------------------------------------------------------ 待初审 / 草稿 / 已签发

    private fun trainingArticle(): Article {
        val writer = roleNames.getValue(Role.WRITER)
        return Article(
            id = ARTICLE_TRAINING,
            title = "学院举办志愿服务培训",
            pushText = "志愿服务培训顺利结束",
            paragraphs = listOf(
                "5月18日，学院举办志愿服务培训，共有 60 名学生参加。",
                "培训内容包括志愿服务礼仪、应急处置与沟通技巧三个部分。",
                "参训学生表示，通过培训对志愿服务的要求有了更清楚的认识。"
            ),
            status = ArticleStatus.IN_REVIEW,
            channel = Channel.COLLEGE_WECHAT,
            currentStep = ReviewStep.FIRST,
            versions = listOf(ArticleVersion(1, hoursAgo(6), writer, "首次提交", listOf())),
            timeline = listOf(
                TimelineEntry(hoursAgo(6), writer, Role.WRITER, ActorAction.SUBMIT, ReviewStep.FIRST, "首次送审")
            ),
            updatedAt = hoursAgo(6)
        )
    }

    private fun clubArticle(): Article {
        val writer = roleNames.getValue(Role.WRITER)
        return Article(
            id = ARTICLE_CLUB,
            title = "秋季社团招新活动",
            pushText = "",
            paragraphs = listOf(
                "10月上旬，学院社团开展秋季招新活动，共有 12 个社团参加。",
                "招新现场设置展示与咨询区域，学生按兴趣了解社团情况。"
            ),
            status = ArticleStatus.DRAFT,
            channel = null,
            currentStep = null,
            versions = listOf(ArticleVersion(1, hoursAgo(26), writer, "草稿", listOf())),
            timeline = emptyList(),
            updatedAt = hoursAgo(26)
        )
    }

    private fun sportsArticle(): Article {
        val writer = roleNames.getValue(Role.WRITER)
        val l1 = roleNames.getValue(Role.REVIEW_L1)
        val l2 = roleNames.getValue(Role.REVIEW_L2)
        val finalSigner = roleNames.getValue(Role.FINAL)
        val signers = Signers(first = l1, second = l2, final = finalSigner)
        return Article(
            id = ARTICLE_SPORTS,
            title = "校运动会顺利闭幕",
            pushText = "校运动会顺利闭幕，运动员表现突出",
            paragraphs = listOf(
                "4月26日，学院组织学生参加校运动会，共有 128 名学生报名参赛。",
                "运动员在田径、球类与趣味项目中完成各项赛事，现场秩序良好。",
                "学院为参赛学生提供了后勤保障与医疗保障服务。"
            ),
            status = ArticleStatus.SIGNED,
            channel = Channel.SCHOOL_SITE,
            currentStep = null,
            passedSteps = setOf(ReviewStep.FIRST, ReviewStep.SECOND, ReviewStep.FINAL),
            versions = listOf(
                ArticleVersion(1, hoursAgo(72), writer, "首次提交", listOf()),
                ArticleVersion(2, hoursAgo(48), writer, "按二审意见修改后重提", listOf())
            ),
            timeline = listOf(
                TimelineEntry(hoursAgo(72), writer, Role.WRITER, ActorAction.SUBMIT, ReviewStep.FIRST, "首次送审"),
                TimelineEntry(hoursAgo(60), l1, Role.REVIEW_L1, ActorAction.PASS, ReviewStep.FIRST, "转二审"),
                TimelineEntry(hoursAgo(48), l2, Role.REVIEW_L2, ActorAction.PASS, ReviewStep.SECOND, "转终审"),
                TimelineEntry(hoursAgo(36), finalSigner, Role.FINAL, ActorAction.SIGN, ReviewStep.FINAL, "同意签发")
            ),
            signers = signers,
            updatedAt = hoursAgo(36)
        )
    }

    // ------------------------------------------------------------------ tools

    private fun comment(
        id: String,
        author: String,
        role: Role,
        paragraphIndex: Int?,
        anchor: String?,
        text: String,
        status: CommentStatus,
        hoursAgo: Long
    ) = ReviewComment(
        id = id,
        author = author,
        authorRole = role,
        paragraphIndex = paragraphIndex,
        anchorText = anchor,
        text = text,
        createdAt = now() - hoursAgo * 3600_000L,
        status = status
    )

    private fun hoursAgo(hours: Long): Long = now() - hours * 3600_000L

    fun now(): Long = System.currentTimeMillis()
}

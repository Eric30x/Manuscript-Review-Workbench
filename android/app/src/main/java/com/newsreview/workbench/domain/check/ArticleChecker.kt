package com.newsreview.workbench.domain.check

import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.Evidence
import com.newsreview.workbench.domain.model.Finding
import com.newsreview.workbench.domain.model.FindingKind
import com.newsreview.workbench.domain.model.FindingLevel
import com.newsreview.workbench.domain.model.MaterialKind
import com.newsreview.workbench.domain.model.ReferenceMaterial
import com.newsreview.workbench.domain.model.TranscriptCue
import com.newsreview.workbench.domain.rule.AhoCorasick
import com.newsreview.workbench.domain.rule.WordLists

/** 已核对一致的正例。和"疑点"分开显示，审核人先看疑点。 */
data class Agreement(val title: String, val detail: String, val source: String)

data class CheckReport(
    val findings: List<Finding>,
    val agreements: List<Agreement>
) {
    val confirmRequired: Int get() = findings.count { it.needsConfirm && !it.ignored && !it.resolved }
}

/**
 * AI 辅助检查。三层结构，三审共用同一批证据：
 *
 *  L1 文字层：错别字 / 敏感词 / 规范用语 —— 词库多模式匹配（Aho–Corasick），提交前必须清掉。
 *  L2 一致性：人名、职务、时间、数字、统计口径与参考材料（通知 / 签到表 / 议程）交叉核对。
 *  L3 判断层：引语有没有丢掉限定条件、结论性表述有没有事实支撑、篇幅结构。
 *
 * 两条不变量：
 *  ① confidence = evidence 各项 delta 之和（保留两位），不是拍出来的数；
 *  ② 只有 needsConfirm 的条目会进"待核实"，进不去就绝不猜。
 */
class ArticleChecker(
    private val typoEngine: AhoCorasick,
    private val bannedEngine: AhoCorasick,
    private val styleEngine: AhoCorasick
) {

    companion object {
        private const val CONFIRM_THRESHOLD = 0.75

        fun default(): ArticleChecker = ArticleChecker(
            typoEngine = AhoCorasick(WordLists.typo.map { it.word }),
            bannedEngine = AhoCorasick(WordLists.banned.map { it.word }),
            styleEngine = AhoCorasick(WordLists.style.map { it.word })
        )

        private val NUMBER_PATTERN = Regex("""(\d{1,5})\s*(人|名|位|个|项|次|场|天)""")
        private val DATE_PATTERN = Regex("""\d{1,2}月\d{1,2}日""")
        /**
         * "职务 + 姓名"的表述。**必须带引导词**才认（邀请 / 出席 / 致辞 / 由 / 请 / 介绍）。
         * 不带引导词会让"与现场老师交流""观看老师操作仪器"这类句子被误判成嘉宾出场，
         * 报出"职务与议程不一致"的假警——宁可漏，不可乱报。
         */
        private val ROLE_NAME_PATTERN = Regex(
            """(?:邀请|出席|致辞|致词|介绍|由|请)\s*([\u4e00-\u9fa5]{2,12}(?:副书记|团委书记|书记|副部长|部长|副院长|院长|副主任|主任|处长|老师))\s*([\u4e00-\u9fa5]{2,3})(?=[，。、；：\s出致介发表表]|$)"""
        )
        private val QUOTE_PATTERN = Regex("""[「“]([^」”]{4,})[」”]""")
        private val SCOPE_WORDS = listOf("报名", "签到", "到场", "参加", "参与", "出席", "观摩")
        private val ON_SITE_SCOPES = listOf("到场", "参加", "参与", "出席", "观摩")
        private val ROLE_LEAD_IN = listOf("邀请", "会议由", "主持", "由", "出席", "请")
        private val DISCARDED_CONDITIONS = listOf("如果评估通过", "如果", "待评估", "初步", "可能", "预计")
        private const val PLACE_NAMES = "教学实验室|科研实验室|创客中心"
    }

    fun check(
        article: Article,
        materials: List<ReferenceMaterial>,
        transcript: List<TranscriptCue> = emptyList()
    ): CheckReport {
        val findings = ArrayList<Finding>()
        val agreements = ArrayList<Agreement>()

        article.paragraphs.forEachIndexed { index, text ->
            findings += checkWordLists(index, text)
        }
        findings += checkTimeAgreement(article, materials, agreements)
        findings += checkPlaceAgreement(article, materials, agreements)
        findings += checkScopeAgreement(article)
        findings += checkOnSiteNumber(article, materials)
        findings += checkRoleNames(article, materials)
        findings += checkQuotes(article, transcript)
        findings += checkStructure(article)

        return CheckReport(findings = dedupe(findings), agreements = agreements)
    }

    // ---------------------------------------------------------------- L1

    private fun checkWordLists(paragraphIndex: Int, text: String): List<Finding> {
        val result = ArrayList<Finding>()
        result += collect(paragraphIndex, text, typoEngine, WordLists.typo, FindingKind.TYPO, FindingLevel.L1, 0.9)
        result += collect(paragraphIndex, text, bannedEngine, WordLists.banned, FindingKind.BANNED_WORD, FindingLevel.L1, 0.0)
        result += collect(paragraphIndex, text, styleEngine, WordLists.style, FindingKind.STYLE, FindingLevel.L1, 0.72)
        return result
    }

    private fun collect(
        paragraphIndex: Int,
        text: String,
        engine: AhoCorasick,
        rules: List<WordLists.WordRule>,
        kind: FindingKind,
        level: FindingLevel,
        baseConfidence: Double
    ): List<Finding> {
        val ruleIndex = rules.withIndex().associate { it.value.word to it.index }
        val hits = engine.findAll(text)
            .sortedWith(compareByDescending<AhoCorasick.HitResult> { it.end - it.start }.thenBy { it.start })
            .fold(mutableListOf<AhoCorasick.HitResult>()) { acc, hit ->
                // 丢掉与已保留区间重叠的短词，避免同一处文字报两次
                if (acc.none { hit.start < it.end && it.start < hit.end }) acc.add(hit)
                acc
            }

        return hits.map { hit ->
            val rule = rules[ruleIndex.getValue(hit.pattern)]
            val span = text.substring(hit.start, hit.end)
            val evidence = when (kind) {
                FindingKind.TYPO -> listOf(
                    Evidence("命中错别字词库", 0.6, "内置错别字表", rule.word),
                    Evidence("词库给出规范写法", 0.3, "内置错别字表", rule.suggestion)
                )
                FindingKind.BANNED_WORD -> listOf(
                    Evidence("命中敏感词 / 禁用词库", 0.45, "内置词库", rule.word),
                    Evidence("该词条属于必须人工确认类别", 0.3, "词库规则说明", rule.note)
                )
                FindingKind.STYLE -> listOf(
                    Evidence("命中规范用语词库", 0.42, "内置规范用词表", rule.word),
                    Evidence("词库给出建议说法", 0.3, "内置规范用词表", rule.suggestion)
                )
                else -> listOf(Evidence("命中词库", 0.5, "内置词库", rule.word))
            }
            val confidence = round2(if (baseConfidence > 0.0) baseConfidence else evidence.sumOf { it.delta })
            Finding(
                id = "F-$kind-$paragraphIndex-${hit.start}",
                level = level,
                kind = kind,
                paragraphIndex = paragraphIndex,
                start = hit.start,
                end = hit.end,
                quote = span,
                title = when (kind) {
                    FindingKind.TYPO -> "错别字"
                    FindingKind.BANNED_WORD -> "敏感词 / 禁用词"
                    FindingKind.STYLE -> "规范用语"
                    else -> kind.label
                },
                detail = rule.note,
                evidence = evidence,
                confidence = confidence,
                suggestions = listOf(rule.suggestion),
                needsConfirm = kind == FindingKind.BANNED_WORD || confidence < CONFIRM_THRESHOLD
            )
        }
    }

    // ---------------------------------------------------------------- L2

    private fun checkTimeAgreement(
        article: Article,
        materials: List<ReferenceMaterial>,
        agreements: MutableList<Agreement>
    ): List<Finding> {
        val notice = materials.firstOrNull { it.kind == MaterialKind.NOTICE } ?: return emptyList()
        val dateInNotice = notice.lines.firstNotNullOfOrNull { DATE_PATTERN.find(it)?.value } ?: return emptyList()
        val body = article.paragraphs.joinToString("")
        if (body.contains(dateInNotice)) {
            agreements += Agreement("活动日期一致", "稿件正文的日期与${notice.name}一致：$dateInNotice", notice.name)
            return emptyList()
        }
        val dateInBody = DATE_PATTERN.find(body)?.value
        return listOf(
            finding(
                kind = FindingKind.CONSISTENCY,
                level = FindingLevel.L2,
                paragraphIndex = article.paragraphs.indexOfFirst { DATE_PATTERN.containsMatchIn(it) }.coerceAtLeast(0),
                quote = dateInBody ?: "（未写日期）",
                title = "日期与活动通知不一致",
                detail = "稿件日期为“${dateInBody ?: "未写明"}”，${notice.name}记录为“$dateInNotice”，请对照原始通知确认。",
                evidence = listOf(
                    Evidence("与通知记录不符", 0.4, notice.name, dateInNotice),
                    Evidence("稿件写为", 0.15, "稿件正文", dateInBody ?: "未写明"),
                    Evidence("材料不足时标记待核实", 0.1, "检查规则")
                ),
                suggestions = listOf("改为“$dateInNotice”", "保留但注明依据")
            )
        )
    }

    private fun checkPlaceAgreement(
        article: Article,
        materials: List<ReferenceMaterial>,
        agreements: MutableList<Agreement>
    ): List<Finding> {
        val notice = materials.firstOrNull { it.kind == MaterialKind.NOTICE } ?: return emptyList()
        val body = article.paragraphs.joinToString("")
        val places = Regex(PLACE_NAMES).findAll(body).map { it.value }.distinct().toList()
        if (places.size < 2) return emptyList()
        val inNotice = places.filter { place -> notice.lines.any { it.contains(place) } }
        if (inNotice.size == places.size) {
            agreements += Agreement("开放场所一致", "稿件写到的${places.joinToString("、")}与${notice.name}一致", notice.name)
            return emptyList()
        }
        val missing = places - inNotice.toSet()
        return listOf(
            finding(
                kind = FindingKind.CONSISTENCY,
                level = FindingLevel.L2,
                paragraphIndex = article.paragraphs.indexOfFirst { it.contains(missing.first()) }.coerceAtLeast(0),
                quote = missing.joinToString("、"),
                title = "开放场所在通知中没有对应记录",
                detail = "${notice.name}只写到了${inNotice.joinToString("、").ifEmpty { "（未列出场所）" }}，稿件多出：${missing.joinToString("、")}。",
                evidence = listOf(
                    Evidence("通知中未出现该场所", 0.35, notice.name, notice.lines.lastOrNull() ?: ""),
                    Evidence("稿件写为", 0.2, "稿件正文", missing.joinToString("、")),
                    Evidence("材料不足时标记待核实", 0.12, "检查规则")
                )
            )
        )
    }

    /**
     * 标题、正文、推送文案的统计口径必须一致。
     * 这是最容易挂网翻车的一类：标题写"报名情况"，推送写"200 人到场"。
     */
    private fun checkScopeAgreement(article: Article): List<Finding> {
        val titleScope = SCOPE_WORDS.firstOrNull { article.title.contains(it) }
        val bodyScope = SCOPE_WORDS.firstOrNull { article.paragraphs.joinToString("").contains(it) }
        val pushScope = SCOPE_WORDS.firstOrNull { article.pushText.contains(it) }
        val distinct = setOfNotNull(titleScope, bodyScope, pushScope)
        if (distinct.size <= 1) return emptyList()

        val scopes = listOfNotNull(
            titleScope?.let { "标题：$it" },
            bodyScope?.let { "正文：$it" },
            pushScope?.let { "推送文案：$it" }
        )
        return listOf(
            finding(
                kind = FindingKind.CONSISTENCY,
                level = FindingLevel.L2,
                paragraphIndex = 0,
                quote = scopes.joinToString(" / "),
                title = "标题、正文与推送文案的统计口径不一致",
                detail = "三处分别用了不同的口径（${distinct.joinToString("、")}）。推送文案会独立出现在订阅号里，读者看不到正文语境，口径不同会被当成两个数。",
                evidence = listOf(
                    Evidence("三处口径不同", 0.45, "稿件三要素", scopes.joinToString(" / ")),
                    Evidence("推送文案独立发布，读者看不到正文语境", 0.15, "发布渠道规则"),
                    Evidence("需确认是否可用同一口径", 0.1, "检查规则")
                ),
                suggestions = listOf("统一改为同一口径", "在正文写明报名与到场的区别")
            )
        )
    }

    /** 稿件里的"到场类"数字必须和签到表对得上；对不上就说明是哪个口径。 */
    private fun checkOnSiteNumber(article: Article, materials: List<ReferenceMaterial>): List<Finding> {
        val signIn = materials.firstOrNull { it.kind == MaterialKind.SIGN_IN } ?: return emptyList()
        val onSiteRecord = numberNear(signIn.lines, "签到")
        val enrolRecord = numberNear(signIn.lines, "报名")
        val paragraphIndex = article.paragraphs.indexOfFirst { para ->
            ON_SITE_SCOPES.any { para.contains(it) } && NUMBER_PATTERN.containsMatchIn(para)
        }
        if (paragraphIndex < 0 || onSiteRecord == null) return emptyList()
        val bodyNumber = NUMBER_PATTERN.find(article.paragraphs[paragraphIndex])?.value ?: return emptyList()
        val bodyValue = Regex("""\d{1,5}""").find(bodyNumber)?.value ?: return emptyList()
        if (bodyValue == onSiteRecord) {
            return emptyList()
        }

        val evidence = mutableListOf(
            Evidence("签到表记录与稿件写法不一致", 0.3, signIn.name, "签到 $onSiteRecord 人"),
            Evidence("稿件写为", 0.15, "稿件正文", bodyNumber)
        )
        if (enrolRecord != null) {
            evidence += Evidence("两者统计口径可能不同（报名 / 签到）", 0.15, signIn.name, "报名 $enrolRecord 人")
        }
        evidence += Evidence("材料不足或口径不清时标记待核实", 0.1, "检查规则")

        return listOf(
            finding(
                kind = FindingKind.CONSISTENCY,
                level = FindingLevel.L2,
                paragraphIndex = paragraphIndex,
                quote = "$bodyNumber（稿件）vs 签到 $onSiteRecord 人（签到表）",
                title = "到场人数与签到记录不一致",
                detail = "稿件写“$bodyNumber”，${signIn.name}记录“签到 $onSiteRecord 人”" +
                    (enrolRecord?.let { "、报名 $it 人" } ?: "") + "。三个口径都可能对，但必须写明用的是哪一个。",
                evidence = evidence,
                suggestions = listOf("改为“签到 $onSiteRecord 人”", "写明“报名 ${enrolRecord ?: "—"} 人、签到 $onSiteRecord 人”")
            )
        )
    }

    private fun checkRoleNames(article: Article, materials: List<ReferenceMaterial>): List<Finding> {
        val agenda = materials.firstOrNull { it.kind == MaterialKind.AGENDA } ?: return emptyList()
        val result = ArrayList<Finding>()
        article.paragraphs.forEachIndexed { index, text ->
            ROLE_NAME_PATTERN.findAll(text).forEach { match ->
                val role = cleanRole(match.groupValues[1])
                val name = match.groupValues[2]
                val record = agenda.lines.firstOrNull { it.contains(name) } ?: return@forEach
                val recordRole = Regex(
                    """[\u4e00-\u9fa5]{2,12}(?:副书记|团委书记|书记|副部长|部长|副院长|院长|副主任|主任|处长|老师)"""
                ).find(record)?.value ?: return@forEach
                if (role != recordRole && !role.endsWith(recordRole) && !recordRole.endsWith(role)) {
                    result += finding(
                        kind = FindingKind.CONSISTENCY,
                        level = FindingLevel.L2,
                        paragraphIndex = index,
                        quote = "$role $name",
                        title = "嘉宾职务与活动议程不一致",
                        detail = "稿件写“$role $name”，${agenda.name}记录为“$recordRole”。人名与职务一旦对不上，属于必须核实项。",
                        evidence = listOf(
                            Evidence("议程记录与稿件表述不同", 0.35, agenda.name, recordRole),
                            Evidence("稿件写为", 0.15, "稿件正文", "$role $name"),
                            Evidence("以活动方确认的信息为准", 0.1, "检查规则")
                        ),
                        suggestions = listOf("改为“$recordRole $name”", "向活动方确认后保留")
                    )
                }
            }
        }
        return result
    }

    // ---------------------------------------------------------------- L3

    private fun checkQuotes(article: Article, transcript: List<TranscriptCue>): List<Finding> {
        if (transcript.isEmpty()) return emptyList()
        val result = ArrayList<Finding>()
        article.paragraphs.forEachIndexed { index, text ->
            QUOTE_PATTERN.findAll(text).forEach { match ->
                val quote = match.groupValues[1]
                val cue = transcript.minByOrNull { distance(quote, it.text) } ?: return@forEach
                if (distance(quote, cue.text) > 0.6) return@forEach
                val condition = DISCARDED_CONDITIONS.firstOrNull { cue.text.contains(it) && !quote.contains(it) }
                if (condition != null) {
                    result += finding(
                        kind = FindingKind.FACT_SUPPORT,
                        level = FindingLevel.L3,
                        paragraphIndex = index,
                        quote = quote,
                        title = "引语可能丢掉了限定条件",
                        detail = "录音原话里有“$condition”，稿件引用时没有保留。限定条件被省掉后，读者会当成已确定的事实。",
                        evidence = listOf(
                            Evidence("录音原话含限定条件", 0.4, "采访录音转写", cue.text),
                            Evidence("稿件引语中未保留该条件", 0.2, "稿件正文", quote),
                            Evidence("已定位到录音片段", 0.1, "时间戳 ${timeRange(cue)}")
                        ),
                        suggestions = listOf("补回“$condition”", "改为间接引述")
                    )
                } else {
                    result += finding(
                        kind = FindingKind.FACT_SUPPORT,
                        level = FindingLevel.L3,
                        paragraphIndex = index,
                        quote = quote,
                        title = "引语已定位到录音",
                        detail = "在录音 ${timeRange(cue)} 找到对应原话，语义一致，引用时保留了原意。",
                        evidence = listOf(
                            Evidence("录音与引语语义一致", 0.5, "采访录音转写", cue.text),
                            Evidence("字面差异", 0.35, "相似度", "%.2f".format(1 - distance(quote, cue.text)))
                        ),
                        needsConfirmOverride = false
                    )
                }
            }
        }
        return result
    }

    private fun checkStructure(article: Article): List<Finding> {
        val result = ArrayList<Finding>()
        if (article.title.length > 24) {
            result += finding(
                kind = FindingKind.STRUCTURE,
                level = FindingLevel.L3,
                paragraphIndex = 0,
                quote = article.title,
                title = "标题偏长",
                detail = "标题共 ${article.title.length} 字，移动端推送列表里会被截断，建议压到 24 字以内。",
                evidence = listOf(
                    Evidence("超出建议长度", 0.4, "结构规则", "> 24 字"),
                    Evidence("移动端列表展示宽度有限", 0.2, "发布渠道规则")
                ),
                suggestions = listOf("压缩到 24 字以内")
            )
        }
        if (article.paragraphs.size < 3) {
            result += finding(
                kind = FindingKind.STRUCTURE,
                level = FindingLevel.L3,
                paragraphIndex = 0,
                quote = "${article.paragraphs.size} 段",
                title = "段落偏少",
                detail = "活动类稿件通常要交代时间地点、过程与结果，当前只有 ${article.paragraphs.size} 段。",
                evidence = listOf(
                    Evidence("段落数低于惯例", 0.35, "结构规则", "< 3 段"),
                    Evidence("缺要素时读者无法还原现场", 0.15, "检查规则")
                )
            )
        }
        return result
    }

    // ---------------------------------------------------------------- helpers

    private fun finding(
        kind: FindingKind,
        level: FindingLevel,
        paragraphIndex: Int,
        quote: String,
        title: String,
        detail: String,
        evidence: List<Evidence>,
        suggestions: List<String> = emptyList(),
        needsConfirmOverride: Boolean? = null
    ): Finding {
        val confidence = round2(evidence.sumOf { it.delta }.coerceIn(0.0, 1.0))
        return Finding(
            id = "F-$kind-$paragraphIndex-${quote.hashCode()}",
            level = level,
            kind = kind,
            paragraphIndex = paragraphIndex,
            start = -1,
            end = -1,
            quote = quote,
            title = title,
            detail = detail,
            evidence = evidence,
            confidence = confidence,
            suggestions = suggestions,
            needsConfirm = needsConfirmOverride ?: (confidence < CONFIRM_THRESHOLD)
        )
    }

    private fun dedupe(findings: List<Finding>): List<Finding> {
        val seen = HashSet<String>()
        return findings.filter { seen.add(it.id + "|" + it.quote) }
    }

    private fun cleanRole(raw: String): String =
        ROLE_LEAD_IN.map { raw.substringAfterLast(it) }.filter { it.isNotEmpty() }.minByOrNull { it.length } ?: raw

    private fun numberNear(lines: List<String>, keyword: String): String? =
        lines.firstOrNull { it.contains(keyword) }
            ?.let { Regex("""\d{1,5}""").find(it)?.value }

    private fun round2(value: Double): Double = Math.round(value * 100) / 100.0

    private fun timeRange(cue: TranscriptCue): String {
        fun fmt(ms: Long): String {
            val total = ms / 1000
            return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
        }
        return "${fmt(cue.startMs)} - ${fmt(cue.endMs)}"
    }

    /** 简单的字符重合度，用来把引语定位到最接近的那句录音。 */
    private fun distance(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 1.0
        val short = if (a.length <= b.length) a else b
        val long = if (a.length <= b.length) b else a
        val hit = short.count { long.contains(it) }
        return 1.0 - hit.toDouble() / short.length
    }
}

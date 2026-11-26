package com.newsreview.workbench.domain.rule

/**
 * 词库。管理员可维护（MVP 阶段先内置，界面见"词库"页）。
 *
 * 三类：
 *  - typo    错别字表：错 → 对
 *  - banned  敏感词 / 禁用词：需要人工确认，AI 不判定，只提示
 *  - style   规范用语：口语或不适合公开稿件的说法 → 建议说法
 */
object WordLists {

    /** 词条：命中词、建议替换、说明。matchMode = EXACT 表示整词命中才提示。 */
    data class WordRule(
        val word: String,
        val suggestion: String,
        val note: String,
        val wholeWord: Boolean = false
    )

    val typo: List<WordRule> = listOf(
        WordRule("再接再励", "再接再厉", "常见错别字"),
        WordRule("迫不急待", "迫不及待", "常见错别字"),
        WordRule("相形见拙", "相形见绌", "常见错别字"),
        WordRule("提纲契领", "提纲挈领", "常见错别字"),
        WordRule("再所不惜", "在所不惜", "常见错别字"),
        WordRule("精采", "精彩", "常见错别字"),
        WordRule("反应热烈", "反响热烈", "“反应”多指生理或化学变化，稿件中宜用“反响”"),
        WordRule("规化", "规划", "常见错别字"),
        WordRule("深收师生欢迎", "深受师生欢迎", "常见错别字"),
        WordRule("渡过难关", "渡过难关", "此处用“度过”更规范（时间意义）")
    )

    val banned: List<WordRule> = listOf(
        WordRule("内部资料", "删去或改为可公开表述", "涉密风险，需人工确认是否可对外发布"),
        WordRule("内部通知", "改为可公开表述", "涉密风险，需人工确认"),
        WordRule("会议纪要", "改为活动概况", "内部材料表述，公开稿件需改写"),
        WordRule("批示", "改为要求 / 部署", "内部流程用语，公开稿件需改写"),
        WordRule("涉密", "删去", "不宜出现在公开稿件中"),
        WordRule("机密", "删去", "不宜出现在公开稿件中"),
        WordRule("史上最", "改为具体表述", "宣传绝对化用语，新闻稿不宜使用"),
        WordRule("世界领先", "改为可核查的表述并注明依据", "绝对化表述，需有依据"),
        WordRule("最佳", "改为具体评价", "绝对化表述，需有依据"),
        WordRule("独一无二", "改为具体表述", "绝对化表述，需有依据"),
        WordRule("国家级", "注明具体批复或依据", "需有明确依据，否则不宜使用"),
        WordRule("绝对", "改为具体表述", "绝对化表述，需有依据")
    )

    val style: List<WordRule> = listOf(
        WordRule("亲自", "删去", "“领导亲自出席”等表述宜精简为“出席”"),
        WordRule("莅临", "出席 / 到访", "平级或校内活动通常用“出席”"),
        WordRule("圆满成功", "顺利举行", "活动类稿件宜用中性表述"),
        WordRule("喜讯", "消息", "正式稿件宜用中性词"),
        WordRule("同学们", "学生 / 与会学生", "正式稿件避免口语化称呼"),
        WordRule("搞", "开展", "口语词，书面稿宜用“开展”"),
        WordRule("抓", "推进", "口语词，书面稿宜用“推进”"),
        WordRule("贵校", "本校", "校内稿件不宜自称“贵校”")
    )

    val all: List<WordRule> = typo + banned + style

    /** 词库总条数，界面与文档会引用这个数字。 */
    val size: Int get() = all.size
}

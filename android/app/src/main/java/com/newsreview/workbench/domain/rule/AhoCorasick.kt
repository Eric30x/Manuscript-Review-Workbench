package com.newsreview.workbench.domain.rule

/**
 * Aho–Corasick 多模式匹配。
 *
 * 词库有几百条时，逐条 `text.contains(word)` 是 O(词数 × 文本长度)；本类是
 * 一次线性扫描 O(文本长度 + 命中数)，并且能直接给出命中区间，界面靠这个区间画红线。
 */
class AhoCorasick(patterns: Collection<String>) {

    private class Node {
        val next = HashMap<Char, Int>(4)
        var fail = 0
        val outputs = ArrayList<Int>(2)
    }

    val patternList: List<String> = patterns.toList()

    private val nodes = ArrayList<Node>().also { it.add(Node()) }

    init {
        for ((idx, pattern) in patternList.withIndex()) {
            if (pattern.isEmpty()) continue
            var cur = 0
            for (ch in pattern) {
                val nxt = nodes[cur].next[ch]
                cur = if (nxt != null) nxt else {
                    nodes.add(Node())
                    val created = nodes.size - 1
                    nodes[cur].next[ch] = created
                    created
                }
            }
            nodes[cur].outputs.add(idx)
        }

        val queue = ArrayDeque<Int>()
        for (child in nodes[0].next.values) {
            nodes[child].fail = 0
            queue.addLast(child)
        }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for ((ch, nxt) in nodes[cur].next) {
                var f = nodes[cur].fail
                while (f != 0 && !nodes[f].next.containsKey(ch)) f = nodes[f].fail
                val candidate = nodes[f].next[ch]
                nodes[nxt].fail = if (candidate != null && candidate != nxt) candidate else 0
                nodes[nxt].outputs.addAll(nodes[nodes[nxt].fail].outputs)
                queue.addLast(nxt)
            }
        }
    }

    fun findAll(text: String): List<HitResult> {
        if (text.isEmpty()) return emptyList()
        val hits = ArrayList<HitResult>()
        var cur = 0
        for (i in text.indices) {
            val ch = text[i]
            while (cur != 0 && !nodes[cur].next.containsKey(ch)) cur = nodes[cur].fail
            cur = nodes[cur].next[ch] ?: 0
            for (idx in nodes[cur].outputs) {
                val len = patternList[idx].length
                hits.add(HitResult(patternList[idx], i - len + 1, i + 1))
            }
        }
        return hits
    }

    fun containsAny(text: String): Boolean = findAll(text).isNotEmpty()

    /** 命中区间为左闭右开 [start, end)。 */
    data class HitResult(val pattern: String, val start: Int, val end: Int)
}

package com.newsreview.workbench.data

import com.newsreview.workbench.domain.model.*
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** An uploaded file is streamed from its Android document URI. */
data class WordUpload(val name: String, val open: () -> InputStream)

class WorkbenchApi(private val baseUrl: String) {
    private var token: String? = null

    fun login(role: Role, password: String) {
        token = json("/api/session", JSONObject().put("role", role.name).put("password", password))
            .getString("token")
    }

    fun articles(): List<Article> = json("/api/manuscripts").getJSONArray("articles").objects().map { item ->
        val latest = item.optInt("latest_version", 0)
        val count = maxOf(item.optInt("version_count", 0), latest)
        Article(
            id = item.getString("article_id"),
            title = item.getString("title"),
            pushText = "",
            paragraphs = emptyList(),
            // 列表接口只给版本号、不给正文：按版本数造占位版本，
            // 首页列表的「第 N 版」与 currentVersion 才不会退化成「第 0 版」。
            versions = (1..count).map { ArticleVersion(it, 0L, "写稿人", "", emptyList()) },
            status = ArticleStatus.valueOf(item.getString("status")),
            currentStep = item.text("current_step")?.let(ReviewStep::valueOf),
            channel = item.text("channel")?.let(Channel::valueOf),
            updatedAt = item.timestamp("updated_at")
        )
    }

    fun article(id: String): Article = readArticle(json("/api/manuscripts/$id"))

    fun originalParagraph(id: String, version: Int, index: Int): String =
        json("/api/manuscripts/$id/versions/$version/paragraphs").getString("P${index + 1}")

    fun upload(manuscript: WordUpload, materials: List<WordUpload>, id: String? = null,
               title: String? = null, note: String? = null): String {
        val boundary = "workbench-${UUID.randomUUID()}"
        val path = if (id == null) "/api/manuscripts" else "/api/manuscripts/$id/versions"
        return request(path, "POST") { connection ->
            connection.doOutput = true
            connection.setChunkedStreamingMode(64 * 1024)
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.buffered().use { output ->
                fun write(text: String) = output.write(text.toByteArray(Charsets.UTF_8))
                fun field(name: String, value: String) {
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                fun file(field: String, upload: WordUpload) {
                    val name = upload.name.replace(Regex("[\\r\\n\"\\\\/]"), "_")
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$name\"\r\n")
                    write("Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\n")
                    upload.open().use { it.copyTo(output) }
                    write("\r\n")
                }
                title?.let { field("title", it) }
                note?.let { field("note", it) }
                file("manuscript", manuscript)
                materials.forEach { file("materials", it) }
                write("--$boundary--\r\n")
            }
            JSONObject(connection.body()).getString("article_id")
        }
    }

    fun check(article: Article) {
        json("/api/manuscripts/${article.id}/versions/${article.currentVersion!!.no}/check", JSONObject())
    }

    fun action(article: Article, action: String, note: String = "", paragraph: Int? = null,
               quote: String? = null, channel: Channel? = null): Article {
        val body = JSONObject()
            .put("action", action)
            .put("version", article.currentVersion!!.no)
            .put("note", note)
        paragraph?.let { body.put("paragraph_id", "P${it + 1}") }
        quote?.let { body.put("quote", it) }
        channel?.let { body.put("channel", it.name) }
        return readArticle(json("/api/manuscripts/${article.id}/actions", body))
    }

    fun downloadSigned(article: Article, file: File) {
        request("/api/manuscripts/${article.id}/signed", "GET") { connection ->
            connection.checkStatus()
            try {
                connection.inputStream.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (ex: Exception) {
                file.delete()
                throw ex
            }
        }
    }

    private fun json(path: String, payload: JSONObject? = null): JSONObject =
        request(path, if (payload == null) "GET" else "POST") { connection ->
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            JSONObject(connection.body())
        }

    private fun <T> request(path: String, method: String, call: (HttpURLConnection) -> T): T {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 150_000
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        return try {
            call(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.body(): String {
        checkStatus()
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun HttpURLConnection.checkStatus() {
        val code = responseCode
        if (code in 200..299) return
        val raw = errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        val detail = raw?.let { runCatching { JSONObject(it).optString("detail") }.getOrNull() }
        throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "后端请求失败（HTTP $code）")
    }

    private fun readArticle(data: JSONObject): Article {
        val id = data.getString("article_id")
        val latest = data.getInt("latest_version")
        val source = data.getJSONObject("paragraphs")
        val paragraphs = (1..source.length()).map { source.getString("P$it") }
        val timeline = data.getJSONArray("timeline").objects()
        val lastReturn = timeline.lastOrNull { it.getString("action") == "RETURN" }
        val versions = data.getJSONArray("versions").objects()
        val version = versions.first { it.getInt("version") == latest }
        val signers = data.optJSONObject("signers")
        return Article(
            id = id,
            title = version.text("title") ?: data.getString("title"),
            pushText = "",
            paragraphs = paragraphs,
            status = ArticleStatus.valueOf(data.getString("status")),
            channel = data.text("channel")?.let(Channel::valueOf),
            currentStep = data.text("current_step")?.let(ReviewStep::valueOf),
            returnedAt = lastReturn?.text("step")?.let(ReviewStep::valueOf),
            returnedBy = lastReturn?.text("actor"),
            returnNote = data.text("return_note"),
            passedSteps = data.getJSONArray("passed_steps").let { array ->
                (0 until array.length()).map { ReviewStep.valueOf(array.getString(it)) }.toSet()
            },
            versions = versions.map {
                val number = it.getInt("version")
                ArticleVersion(number, it.timestamp("created_at"), "写稿人", it.getString("note"),
                    if (number == latest) paragraphs else emptyList())
            },
            findings = data.getJSONArray("issues").objects().mapIndexedNotNull { index, item ->
                val paragraph = item.paragraphIndex() ?: return@mapIndexedNotNull null
                val quote = item.getString("quote")
                val start = paragraphs.getOrNull(paragraph)?.indexOf(quote) ?: -1
                if (start < 0) return@mapIndexedNotNull null
                Finding(
                    id = "$latest-$index", level = FindingLevel.L2, kind = FindingKind.CONSISTENCY,
                    paragraphIndex = paragraph, start = start, end = start + quote.length,
                    quote = quote, title = "人数口径待核实", detail = item.getString("reason"),
                    evidence = item.getJSONArray("evidence").objects().map {
                        Evidence("材料摘录", 0.0, it.getString("source"), it.getString("quote"))
                    },
                    confidence = 0.0, suggestions = listOf(item.getString("suggestion")), needsConfirm = true
                )
            },
            comments = data.getJSONArray("comments").objects().map {
                ReviewComment(it.getString("id"), it.getString("actor"), Role.valueOf(it.getString("role")),
                    it.paragraphIndex(), it.text("quote"), it.getString("note"), it.timestamp("at"),
                    sourceVersion = it.getInt("version"))
            },
            timeline = timeline.map {
                TimelineEntry(it.timestamp("at"), it.getString("actor"), Role.valueOf(it.getString("role")),
                    ActorAction.valueOf(it.getString("action")), it.text("step")?.let(ReviewStep::valueOf),
                    it.text("note")?.takeIf(String::isNotBlank))
            },
            signers = signers?.let { Signers(it.getString("FIRST"), it.getString("SECOND"), it.getString("FINAL")) },
            attachments = version.getJSONArray("materials").objects().map {
                Attachment(it.getString("name"), "参考材料", "${it.getLong("size") / 1024} KB")
            },
            updatedAt = data.timestamp("updated_at"),
            serverChecked = version.optBoolean("checked"),
            reviewedReturnNote = data.optJSONObject("reviewed_return")?.text("note"),
            allowedActions = data.getJSONArray("allowed_actions").let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }
        )
    }

    private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
    private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name)
    private fun JSONObject.timestamp(name: String): Long =
        text(name)?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: 0L
    private fun JSONObject.paragraphIndex(): Int? = text("paragraph_id")
        ?.removePrefix("P")?.toIntOrNull()?.minus(1)
}

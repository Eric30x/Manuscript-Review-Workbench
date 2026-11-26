package com.newsreview.workbench.domain.docx

import android.util.Xml
import com.newsreview.workbench.domain.model.RevisionKind
import com.newsreview.workbench.domain.model.TrackedRevision
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipInputStream

data class DocxContent(
    val title: String?,
    val paragraphs: List<String>,
    val revisions: List<TrackedRevision>
)

/**
 * .docx 解析。
 *
 * 选型说明：Android 上引 Apache POI 需要拖进 xmlbeans 等一大堆依赖，还要开脱糖，
 * 很容易在真机上崩。而 .docx 本身就是"zip + word/document.xml"，用系统自带的
 * java.util.zip 与 XmlPullParser 解析正文段落足够稳，零依赖、体积为零。
 * 代价：不解析样式、表格、页眉页脚（MVP 用不到，README 的"已知限制"里写明了）。
 */
object DocxReader {

    private const val DOCUMENT_ENTRY = "word/document.xml"

    fun read(input: InputStream): DocxContent {
        val xml = ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            var found: String? = null
            while (entry != null) {
                if (entry.name == DOCUMENT_ENTRY) {
                    found = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                entry = zip.nextEntry
            }
            found
        } ?: throw IllegalArgumentException("不是有效的 .docx 文件（缺少 $DOCUMENT_ENTRY）")
        return parse(xml)
    }

    private fun parse(xml: String): DocxContent {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        val paragraphs = ArrayList<String>()
        val revisions = ArrayList<TrackedRevision>()
        val paragraph = StringBuilder()
        val insertBuffer = StringBuilder()
        val deleteBuffer = StringBuilder()

        var inText = false
        var inInsert = false
        var inDelete = false
        var inInstruction = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> paragraph.setLength(0)
                    "t" -> inText = true
                    "delText" -> {
                        inText = true
                        inDelete = true
                    }
                    "instrText" -> inInstruction = true
                    "ins" -> inInsert = true
                    "del" -> inDelete = true
                    "br" -> paragraph.append('\n')
                    "tab" -> paragraph.append('\t')
                }

                XmlPullParser.TEXT -> if (inText && !inInstruction) {
                    // Word 自带修订默认接受：正文保留插入的文字，删除的文字不进正文
                    if (!inDelete) paragraph.append(parser.text)
                    if (inInsert) insertBuffer.append(parser.text)
                    if (inDelete) deleteBuffer.append(parser.text)
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "delText" -> {
                        inText = false
                        inDelete = false
                    }
                    "instrText" -> inInstruction = false
                    "ins" -> {
                        inInsert = false
                        if (insertBuffer.isNotBlank()) {
                            revisions.add(TrackedRevision(RevisionKind.INSERT, insertBuffer.toString().trim()))
                            insertBuffer.setLength(0)
                        }
                    }
                    "del" -> {
                        inDelete = false
                        if (deleteBuffer.isNotBlank()) {
                            revisions.add(TrackedRevision(RevisionKind.DELETE, deleteBuffer.toString().trim()))
                            deleteBuffer.setLength(0)
                        }
                    }
                    "p" -> {
                        val text = paragraph.toString().trim()
                        if (text.isNotEmpty()) paragraphs.add(text)
                        paragraph.setLength(0)
                    }
                }
            }
            event = parser.next()
        }

        val title = paragraphs.firstOrNull()
            ?.takeIf { paragraphs.size > 1 && it.length <= 40 && !it.endsWith("。") && !it.endsWith("；") }
        return DocxContent(
            title = title,
            paragraphs = if (title == null) paragraphs else paragraphs.drop(1),
            revisions = revisions
        )
    }
}

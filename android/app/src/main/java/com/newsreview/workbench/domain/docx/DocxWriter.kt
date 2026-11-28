package com.newsreview.workbench.domain.docx

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * .docx 写出：导出"签发版"。只写出正文段落 + 文末三审署名行 + 一行版本信息。
 *
 * 与 DocxReader 对称，同样零依赖。产出的包结构是 OOXML 的最小可用集，
 * Word / WPS / 手机自带预览都能打开。
 */
object DocxWriter {

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    fun write(
        target: File,
        title: String,
        paragraphs: List<String>,
        signerLine: String?,
        metaLine: String?
    ): File {
        target.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            put(zip, "[Content_Types].xml", CONTENT_TYPES)
            put(zip, "_rels/.rels", RELS)
            put(zip, "word/document.xml", documentXml(title, paragraphs, signerLine, metaLine))
        }
        return target
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun documentXml(title: String, paragraphs: List<String>, signerLine: String?, metaLine: String?): String {
        val body = StringBuilder()
        if (title.isNotBlank()) body.append(paragraph(title, bold = true))
        paragraphs.filter { it.isNotBlank() }.forEach { body.append(paragraph(it, bold = false)) }
        if (!signerLine.isNullOrBlank()) body.append(paragraph(signerLine, bold = true))
        if (!metaLine.isNullOrBlank()) body.append(paragraph(metaLine, bold = false))
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>
$body<w:sectPr/>
</w:body>
</w:document>"""
    }

    private fun paragraph(text: String, bold: Boolean): String {
        val runProps = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
        return "<w:p><w:r>$runProps<w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>\n"
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

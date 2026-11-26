package com.newsreview.workbench.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把导出的"签发版 .docx"交给系统分享（微信 / 邮件 / 网盘）。
 *
 * 用 FileProvider 授权单个文件，不申请任何存储权限；稿件平时只待在本机，
 * 只有用户主动点"分享"时才会离开设备。
 */
fun shareDocument(context: Context, file: File, chooserTitle: String = "分享签发版") {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}


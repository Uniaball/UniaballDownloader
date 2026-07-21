package com.uniaball.downloader.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.uniaball.downloader.data.repository.UniaballRepository

object DownloadUtil {
    /**
     * 将 GitHub 原始下载 URL 通过 gh-proxy.com 镜像拼接后，
     * 通过 ACTION_VIEW Intent 调起系统浏览器下载。
     */
    fun openDownload(context: Context, rawUrl: String) {
        if (rawUrl.isBlank()) {
            Toast.makeText(context, "下载链接为空", Toast.LENGTH_SHORT).show()
            return
        }
        val mirrored = UniaballRepository.mirror(rawUrl)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mirrored)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "无法打开下载链接", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 应用内下载：不跳转浏览器，直接使用 OkHttp 下载到本地，
     * 通过 InAppDownloadManager 管理进度、速率和状态。
     */
    fun startInAppDownload(rawUrl: String, fileName: String) {
        if (rawUrl.isBlank()) return
        val mirrored = UniaballRepository.mirror(rawUrl)
        InAppDownloadManager.startDownload(mirrored, fileName)
    }
}

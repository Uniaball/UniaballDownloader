package com.uniaball.downloader.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val url: String = "",
    val fileName: String = "",
    val filePath: String = "",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val error: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

object InAppDownloadManager {

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var downloadDir: File? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val speedSamples = mutableListOf<Pair<Long, Long>>()

    fun init(context: Context) {
        downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "UniaballDownloader")
        downloadDir?.mkdirs()
    }

    fun startDownload(url: String, fileName: String) {
        cancelDownload()
        _downloadState.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            url = url,
            fileName = fileName
        )
        speedSamples.clear()

        downloadJob = scope.launch {
            var response: okhttp3.Response? = null
            try {
                val dir = downloadDir ?: throw IllegalStateException("Download directory not initialized")
                val file = File(dir, sanitizeFileName(fileName))
                val request = Request.Builder().url(url).build()
                response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    _downloadState.value = _downloadState.value.copy(
                        status = DownloadStatus.FAILED,
                        error = "HTTP ${response.code}: ${response.message}"
                    )
                    return@launch
                }

                val body = response.body ?: run {
                    _downloadState.value = _downloadState.value.copy(
                        status = DownloadStatus.FAILED,
                        error = "响应体为空"
                    )
                    return@launch
                }

                val contentLength = body.contentLength()
                val source = body.source()

                _downloadState.value = _downloadState.value.copy(
                    totalBytes = contentLength,
                    filePath = file.absolutePath
                )

                var totalRead = 0L
                file.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var lastReportTime = System.currentTimeMillis()
                    var bytesRead: Int
                    while (isActive) {
                        bytesRead = source.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 200) {
                            lastReportTime = now
                            val speed = updateSpeed(now, totalRead)
                            _downloadState.value = _downloadState.value.copy(
                                downloadedBytes = totalRead,
                                speedBytesPerSec = speed
                            )
                        }
                    }
                }

                if (!isActive) {
                    file.delete()
                    _downloadState.value = _downloadState.value.copy(
                        status = DownloadStatus.CANCELLED
                    )
                    return@launch
                }

                _downloadState.value = _downloadState.value.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = totalRead,
                    speedBytesPerSec = 0L
                )
            } catch (e: Exception) {
                if (isActive) {
                    _downloadState.value = _downloadState.value.copy(
                        status = DownloadStatus.FAILED,
                        error = e.message ?: "下载失败"
                    )
                }
            } finally {
                response?.close()
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        speedSamples.clear()
    }

    fun resetState() {
        cancelDownload()
        _downloadState.value = DownloadState()
    }

    fun openFile(context: Context) {
        val state = _downloadState.value
        if (state.status != DownloadStatus.COMPLETED || state.filePath.isBlank()) return
        val file = File(state.filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = state.fileName.let { name ->
            when {
                name.endsWith(".apk", ignoreCase = true) -> "application/vnd.android.package-archive"
                name.endsWith(".zip", ignoreCase = true) -> "application/zip"
                name.endsWith(".tar.gz", ignoreCase = true) || name.endsWith(".tgz", ignoreCase = true) -> "application/gzip"
                name.endsWith(".jar", ignoreCase = true) -> "application/java-archive"
                else -> "*/*"
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }
    }

    fun openFileLocation(context: Context) {
        val state = _downloadState.value
        if (state.status != DownloadStatus.COMPLETED || state.filePath.isBlank()) return
        val file = File(state.filePath)
        if (!file.exists()) return
        val parentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(parentUri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "文件已下载到: ${file.absolutePath}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(Intent.createChooser(shareIntent, "文件位置")) }
        }
    }

    private fun updateSpeed(now: Long, totalBytes: Long): Long {
        speedSamples.add(now to totalBytes)
        speedSamples.removeAll { (t, _) -> now - t > 2000L }
        if (speedSamples.size < 2) return 0L
        val first = speedSamples.first()
        val last = speedSamples.last()
        val deltaSec = (last.first - first.first) / 1000.0
        if (deltaSec <= 0) return 0L
        return ((last.second - first.second) / deltaSec).toLong()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}

package com.uniaball.downloader.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.uniaball.downloader.data.repository.UniaballRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLongArray

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

    // 多线程下载分片数
    private const val MULTI_THREAD_COUNT = 3

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val speedSamples = mutableListOf<Pair<Long, Long>>()

    fun init(context: Context) {
        downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "UniaballDownloader"
        )
        downloadDir?.mkdirs()
    }

    fun startDownload(url: String, fileName: String) {
        cancelDownload()
        LogUtil.i("Download", "开始下载: $fileName")
        _downloadState.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            url = url,
            fileName = fileName
        )
        speedSamples.clear()

        downloadJob = scope.launch {
            var dir: File? = null
            try {
                dir = downloadDir ?: throw IllegalStateException("Download directory not initialized")
                if (!dir.exists() && !dir.mkdirs()) {
                    LogUtil.e("Download", "下载目录创建失败: ${dir.absolutePath}")
                    throw IOException("下载目录创建失败，请检查存储权限: ${dir.absolutePath}")
                }
                LogUtil.i("Download", "下载目录就绪: ${dir.absolutePath}")
                val file = File(dir, sanitizeFileName(fileName))
                _downloadState.value = _downloadState.value.copy(
                    filePath = file.absolutePath
                )

                val multiEnabled = UniaballRepository.isMultiThreadDownload.value
                if (multiEnabled) {
                    val (supported, len) = checkRangeSupport(url)
                    if (supported) {
                        downloadMultiThread(url, file, len)
                    } else {
                        downloadSingleThread(url, file)
                    }
                } else {
                    downloadSingleThread(url, file)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    LogUtil.e("Download", "下载失败: $fileName", e)
                    val error = e.message ?: "下载失败"
                    _downloadState.value = _downloadState.value.copy(
                        status = DownloadStatus.FAILED,
                        error = error
                    )
                }
            }
        }
    }

    /**
     * 健壮地清理目标文件路径：处理普通文件和目录两种情况。
     * 删除失败时记录警告并返回 false，由调用方决定后续处理。
     */
    private fun ensureFileDeletable(file: File): Boolean {
        if (!file.exists()) return true
        val deleted = if (file.isDirectory) {
            LogUtil.w("Download", "目标路径是目录，尝试递归删除: ${file.absolutePath}")
            file.deleteRecursively()
        } else {
            file.delete()
        }
        if (!deleted && file.exists()) {
            LogUtil.w("Download", "文件删除失败，将尝试继续: ${file.absolutePath}")
            return false
        }
        if (deleted) {
            LogUtil.i("Download", "已清理旧文件: ${file.absolutePath}")
        }
        return true
    }

    // 单线程下载（fallback 路径，行为与原实现保持一致）
    private suspend fun downloadSingleThread(url: String, file: File) {
        var response: okhttp3.Response? = null
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        try {
            ensureFileDeletable(tmpFile)
            val request = Request.Builder().url(url).build()
            response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                LogUtil.e("Download", "HTTP ${response.code}: ${response.message}")
                _downloadState.value = _downloadState.value.copy(
                    status = DownloadStatus.FAILED,
                    error = "HTTP ${response.code}: ${response.message}"
                )
                return
            }

            val body = response.body ?: run {
                _downloadState.value = _downloadState.value.copy(
                    status = DownloadStatus.FAILED,
                    error = "响应体为空"
                )
                return
            }

            val contentLength = body.contentLength()
            val source = body.source()

            _downloadState.value = _downloadState.value.copy(
                totalBytes = contentLength,
                filePath = file.absolutePath
            )

            var totalRead = 0L
            tmpFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(8192)
                var lastReportTime = System.currentTimeMillis()
                var bytesRead: Int
                while (coroutineContext.isActive) {
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

            if (!coroutineContext.isActive) {
                tmpFile.delete()
                _downloadState.value = _downloadState.value.copy(
                    status = DownloadStatus.CANCELLED
                )
                return
            }

            // 原子化：临时文件重命名为目标文件
            if (ensureFileDeletable(file) && tmpFile.renameTo(file)) {
                LogUtil.i("Download", "文件重命名成功: ${tmpFile.name} -> ${file.name}")
            } else {
                // renameTo 失败或目标不可删除，回退为复制 + 删除
                LogUtil.w("Download", "renameTo 失败，回退为复制: ${tmpFile.name} -> ${file.name}")
                tmpFile.inputStream().buffered().use { input ->
                    file.outputStream().buffered().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                tmpFile.delete()
            }

            _downloadState.value = _downloadState.value.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = totalRead,
                speedBytesPerSec = 0L,
                filePath = file.absolutePath
            )
            LogUtil.i("Download", "下载完成: ${_downloadState.value.fileName} (${totalRead} bytes)")
            UniaballRepository.clearCache()
        } finally {
            response?.close()
        }
    }

    // 检测服务器是否支持 Range 请求，返回 (支持, 总大小)
    private suspend fun checkRangeSupport(url: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false to 0L
            val acceptsRanges = response.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
            val len = response.body?.contentLength() ?: -1L
            (acceptsRanges && len > 0) to (if (len > 0) len else 0L)
        }
    }

    // 多线程分片下载：将 [0, contentLength) 拆成 MULTI_THREAD_COUNT 段并发下载
    private suspend fun downloadMultiThread(url: String, file: File, contentLength: Long) {
        val chunkSize = contentLength / MULTI_THREAD_COUNT
        val ranges = ArrayList<Pair<Long, Long>>(MULTI_THREAD_COUNT)
        var start = 0L
        for (i in 0 until MULTI_THREAD_COUNT) {
            val end = if (i == MULTI_THREAD_COUNT - 1) {
                contentLength - 1
            } else {
                start + chunkSize - 1
            }
            ranges.add(start to end)
            start = end + 1
        }

        cleanupPartFiles(file)
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        ensureFileDeletable(tmpFile)
        val partFiles = (0 until MULTI_THREAD_COUNT).map { i ->
            File(file.parentFile, "${file.name}.part$i")
        }
        val chunkBytes = AtomicLongArray(MULTI_THREAD_COUNT)

        _downloadState.value = _downloadState.value.copy(
            totalBytes = contentLength
        )

        try {
            coroutineScope {
                for (i in 0 until MULTI_THREAD_COUNT) {
                    launch {
                        val (rangeStart, rangeEnd) = ranges[i]
                        val partFile = partFiles[i]
                        val request = Request.Builder()
                            .url(url)
                            .header("Range", "bytes=$rangeStart-$rangeEnd")
                            .build()
                        val response = client.newCall(request).execute()
                        try {
                            if (!response.isSuccessful) {
                                throw IOException("分片下载失败 HTTP ${response.code}")
                            }
                            val source = response.body!!.source()
                            partFile.outputStream().buffered().use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var lastReportTime = System.currentTimeMillis()
                                while (isActive) {
                                    bytesRead = source.read(buffer)
                                    if (bytesRead == -1) break
                                    output.write(buffer, 0, bytesRead)
                                    chunkBytes.addAndGet(i, bytesRead.toLong())

                                    val now = System.currentTimeMillis()
                                    if (now - lastReportTime >= 200) {
                                        lastReportTime = now
                                        var total = 0L
                                        for (j in 0 until MULTI_THREAD_COUNT) {
                                            total += chunkBytes.get(j)
                                        }
                                        val speed = updateSpeed(now, total)
                                        _downloadState.value = _downloadState.value.copy(
                                            downloadedBytes = total,
                                            speedBytesPerSec = speed
                                        )
                                    }
                                }
                            }
                        } finally {
                            response.close()
                        }
                    }
                }
            }

            // 合并分片到目标文件
            tmpFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(8192)
                for (partFile in partFiles) {
                    if (!partFile.exists()) {
                        throw IOException("分片文件缺失: ${partFile.name}")
                    }
                    partFile.inputStream().buffered().use { input ->
                        var bytesRead: Int
                        while (true) {
                            bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }

            // 原子化：临时文件重命名为目标文件
            if (ensureFileDeletable(file) && tmpFile.renameTo(file)) {
                LogUtil.i("Download", "文件重命名成功: ${tmpFile.name} -> ${file.name}")
            } else {
                LogUtil.w("Download", "renameTo 失败，回退为复制: ${tmpFile.name} -> ${file.name}")
                tmpFile.inputStream().buffered().use { input ->
                    file.outputStream().buffered().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                tmpFile.delete()
            }

            _downloadState.value = _downloadState.value.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = contentLength,
                speedBytesPerSec = 0L,
                filePath = file.absolutePath
            )
            LogUtil.i("Download", "多线程下载完成: ${_downloadState.value.fileName} (${contentLength} bytes)")
            UniaballRepository.clearCache()
        } catch (e: CancellationException) {
            file.delete()
            tmpFile.delete()
            _downloadState.value = _downloadState.value.copy(
                status = DownloadStatus.CANCELLED
            )
            throw e
        } catch (e: Exception) {
            file.delete()
            tmpFile.delete()
            _downloadState.value = _downloadState.value.copy(
                status = DownloadStatus.FAILED,
                error = e.message ?: "多线程下载失败"
            )
        } finally {
            cleanupPartFiles(file)
            tmpFile.delete()
        }
    }

    // 清理所有分片临时文件
    private fun cleanupPartFiles(file: File) {
        val parent = file.parentFile ?: return
        for (i in 0 until MULTI_THREAD_COUNT) {
            val partFile = File(parent, "${file.name}.part$i")
            if (partFile.exists()) {
                partFile.delete()
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

    fun openDownloadedFile(context: Context) {
        val state = _downloadState.value
        if (state.status != DownloadStatus.COMPLETED || state.filePath.isBlank()) return
        val file = File(state.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = getMimeType(state.fileName)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(Intent.createChooser(intent, "查看文件"))
        }.onFailure {
            Toast.makeText(context, "无法打开文件，文件路径：${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getMimeType(fileName: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
        if (extension.isNotBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
        }
        return when {
            fileName.endsWith(".apk", ignoreCase = true) -> "application/vnd.android.package-archive"
            fileName.endsWith(".tar.gz", ignoreCase = true) || fileName.endsWith(".tgz", ignoreCase = true) -> "application/gzip"
            else -> "*/*"
        }
    }

    private fun updateSpeed(now: Long, totalBytes: Long): Long = synchronized(speedSamples) {
        speedSamples.add(now to totalBytes)
        speedSamples.removeAll { (t, _) -> now - t > 2000L }
        if (speedSamples.size < 2) return@synchronized 0L
        val first = speedSamples.first()
        val last = speedSamples.last()
        val deltaSec = (last.first - first.first) / 1000.0
        if (deltaSec <= 0) return@synchronized 0L
        ((last.second - first.second) / deltaSec).toLong()
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", " ")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}

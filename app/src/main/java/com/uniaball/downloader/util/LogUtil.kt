package com.uniaball.downloader.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtil {

    private const val TAG = "UniaballDL"
    private const val LOG_FILE_NAME = "uniaball_logs.txt"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private const val MAX_LOG_ENTRIES = 500
    private val logBuffer = ArrayDeque<String>()

    private fun appendLog(level: String, tag: String, msg: String, tr: Throwable? = null) {
        synchronized(logBuffer) {
            val timestamp = dateFormat.format(Date())
            val entry = buildString {
                append(timestamp)
                append(" ")
                append(level)
                append("/")
                append(tag)
                append(": ")
                append(msg)
                if (tr != null) {
                    append("\n")
                    append(tr.stackTraceToString())
                }
            }
            logBuffer.addLast(entry)
            while (logBuffer.size > MAX_LOG_ENTRIES) {
                logBuffer.removeFirst()
            }
        }
    }

    fun d(tag: String, msg: String) {
        Log.d("$TAG/$tag", msg)
        appendLog("D", tag, msg)
    }
    fun i(tag: String, msg: String) {
        Log.i("$TAG/$tag", msg)
        appendLog("I", tag, msg)
    }
    fun w(tag: String, msg: String, tr: Throwable? = null) {
        Log.w("$TAG/$tag", msg, tr)
        appendLog("W", tag, msg, tr)
    }
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e("$TAG/$tag", msg, tr)
        appendLog("E", tag, msg, tr)
    }

    fun exportLogs(context: Context): File {
        val logFile = File(context.cacheDir, LOG_FILE_NAME)
        logFile.delete()

        BufferedWriter(FileWriter(logFile, true)).use { writer ->
            writer.write("=== UniaballDownloader v${com.uniaball.downloader.BuildConfig.VERSION_NAME} Log Export ===\n")
            writer.write("Exported: ${dateFormat.format(Date())}\n")
            writer.write("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (SDK ${android.os.Build.VERSION.SDK_INT})\n")
            writer.write("=" .repeat(60) + "\n\n")
            writer.write("[APP LOGS BELOW]\n\n")

            synchronized(logBuffer) {
                logBuffer.forEach { entry ->
                    writer.write(entry)
                    writer.newLine()
                }
            }
        }

        return logFile
    }

    fun shareLogs(context: Context) {
        val logFile = exportLogs(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "UniaballDownloader Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "导出日志"))
    }
}

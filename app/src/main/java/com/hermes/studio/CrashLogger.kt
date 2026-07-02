package com.hermes.studio

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(context, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        try {
            val logFile = getLogFile(context)
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val entry = buildString {
                appendLine("========== CRASH REPORT ==========")
                appendLine("Time: $timestamp")
                appendLine("Thread: ${thread.name}")
                appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                appendLine("Stack Trace:")
                appendLine(sw)
                appendLine()
            }
            logFile.appendText(entry)
        } catch (_: Exception) {
            // Last resort: can't do much if logging itself fails
        }
    }

    fun getLogFile(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.filesDir, "crash.log")
        } else {
            val dir = File(Environment.getExternalStorageDirectory(), "Android/data/com.hermes.studio/files")
            dir.mkdirs()
            File(dir, "crash.log")
        }
    }

    fun getRuntimeLog(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.filesDir, "runtime.log")
        } else {
            val dir = File(Environment.getExternalStorageDirectory(), "Android/data/com.hermes.studio/files")
            dir.mkdirs()
            File(dir, "runtime.log")
        }
    }

    fun log(context: Context, tag: String, message: String) {
        try {
            val logFile = getRuntimeLog(context)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            logFile.appendText("[$timestamp] [$tag] $message\n")
        } catch (_: Exception) { }
    }

    fun exportLogs(context: Context): File {
        val crashFile = getLogFile(context)
        val runtimeFile = getRuntimeLog(context)
        val exportDir = File(context.filesDir, "exports")
        exportDir.mkdirs()
        val exportFile = File(exportDir, "hermes-studio-logs-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.txt")

        exportFile.bufferedWriter().use { out ->
            out.write("===== Hermes Studio Logs =====\n\n")
            if (runtimeFile.exists()) {
                out.write("----- Runtime Log -----\n")
                out.write(runtimeFile.readText())
                out.write("\n")
            }
            if (crashFile.exists()) {
                out.write("----- Crash Log -----\n")
                out.write(crashFile.readText())
                out.write("\n")
            }
        }
        return exportFile
    }

    fun shareLogs(context: Context) {
        try {
            val logFile = exportLogs(context)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", logFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "导出日志"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "导出日志失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

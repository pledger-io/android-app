package com.pledgerio.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * In-memory ring buffer plus optional on-disk log for issue reports.
 */
class AppLogCollector(
    context: Context,
    private val logcatWriter: (priority: Int, tag: String, message: String) -> Unit = { priority, tag, message ->
        Log.println(priority, tag, message)
        Unit
    },
) {

    private val lock = ReentrantLock()
    private val buffer = ArrayDeque<String>(MAX_LINES)
    private val logFile: File = File(context.cacheDir, "logs/app.log").also { it.parentFile?.mkdirs() }
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    fun installUncaughtExceptionHandler() {
        defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(Log.ERROR, "Crash", "Uncaught exception on ${thread.name}", throwable)
            defaultUncaughtHandler?.uncaughtException(thread, throwable)
        }
    }

    fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        val sanitizedMessage = LogSanitizer.sanitize(message)
        val sanitizedStackTrace = throwable
            ?.let(Log::getStackTraceString)
            ?.let(LogSanitizer::sanitize)
        val line = buildString {
            append(timestampFormat.format(Date()))
            append(' ')
            append(priorityLabel(priority))
            append('/')
            append(tag)
            append(": ")
            append(sanitizedMessage)
            if (sanitizedStackTrace != null) {
                append('\n')
                append(sanitizedStackTrace)
            }
        }
        lock.withLock {
            while (buffer.size >= MAX_LINES) {
                buffer.removeFirst()
            }
            buffer.addLast(line)
            appendToFile(line)
        }
        logcatWriter(priority, tag, sanitizedMessage)
        if (sanitizedStackTrace != null) {
            logcatWriter(priority, tag, sanitizedStackTrace)
        }
    }

    fun export(maxChars: Int = MAX_EXPORT_CHARS): String {
        lock.withLock {
            trimLogFileIfNeeded()
            val fromFile = if (logFile.exists()) logFile.readText() else ""
            val fromMemory = buffer.joinToString(separator = "\n")
            val combined = when {
                fromFile.isBlank() -> fromMemory
                fromMemory.isBlank() -> fromFile
                else -> "$fromFile\n$fromMemory"
            }
            val sanitized = LogSanitizer.sanitize(combined)
            return if (sanitized.length <= maxChars) {
                sanitized
            } else {
                val omitted = sanitized.length - maxChars
                "… (${omitted} characters omitted)\n" + sanitized.takeLast(maxChars)
            }
        }
    }

    fun clear() {
        lock.withLock {
            buffer.clear()
            runCatching {
                if (logFile.exists() && !logFile.delete()) {
                    logFile.writeText("")
                }
            }
        }
    }

    private fun appendToFile(line: String) {
        runCatching {
            logFile.appendText(line + '\n')
            trimLogFileIfNeeded()
        }
    }

    private fun trimLogFileIfNeeded() {
        if (!logFile.exists() || logFile.length() <= MAX_FILE_BYTES) return
        val lines = logFile.readLines()
        val keep = lines.takeLast(MAX_LINES)
        logFile.writeText(keep.joinToString(separator = "\n") + '\n')
    }

    private fun priorityLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }

    companion object {
        private const val MAX_LINES = 2_000
        private const val MAX_FILE_BYTES = 512 * 1024
        const val MAX_EXPORT_CHARS = 48_000
    }
}

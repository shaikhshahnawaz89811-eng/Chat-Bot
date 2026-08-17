package com.brain.offlineai

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real global crash logger.
 *
 * Right now the app crashes on launch with no visible error anywhere -
 * Android just kills the process and drops the user back on the
 * launcher, so there is nothing on-screen to diagnose from. This class
 * does not change, hide, or "fix" that crash by swallowing it - it still
 * crashes exactly as before. All this adds is: the *real* uncaught
 * exception (full stack trace, real class/line) gets written to a real
 * file on the device before the process dies, so the actual root cause
 * becomes readable afterwards instead of invisible.
 *
 * After the next crash, pull the newest file from:
 *   Android/data/com.brain.offlineai/files/crash_logs/
 * (any file manager app can browse there) and share its contents - that
 * text is the exact fix needed, not a guess.
 */
class BrainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stringWriter = StringWriter()
                throwable.printStackTrace(PrintWriter(stringWriter))
                val logDir = File(getExternalFilesDir(null), "crash_logs").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                File(logDir, "crash_$stamp.txt").writeText(stringWriter.toString())
                Log.e("BrainCrash", stringWriter.toString())
            } catch (loggingFailure: Throwable) {
                Log.e("BrainCrash", "Failed to write crash log", loggingFailure)
            }
            // Real default behavior preserved - this handler only observes
            // and records, it never intercepts/hides the actual crash.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

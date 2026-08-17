package com.brain.offlineai

import android.app.Application
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stringWriter = StringWriter()
                throwable.printStackTrace(PrintWriter(stringWriter))
                val crashText = stringWriter.toString()

                val downloadDir =
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )

                val logDir = File(downloadDir, "BrainCrashLogs")
                logDir.mkdirs()

                val stamp = SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.US
                ).format(Date())

                File(logDir, "crash_$stamp.txt").writeText(crashText)

                Log.e("BrainCrash", crashText)
            } catch (loggingFailure: Throwable) {
                Log.e(
                    "BrainCrash",
                    "Failed to write crash log",
                    loggingFailure
                )
            }

            // Original Android crash behavior remains unchanged.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

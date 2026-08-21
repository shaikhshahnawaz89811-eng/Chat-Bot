package com.brain.offlineai.cloudflared

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CloudflaredManager(private val context: Context) {

    companion object {
        private const val ASSET_NAME = "libcloudflared.so"
        private const val EXECUTABLE_NAME = "cloudflared"
    }

    private var process: Process? = null

    suspend fun start(vararg args: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
                    "Unsupported Android ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
                }

                val executable = extractExecutable()

                check(executable.length() > 0L) {
                    "Cloudflared executable is empty"
                }

                check(executable.setExecutable(true)) {
                    "Unable to make Cloudflared executable"
                }

                check(process == null || !process!!.isAlive) {
                    "Cloudflared is already running"
                }

                val command = mutableListOf(executable.absolutePath)
                command.addAll(args)

                process = ProcessBuilder(command)
                    .directory(context.filesDir)
                    .redirectErrorStream(true)
                    .start()
            }
        }

    fun stop() {
        process?.destroy()
        process = null
    }

    fun isRunning(): Boolean =
        process?.isAlive == true

    private fun extractExecutable(): File {
        val target = File(context.filesDir, EXECUTABLE_NAME)

        if (!target.exists() || target.length() == 0L) {
            context.assets.open(ASSET_NAME).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return target
    }
}

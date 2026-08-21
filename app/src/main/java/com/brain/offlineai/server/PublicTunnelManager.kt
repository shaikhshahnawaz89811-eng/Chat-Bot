package com.brain.offlineai.server

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Real state of the public tunnel - same shape as [ServerState] so the UI
 * can treat both consistently. [Running.url] is only ever a value actually
 * parsed from cloudflared's own real stdout - never guessed or templated.
 */
sealed class TunnelState {
    data object Off : TunnelState()
    data object Starting : TunnelState()
    data class Running(val url: String) : TunnelState()
    data class Error(val message: String) : TunnelState()
}

/**
 * Public Tunnel (additive feature) - runs entirely inside Brain's own
 * process. No Termux, no other app, no user typing a command, no domain
 * or Cloudflare account required - this is Cloudflare's free "quick
 * tunnel" mode, which hands back a real but random *.trycloudflare.com
 * link each time it starts.
 *
 * How this actually works (real, not a workaround): Android extracts every
 * file under app/src/main/jniLibs/<abi>/ into this app's own
 * `applicationInfo.nativeLibraryDir` at install time, *with execute
 * permission already set* - that's the one directory in an app's private
 * storage Android's W^X policy (10+) exempts, specifically so apps can ship
 * and run their own native helper binaries. This class runs the real
 * `cloudflared` ARM64 binary from exactly that directory via a plain
 * [ProcessBuilder] - the same primitive Termux itself is built on - so the
 * tunnel is a genuine child process of Brain, started and killed with
 * Brain's own Start/Stop, with nothing external to install or configure.
 *
 * The one real prerequisite: the actual `cloudflared` binary has to be
 * present in the built APK (see docs/PUBLIC_TUNNEL_SETUP.md for the
 * one-time step of adding it to the project before building) - this class
 * cannot download or fabricate that binary itself, and honestly reports
 * [TunnelState.Error] if it isn't there, rather than pretending to start.
 */
object PublicTunnelManager {

    private const val LOCAL_PORT = 11434
    private val URL_REGEX = Regex("https://[a-zA-Z0-9.-]+\\.trycloudflare\\.com")

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Off)
    val state: StateFlow<TunnelState> = _state

    private var process: Process? = null
    private var readerJob: Job? = null

    /** Real path to the bundled binary once installed - null (never a
     *  guessed path) if this app build has no native library dir at all. */
    private fun binaryPath(context: Context): File? {
        val dir = context.applicationInfo.nativeLibraryDir ?: return null
        val file = File(dir, "libcloudflared.so")
        return if (file.exists()) file else null
    }

    /** Called from [LocalApiServerManager.start] - never called standalone,
     *  so the tunnel can never be "on" while the Local API Server it
     *  tunnels is off. */
    fun start(context: Context, scope: CoroutineScope) {
        if (_state.value is TunnelState.Running || _state.value is TunnelState.Starting) return

        val binary = binaryPath(context)
        if (binary == null) {
            _state.value = TunnelState.Error(
                "cloudflared binary not bundled in this build - see docs/PUBLIC_TUNNEL_SETUP.md " +
                    "(one-time: add it to app/src/main/jniLibs/arm64-v8a/ before building)."
            )
            return
        }

        _state.value = TunnelState.Starting
        try {
            val builder = ProcessBuilder(binary.absolutePath, "tunnel", "--url", "http://127.0.0.1:$LOCAL_PORT")
            builder.redirectErrorStream(true) // cloudflared logs its ready-URL line to stderr
            val proc = builder.start()
            process = proc

            readerJob?.cancel()
            readerJob = scope.launch(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                try {
                    while (true) {
                        line = reader.readLine() ?: break
                        val match = URL_REGEX.find(line)
                        if (match != null && _state.value !is TunnelState.Running) {
                            _state.value = TunnelState.Running(match.value)
                        }
                    }
                } catch (e: Exception) {
                    // Real stream failure (process died, pipe closed) - fall through
                    // to the exit-code check below rather than looping forever.
                }
                // Reader loop ended - if we never saw a URL, the process
                // exited (or was killed) before reporting one; report the
                // real state instead of leaving the UI stuck on "Starting".
                if (_state.value is TunnelState.Starting) {
                    val exitCode = try { proc.exitValue() } catch (e: IllegalThreadStateException) { null }
                    _state.value = TunnelState.Error(
                        if (exitCode != null) "cloudflared exited (code $exitCode) before reporting a URL."
                        else "cloudflared stopped before reporting a URL."
                    )
                }
            }
        } catch (e: Exception) {
            // Real launch failure (binary not executable, OS blocked exec, etc.)
            // - never swallowed into a fake "Running" state.
            _state.value = TunnelState.Error(e.message ?: "Failed to start the tunnel process.")
        }
    }

    /** Called from [LocalApiServerManager.stop]. */
    fun stop() {
        readerJob?.cancel()
        readerJob = null
        process?.destroy()
        process = null
        _state.value = TunnelState.Off
    }
}

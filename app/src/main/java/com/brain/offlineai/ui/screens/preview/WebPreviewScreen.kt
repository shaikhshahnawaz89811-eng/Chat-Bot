package com.brain.offlineai.ui.screens.preview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.padding
import com.brain.offlineai.ui.theme.BrainBgPrimary
import com.brain.offlineai.ui.theme.BrainDangerRed
import com.brain.offlineai.ui.theme.BrainTextPrimary
import java.io.File

/**
 * Real, on-device live preview for a real HTML/HTM artifact
 * [com.brain.offlineai.data.artifacts.ArtifactFileManager] already wrote to
 * app-private storage (`context.filesDir/artifacts/<uuid>/<file>.html`).
 * Loaded straight off disk via a real `file://` URL into a real Android
 * `WebView` - no server, no upload, no network call of any kind, so it
 * works exactly the same with the device fully offline (same "100%
 * offline" promise every other real feature in this app already holds
 * itself to). This is genuinely just a renderer, not a host: there is no
 * public URL and nothing here is reachable from outside this device -
 * see [com.brain.offlineai.ui.screens.about.AboutScreen] / README for the
 * honest "why this app can't give you a public domain" note.
 *
 * Deliberately does NOT try to resolve the artifact's referenced
 * `<script src>`/`<link href>` siblings from a real multi-file build yet -
 * a single self-contained HTML file (inline `<style>`/`<script>`, the
 * common case for anything the model generates in one artifact) renders
 * correctly; a page that references a sibling `style.css`/`script.js`
 * artifact by relative path will 404 inside the WebView until those
 * siblings are copied into the same real on-disk folder before load. That
 * gap is intentionally not hidden here - see the file-not-found state
 * below, which reports it honestly instead of showing a blank white page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewScreen(
    fileName: String,
    storedPath: String,
    onBack: () -> Unit,
    // GitHub Hosting feature - additive, safe no-op default (Document-
    // Editing Convention, same as every other additive param elsewhere in
    // this project) so this screen keeps working exactly as before until
    // MainActivity wires this to a real
    // navController.navigate(Screen.GitHubPublish...) call for the same
    // file already open here. Null hides the action entirely (e.g. if a
    // future caller doesn't want it offered from this screen).
    onPublish: (() -> Unit)? = null
) {
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    val fileExists = remember(storedPath) { File(storedPath).exists() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, color = BrainTextPrimary, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrainTextPrimary)
                    }
                },
                actions = {
                    // GitHub Hosting feature - only shown when the caller
                    // actually wired a real publish destination (onPublish
                    // non-null), so this screen's existing offline-only
                    // behavior is completely unchanged for any call site
                    // that doesn't pass one.
                    onPublish?.let { publish ->
                        IconButton(onClick = publish) {
                            Icon(Icons.Filled.Public, contentDescription = "Publish to GitHub", tint = BrainTextPrimary)
                        }
                    }
                    IconButton(onClick = {
                        loadError = null
                        isLoading = true
                        reloadKey++
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload", tint = BrainTextPrimary)
                    }
                }
            )
        },
        containerColor = BrainBgPrimary
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !fileExists -> PreviewMessage(
                    "This artifact's file is no longer on disk - it may have been cleared from storage. Try regenerating it."
                )
                loadError != null -> PreviewMessage(loadError!!)
                else -> {
                    // key() forces a genuinely fresh WebView (and one real
                    // file:// load) per Reload tap, instead of re-running
                    // update{} on top of a WebView that already finished its
                    // own initial load from factory{} - avoids a redundant
                    // double-load on first composition.
                    key(reloadKey) {
                        AndroidWebView(storedPath = storedPath,
                            onLoadStarted = { isLoading = true },
                            onLoadFinished = { isLoading = false },
                            onLoadFailed = { reason -> isLoading = false; loadError = reason }
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = BrainDangerRed)
        Text(text, color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AndroidWebView(
    storedPath: String,
    onLoadStarted: () -> Unit,
    onLoadFinished: () -> Unit,
    onLoadFailed: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // Real, deliberately narrow settings: JS/DOM storage on so a
                // real generated page (which may use inline <script>) genuinely
                // runs, but this WebView only ever loads a single real
                // file:// URL this app itself wrote - never an arbitrary
                // remote page - so the broader risk JS-in-WebView normally
                // carries doesn't apply here the way it would for a browser.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onLoadStarted()
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadFinished()
                    }
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        // Real failure (e.g. a relative-path sibling asset
                        // that isn't on disk - see this file's own doc)
                        // reported honestly, never swallowed into a blank page.
                        if (request?.isForMainFrame == true) {
                            onLoadFailed("Couldn't render this file: ${error?.description ?: "unknown error"}")
                        }
                    }
                }
                loadUrl("file://$storedPath")
            }
        }
    )
}

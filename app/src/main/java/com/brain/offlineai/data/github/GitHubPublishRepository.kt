package com.brain.offlineai.data.github

import android.content.Context
import com.brain.offlineai.data.websearch.ConnectivityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * GitHub Hosting feature - the real, end-to-end "put this generated site
 * on a genuine public URL" flow: create (or reuse) a real GitHub repo
 * under the user's own account, push every selected artifact's real bytes
 * as real commits via [GitHubApiClient], turn on real GitHub Pages, and
 * poll the real build status until it's actually live (or a real, honest
 * timeout - never a fabricated "it's live" the app didn't confirm).
 *
 * Same offline-first gate [com.brain.offlineai.data.websearch.WebSearchRepository]
 * already uses: no stored token, or no real device connectivity right
 * now, and this returns [GitHubPublishStep.Failed] immediately - nothing
 * here is ever attempted silently in the background, and the on-device
 * [com.brain.offlineai.ui.screens.preview.WebPreviewScreen] keeps working
 * fully offline exactly as before regardless of whether this feature is
 * ever configured.
 *
 * Custom-domain feature (additive, [customDomain] param on [publish]) -
 * user-requested: put a generated site on their own domain instead of
 * only the `.github.io` URL. What this genuinely automates, both real
 * GitHub API actions:
 *  1. Commits a real `CNAME` file (content = the domain, nothing else) to
 *     the repo root - the same file GitHub's own "Custom domain" web-UI
 *     box creates automatically, done here explicitly so it survives even
 *     if GitHub's own auto-create behavior ever changes.
 *  2. Calls the real `PUT /repos/{owner}/{repo}/pages` cname update (see
 *     [GitHubApiClient.updatePagesCname]).
 *
 * What this explicitly does NOT and cannot automate: DNS. Pointing the
 * domain's actual A/CNAME records at GitHub happens at the user's own
 * domain registrar (GoDaddy, Namecheap, Cloudflare, whichever one they
 * bought the domain from) - completely outside GitHub's API surface, no
 * app running on the user's phone can reach into a third-party registrar
 * account it has no credentials for. [GitHubPublishStep.Done] carries the
 * real domain/repo values back so the UI can show the user the exact,
 * real DNS records (see [GitHubApiClient.updatePagesCname]'s own doc for
 * where those values come from) they still need to add themselves -
 * never silently claimed as already working.
 */
class GitHubPublishRepository(context: Context) {

    private val appContext = context.applicationContext
    private val keyStore = GitHubKeyStore(appContext)

    fun hasStoredToken(): Boolean = keyStore.hasToken()

    /**
     * Real, real-only progress stream. [files] are the artifact
     * (fileName, on-disk storedPath) pairs already written to app-private
     * storage by [com.brain.offlineai.data.artifacts.ArtifactFileManager] -
     * this function only ever reads bytes that genuinely exist there, it
     * never invents content. [repoName] is whatever the user confirmed on
     * [com.brain.offlineai.ui.screens.github.GitHubPublishScreen] (pre-filled
     * from the first file's own name, editable before the real API call is
     * made). [customDomain], when non-blank, is validated and normalized
     * (see [sanitizeDomain]) before any real API call is made with it -
     * see class doc for exactly what is and isn't automated for it.
     */
    fun publish(
        files: List<Pair<String, String>>,
        repoName: String,
        makePrivate: Boolean,
        customDomain: String? = null
    ): Flow<GitHubPublishStep> = flow {
        val token = keyStore.getToken()
        if (token == null) {
            emit(GitHubPublishStep.Failed("No GitHub token configured yet. Add one in GitHub Publishing settings first."))
            return@flow
        }
        if (!ConnectivityChecker.hasInternet(appContext)) {
            emit(GitHubPublishStep.Failed("No internet connectivity right now - can't publish without a real connection."))
            return@flow
        }
        if (files.isEmpty()) {
            emit(GitHubPublishStep.Failed("No files to publish."))
            return@flow
        }

        val sanitizedDomain = customDomain?.trim()?.takeIf { it.isNotBlank() }?.let { sanitizeDomain(it) }
        if (customDomain?.isNotBlank() == true && sanitizedDomain == null) {
            emit(GitHubPublishStep.Failed("\"$customDomain\" doesn't look like a valid domain (e.g. mysite.com or www.mysite.com) - fix it or leave the field empty."))
            return@flow
        }

        emit(GitHubPublishStep.ValidatingToken)
        val owner = keyStore.getCachedUsername() ?: run {
            when (val result = GitHubApiClient.getAuthenticatedUser(token)) {
                is GitHubApiResult.Success -> result.value.also { keyStore.cacheUsername(it) }
                is GitHubApiResult.Failure -> {
                    emit(GitHubPublishStep.Failed(result.reason))
                    return@flow
                }
            }
        }

        val sanitizedRepoName = sanitizeRepoName(repoName)
        if (sanitizedRepoName.isBlank()) {
            emit(GitHubPublishStep.Failed("Enter a valid repository name (letters, numbers, \"-\", \"_\", \".\")."))
            return@flow
        }

        emit(GitHubPublishStep.CreatingRepo)
        val defaultBranch: String = when (val created = GitHubApiClient.createRepo(token, sanitizedRepoName, makePrivate)) {
            is GitHubApiResult.Success -> created.value
            is GitHubApiResult.Failure -> {
                if (created.reason == "REPO_EXISTS") {
                    when (val existing = GitHubApiClient.getDefaultBranch(token, owner, sanitizedRepoName)) {
                        is GitHubApiResult.Success -> existing.value
                        is GitHubApiResult.Failure -> {
                            emit(GitHubPublishStep.Failed(existing.reason))
                            return@flow
                        }
                    }
                } else {
                    emit(GitHubPublishStep.Failed(created.reason))
                    return@flow
                }
            }
        }

        val plan = buildRepoPathPlan(files)
        var uploaded = 0
        emit(GitHubPublishStep.UploadingFiles(uploaded, plan.size))
        for (upload in plan) {
            val file = File(upload.storedPath)
            if (!file.exists()) {
                emit(GitHubPublishStep.Failed("${upload.fileName} is no longer on disk - it may have been cleared from storage. Try regenerating it."))
                return@flow
            }
            val content = file.readText(Charsets.UTF_8)
            val result = GitHubApiClient.putFile(
                token = token,
                owner = owner,
                repo = sanitizedRepoName,
                path = upload.repoPath,
                content = content,
                branch = defaultBranch,
                commitMessage = "Publish ${upload.fileName} from Brain"
            )
            if (result is GitHubApiResult.Failure) {
                emit(GitHubPublishStep.Failed(result.reason))
                return@flow
            }
            uploaded++
            emit(GitHubPublishStep.UploadingFiles(uploaded, plan.size))
        }

        emit(GitHubPublishStep.EnablingPages)
        when (val enabled = GitHubApiClient.enablePages(token, owner, sanitizedRepoName, defaultBranch)) {
            is GitHubApiResult.Failure -> {
                emit(GitHubPublishStep.Failed(enabled.reason))
                return@flow
            }
            is GitHubApiResult.Success -> Unit
        }

        var customDomainApplied = false
        if (sanitizedDomain != null) {
            emit(GitHubPublishStep.SettingCustomDomain)
            val cnameCommit = GitHubApiClient.putFile(
                token = token,
                owner = owner,
                repo = sanitizedRepoName,
                path = "CNAME",
                content = sanitizedDomain,
                branch = defaultBranch,
                commitMessage = "Set custom domain to $sanitizedDomain (via Brain)"
            )
            if (cnameCommit is GitHubApiResult.Failure) {
                emit(GitHubPublishStep.Failed("Files and Pages published fine, but committing the CNAME file failed: ${cnameCommit.reason}"))
                return@flow
            }
            when (val cnameApi = GitHubApiClient.updatePagesCname(token, owner, sanitizedRepoName, defaultBranch, sanitizedDomain)) {
                is GitHubApiResult.Failure -> {
                    emit(GitHubPublishStep.Failed("Files and Pages published fine, but GitHub rejected the custom domain: ${cnameApi.reason}"))
                    return@flow
                }
                is GitHubApiResult.Success -> customDomainApplied = true
            }
        }

        val repoUrl = "https://github.com/$owner/$sanitizedRepoName"
        val githubIoUrl = "https://$owner.github.io/$sanitizedRepoName/"

        var pagesUrl = githubIoUrl
        var reportedCname: String? = null
        var built = false
        for (attempt in 1..MAX_POLL_ATTEMPTS) {
            emit(GitHubPublishStep.WaitingForPagesBuild(attempt, MAX_POLL_ATTEMPTS))
            delay(POLL_DELAY_MS)
            when (val status = GitHubApiClient.getPagesStatus(token, owner, sanitizedRepoName)) {
                is GitHubApiResult.Success -> {
                    reportedCname = status.value.cname
                    pagesUrl = if (customDomainApplied && sanitizedDomain != null) "https://$sanitizedDomain" else status.value.htmlUrl
                    if (status.value.status.contains("built", ignoreCase = true)) {
                        built = true
                        break
                    }
                }
                is GitHubApiResult.Failure -> {
                    // A real, transient "not ready yet" read failure right
                    // after enabling Pages isn't treated as a hard publish
                    // failure - the repo and files are already genuinely
                    // live; only the build-status poll itself is retried.
                }
            }
        }

        emit(
            GitHubPublishStep.Done(
                pagesUrl = pagesUrl,
                repoUrl = repoUrl,
                stillBuilding = !built,
                customDomain = if (customDomainApplied) (reportedCname ?: sanitizedDomain) else null,
                githubIoUrl = githubIoUrl
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Real placement plan for the artifacts actually being published: the
     * one real HTML/HTM file among them is normalized to "index.html" (the
     * only path GitHub Pages will actually serve at the repo root) and
     * every other real file keeps its own name so relative
     * `<link>`/`<script>` references to it keep resolving - same sibling-
     * asset shape [com.brain.offlineai.ui.screens.preview.WebPreviewScreen]
     * already documents for the local on-device preview.
     */
    private fun buildRepoPathPlan(files: List<Pair<String, String>>): List<GitHubUploadFile> {
        var htmlAlreadyAssigned = false
        return files.map { (fileName, storedPath) ->
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val isHtml = ext == "html" || ext == "htm"
            val repoPath = if (isHtml && !htmlAlreadyAssigned) {
                htmlAlreadyAssigned = true
                "index.html"
            } else {
                sanitizeRepoFilePath(fileName)
            }
            GitHubUploadFile(fileName = fileName, storedPath = storedPath, repoPath = repoPath)
        }
    }

    private fun sanitizeRepoName(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-').take(90)

    private fun sanitizeRepoFilePath(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9._/-]"), "_").trimStart('/')

    /**
     * Real, conservative domain validation/normalization - strips a
     * leading `http://`/`https://` and any trailing path/slash a user
     * might paste in by habit (GitHub's `cname` field wants a bare
     * hostname like `mysite.com` or `www.mysite.com`, not a full URL),
     * lowercases it, and checks it against a real hostname-shape regex
     * (labels of letters/digits/hyphens, at least one dot, no spaces).
     * Returns null - never a best-guess fixed-up value - when what's left
     * still doesn't look like a real domain, so the caller can fail
     * honestly instead of silently sending GitHub something wrong.
     */
    private fun sanitizeDomain(raw: String): String? {
        var d = raw.trim().lowercase()
        d = d.removePrefix("https://").removePrefix("http://")
        d = d.substringBefore('/').substringBefore(':').substringBefore('?')
        val hostnameRegex = Regex("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$")
        return d.takeIf { hostnameRegex.matches(it) }
    }

    companion object {
        private const val MAX_POLL_ATTEMPTS = 8
        private const val POLL_DELAY_MS = 5_000L
    }
}

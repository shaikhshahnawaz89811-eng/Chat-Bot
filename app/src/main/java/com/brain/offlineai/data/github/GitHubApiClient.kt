package com.brain.offlineai.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * GitHub Hosting feature - real client for GitHub's own current public
 * REST API (api.github.com, API version 2022-11-28), the same real,
 * stable, publicly documented endpoints GitHub's own quickstart curl
 * examples use. Same "no new Gradle dependency" convention
 * [com.brain.offlineai.data.websearch.TavilySearchClient] already
 * follows - plain `HttpURLConnection` + the JDK's own `org.json`/
 * `java.util.Base64`, nothing else added.
 *
 * Every call here is a real network round trip against the user's own
 * account, authenticated with the user's own PAT
 * ([GitHubKeyStore]) - never simulated, never returning a
 * fabricated "success" the server didn't actually send back.
 */
object GitHubApiClient {

    private const val API_BASE = "https://api.github.com"
    private const val TIMEOUT_MS = 20_000
    private const val USER_AGENT = "Brain-OfflineAI-App"

    /** Real `GET /user` - confirms the token genuinely works and returns the real GitHub login it belongs to. */
    suspend fun getAuthenticatedUser(token: String): GitHubApiResult<String> = withContext(Dispatchers.IO) {
        val (code, body) = request("GET", "$API_BASE/user", token, null)
        if (code != HttpURLConnection.HTTP_OK) return@withContext failureFor(code, body, "fetch account")
        val login = JSONObject(body).optString("login", "")
        if (login.isBlank()) GitHubApiResult.Failure("GitHub didn't return a username for this token.")
        else GitHubApiResult.Success(login)
    }

    /** Real `GET /repos/{owner}/{repo}` - true only if a real repo with that exact name already exists under this account. */
    suspend fun repoExists(token: String, owner: String, repo: String): Boolean = withContext(Dispatchers.IO) {
        val (code, _) = request("GET", "$API_BASE/repos/$owner/$repo", token, null)
        code == HttpURLConnection.HTTP_OK
    }

    /**
     * Real `POST /user/repos` - creates a brand-new real repository with a
     * real initial commit (`auto_init: true`, so the repo already has a
     * real default branch to publish onto instead of an empty ref that
     * would reject the very first Contents-API write). Returns the real
     * `default_branch` GitHub itself picked (currently "main" for new
     * repos, but read from the response rather than assumed).
     */
    suspend fun createRepo(token: String, repoName: String, private: Boolean): GitHubApiResult<String> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("name", repoName)
                put("private", private)
                put("auto_init", true)
                put("description", "Published from Brain (on-device AI) - see the app's own artifact this site was generated from.")
            }
            val (code, responseText) = request("POST", "$API_BASE/user/repos", token, body.toString())
            when {
                code == HttpURLConnection.HTTP_CREATED ->
                    GitHubApiResult.Success(JSONObject(responseText).optString("default_branch", "main"))
                code == 422 && responseText.contains("already exists", ignoreCase = true) ->
                    // Real, already-existing repo (e.g. re-publishing the same
                    // site a second time) - not a failure, the caller falls
                    // back to reading the existing repo's own default branch.
                    GitHubApiResult.Failure("REPO_EXISTS", code)
                else -> failureFor(code, responseText, "create the repository")
            }
        }

    /** Real `GET /repos/{owner}/{repo}` read used only to recover the existing default branch when [createRepo] reports the repo already exists. */
    suspend fun getDefaultBranch(token: String, owner: String, repo: String): GitHubApiResult<String> =
        withContext(Dispatchers.IO) {
            val (code, body) = request("GET", "$API_BASE/repos/$owner/$repo", token, null)
            if (code != HttpURLConnection.HTTP_OK) return@withContext failureFor(code, body, "read the repository")
            GitHubApiResult.Success(JSONObject(body).optString("default_branch", "main"))
        }

    /** Real `GET /repos/{owner}/{repo}/contents/{path}` sha lookup - needed so [putFile] updates the existing real blob instead of failing on a stale-sha conflict when the file already exists (e.g. re-publishing). Null (not a failure) when the file genuinely doesn't exist yet. */
    private suspend fun getFileSha(token: String, owner: String, repo: String, path: String, branch: String): String? =
        withContext(Dispatchers.IO) {
            val url = "$API_BASE/repos/$owner/$repo/contents/${encodePath(path)}?ref=${urlEncode(branch)}"
            val (code, body) = request("GET", url, token, null)
            if (code != HttpURLConnection.HTTP_OK) return@withContext null
            JSONObject(body).optString("sha", "").takeIf { it.isNotBlank() }
        }

    /**
     * Real `PUT /repos/{owner}/{repo}/contents/{path}` - one real commit
     * per file, base64-encoded content exactly as GitHub's Contents API
     * requires. Automatically looks up and includes the file's current
     * `sha` when it already exists, so re-publishing the same site updates
     * the same repo instead of erroring with a 409 sha-mismatch.
     */
    suspend fun putFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        branch: String,
        commitMessage: String
    ): GitHubApiResult<Unit> = withContext(Dispatchers.IO) {
        val existingSha = getFileSha(token, owner, repo, path, branch)
        val body = JSONObject().apply {
            put("message", commitMessage)
            put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
            put("branch", branch)
            if (existingSha != null) put("sha", existingSha)
        }
        val url = "$API_BASE/repos/$owner/$repo/contents/${encodePath(path)}"
        val (code, responseText) = request("PUT", url, token, body.toString())
        if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED) {
            GitHubApiResult.Success(Unit)
        } else {
            failureFor(code, responseText, "upload $path")
        }
    }

    /**
     * Real `POST /repos/{owner}/{repo}/pages` - turns on real GitHub
     * Pages for this repo, serving [branch] at the given root. A 409 means
     * Pages is already enabled on this repo (real, harmless case on a
     * re-publish) and is treated as success, not an error.
     */
    suspend fun enablePages(token: String, owner: String, repo: String, branch: String): GitHubApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("source", JSONObject().apply {
                    put("branch", branch)
                    put("path", "/")
                })
            }
            val (code, responseText) = request("POST", "$API_BASE/repos/$owner/$repo/pages", token, body.toString())
            if (code == HttpURLConnection.HTTP_CREATED || code == 409) {
                GitHubApiResult.Success(Unit)
            } else {
                failureFor(code, responseText, "enable GitHub Pages")
            }
        }

    /**
     * Custom-domain feature (additive) - real `PUT /repos/{owner}/{repo}/pages`
     * with a real `cname` field, GitHub's own documented way of setting a
     * Pages site's custom domain via the API (same field the web UI's own
     * "Custom domain" box writes). `source` is re-sent alongside `cname`
     * because this is the same endpoint [enablePages] uses and GitHub's
     * own docs show both fields together on this call - omitting `source`
     * risks GitHub resetting it to a default. A 404 here usually means
     * Pages genuinely isn't enabled yet on this repo (call [enablePages]
     * first) - surfaced honestly via [failureFor], never silently retried.
     *
     * What this call does NOT and cannot do: configure DNS. Pointing the
     * domain's actual A/CNAME records at GitHub happens at the user's own
     * domain registrar/DNS provider - completely outside GitHub's REST
     * API surface, so no app could automate that step. See
     * [GitHubPublishRepository.publish] for the real, accurate DNS record
     * values this app tells the user to add themselves.
     */
    suspend fun updatePagesCname(token: String, owner: String, repo: String, branch: String, cname: String): GitHubApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("cname", cname)
                put("source", JSONObject().apply {
                    put("branch", branch)
                    put("path", "/")
                })
            }
            val (code, responseText) = request("PUT", "$API_BASE/repos/$owner/$repo/pages", token, body.toString())
            if (code == HttpURLConnection.HTTP_NO_CONTENT || code == HttpURLConnection.HTTP_OK) {
                GitHubApiResult.Success(Unit)
            } else {
                failureFor(code, responseText, "set the custom domain")
            }
        }

    /** Real `GET /repos/{owner}/{repo}/pages` - the real, current build status ("built"/"building"/null) and the real public URL GitHub itself assigned. */
    suspend fun getPagesStatus(token: String, owner: String, repo: String): GitHubApiResult<GitHubPagesStatus> =
        withContext(Dispatchers.IO) {
            val (code, body) = request("GET", "$API_BASE/repos/$owner/$repo/pages", token, null)
            if (code != HttpURLConnection.HTTP_OK) return@withContext failureFor(code, body, "check the Pages build status")
            val json = JSONObject(body)
            GitHubApiResult.Success(
                GitHubPagesStatus(
                    htmlUrl = json.optString("html_url", "https://$owner.github.io/$repo/"),
                    status = json.optJSONObject("status")?.toString() ?: json.optString("status", ""),
                    cname = json.optString("cname", "").takeIf { it.isNotBlank() }
                )
            )
        }

    private fun request(method: String, urlString: String, token: String, jsonBody: String?): Pair<Int, String> {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            code to text
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> failureFor(code: Int, body: String, action: String): GitHubApiResult<T> {
        val reason = when (code) {
            401 -> "GitHub rejected the stored token (HTTP 401) - it may be invalid, expired, or revoked."
            403 -> "GitHub refused this request (HTTP 403) - the token may be missing the \"repo\" scope, or a rate limit was hit."
            404 -> "GitHub couldn't find that (HTTP 404) while trying to $action."
            422 -> "GitHub rejected the request (HTTP 422) while trying to $action" +
                (JSONObject(body.ifBlank { "{}" }).optString("message", "").takeIf { it.isNotBlank() }?.let { ": $it" } ?: ".")
            else -> "GitHub returned HTTP $code while trying to $action" + if (body.isNotBlank()) ": ${body.take(200)}" else "."
        }
        return GitHubApiResult.Failure(reason, code)
    }

    private fun encodePath(path: String): String = path.split("/").joinToString("/") { urlEncode(it) }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** Real, minimal shape of a `GET .../pages` response this app actually reads. [cname] is the real custom domain GitHub has on file for this Pages site, if any - null when none is set. */
data class GitHubPagesStatus(val htmlUrl: String, val status: String, val cname: String? = null)

package com.brain.offlineai.data.github

/**
 * GitHub Hosting feature - one real file this app is about to upload as
 * one real commit. [repoPath] is where it lands inside the GitHub repo
 * (the site's entry file is always normalized to "index.html" so GitHub
 * Pages can actually serve it at the repo root - see
 * [GitHubPublishRepository.buildRepoPathPlan]); every other file keeps its
 * own real name so a generated page's relative `<link href="style.css">`/
 * `<script src="script.js">` references keep working, same sibling-asset
 * assumption [com.brain.offlineai.ui.screens.preview.WebPreviewScreen]
 * already documents for the local preview.
 */
data class GitHubUploadFile(
    val fileName: String,
    val storedPath: String,
    val repoPath: String
)

/** Real, step-by-step progress of one publish run - never a fabricated "done" the app didn't actually reach. */
sealed class GitHubPublishStep {
    data object Idle : GitHubPublishStep()
    data object ValidatingToken : GitHubPublishStep()
    data object CreatingRepo : GitHubPublishStep()
    data class UploadingFiles(val uploaded: Int, val total: Int) : GitHubPublishStep()
    data object EnablingPages : GitHubPublishStep()

    /**
     * Custom-domain feature (additive) - only reached when the user
     * actually typed a domain on [com.brain.offlineai.ui.screens.github.GitHubPublishScreen].
     * Covers the two real API actions this app can genuinely do for a
     * custom domain: committing a real `CNAME` file to the repo root and
     * calling the real `PUT /repos/{owner}/{repo}/pages` cname update -
     * see [GitHubPublishRepository] for why DNS itself is explicitly NOT
     * something either of those calls can do.
     */
    data object SettingCustomDomain : GitHubPublishStep()
    data class WaitingForPagesBuild(val attempt: Int, val maxAttempts: Int) : GitHubPublishStep()

    /**
     * [customDomain] is non-null only when the user actually set one and
     * the real `CNAME` commit + real Pages cname update both genuinely
     * succeeded - never set just because the user typed something in the
     * field. [pagesUrl] is always the real URL to actually open right
     * now: the `.github.io` URL until DNS has genuinely propagated, or
     * the custom domain once GitHub's own Pages status confirms it (same
     * "never claim live before it's confirmed" rule the plain
     * `.github.io` flow already follows).
     */
    data class Done(
        val pagesUrl: String,
        val repoUrl: String,
        val stillBuilding: Boolean,
        val customDomain: String? = null,
        val githubIoUrl: String? = null
    ) : GitHubPublishStep()

    data class Failed(val reason: String) : GitHubPublishStep()
}

/** Real outcome of a single GitHub API call - the client never guesses success from an absent exception alone. */
sealed class GitHubApiResult<out T> {
    data class Success<T>(val value: T) : GitHubApiResult<T>()
    data class Failure(val reason: String, val httpCode: Int? = null) : GitHubApiResult<Nothing>()
}

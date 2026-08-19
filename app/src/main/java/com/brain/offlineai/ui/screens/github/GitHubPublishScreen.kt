package com.brain.offlineai.ui.screens.github

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.github.GitHubPublishStep
import com.brain.offlineai.ui.theme.*

/**
 * GitHub Hosting feature - the real publish screen reached from a
 * "Publish to GitHub" action on one or more artifacts
 * ([com.brain.offlineai.ui.components.ArtifactCard],
 * [com.brain.offlineai.ui.screens.preview.WebPreviewScreen]). [files] are
 * the real (fileName, on-disk storedPath) pairs to push - see
 * [com.brain.offlineai.navigation.Screen.GitHubPublish] for how they're
 * encoded into the nav route.
 *
 * If no token is configured yet, this screen shows that honestly and
 * routes to [GitHubSettingsScreen] instead of pretending it could publish
 * anyway.
 *
 * Custom-domain feature (additive) - an optional "Custom domain" field
 * below the repository name. Left blank, publishing behaves exactly as
 * before (`.github.io` URL only). Filled in, [DoneCard] shows the real,
 * exact DNS records the user still needs to add at their own domain
 * registrar - see [GitHubPublishRepository][com.brain.offlineai.data.github.GitHubPublishRepository]'s
 * own doc for why DNS itself can never be automated from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubPublishScreen(
    files: List<Pair<String, String>>,
    viewModel: GitHubPublishViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var makePrivate by remember { mutableStateOf(false) }

    LaunchedEffect(files) {
        if (viewModel.repoName.isBlank()) {
            viewModel.updateRepoName(suggestRepoName(files.firstOrNull()?.first ?: "site"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publish to GitHub", color = BrainTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrainTextPrimary)
                    }
                }
            )
        },
        containerColor = BrainBgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            if (!viewModel.hasStoredToken) {
                NoTokenState(onOpenSettings = onOpenSettings)
                return@Scaffold
            }

            Text(
                "${files.size} file${if (files.size == 1) "" else "s"} will be pushed to a real repository on your own GitHub account, and GitHub Pages will be turned on so it gets a genuine public URL.",
                color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrainBgCard)
                    .padding(14.dp)
            ) {
                files.forEach { (fileName, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = BrainCyanAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(fileName, color = BrainTextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            val editable = viewModel.step is GitHubPublishStep.Idle || viewModel.step is GitHubPublishStep.Failed
            OutlinedTextField(
                value = viewModel.repoName,
                onValueChange = { viewModel.updateRepoName(it) },
                label = { Text("Repository name") },
                singleLine = true,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrainPurplePrimary,
                    unfocusedBorderColor = BrainBorder,
                    focusedTextColor = BrainTextPrimary,
                    unfocusedTextColor = BrainTextPrimary,
                    cursorColor = BrainPurplePrimary
                )
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = viewModel.customDomain,
                onValueChange = { viewModel.updateCustomDomain(it) },
                label = { Text("Custom domain (optional)") },
                placeholder = { Text("mysite.com", color = BrainTextMuted) },
                singleLine = true,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrainPurplePrimary,
                    unfocusedBorderColor = BrainBorder,
                    focusedTextColor = BrainTextPrimary,
                    unfocusedTextColor = BrainTextPrimary,
                    cursorColor = BrainPurplePrimary
                )
            )
            Text(
                "Leave blank to just use the free *.github.io URL. If you own a domain already, this connects it - you'll still need to add a couple of DNS records yourself afterwards (shown here once publishing finishes).",
                color = BrainTextMuted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = editable) { makePrivate = !makePrivate },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = makePrivate, onCheckedChange = { if (editable) makePrivate = it }, enabled = editable)
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Private repository", color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Note: GitHub Pages on a private repo needs GitHub Pro/Team/Enterprise, or the site simply won't be publicly reachable.",
                        color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PublishStepContent(
                step = viewModel.step,
                onPublish = { viewModel.publish(files, makePrivate) },
                onRetry = { viewModel.publish(files, makePrivate) },
                context = context
            )
        }
    }
}

@Composable
private fun NoTokenState(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Public, contentDescription = null, tint = BrainPurplePrimary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "No GitHub token configured yet",
            color = BrainTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Add your own GitHub Personal Access Token first - it's only ever used for the real publish action you trigger yourself.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary, contentColor = androidx.compose.ui.graphics.Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Open GitHub Publishing Settings")
        }
    }
}

@Composable
private fun PublishStepContent(
    step: GitHubPublishStep,
    onPublish: () -> Unit,
    onRetry: () -> Unit,
    context: Context
) {
    when (step) {
        is GitHubPublishStep.Idle -> {
            Button(
                onClick = onPublish,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary, contentColor = androidx.compose.ui.graphics.Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Publish")
            }
        }
        is GitHubPublishStep.ValidatingToken -> ProgressRow("Checking your GitHub account...")
        is GitHubPublishStep.CreatingRepo -> ProgressRow("Creating the repository...")
        is GitHubPublishStep.UploadingFiles -> ProgressRow("Uploading files (${step.uploaded}/${step.total})...")
        is GitHubPublishStep.EnablingPages -> ProgressRow("Turning on GitHub Pages...")
        is GitHubPublishStep.SettingCustomDomain -> ProgressRow("Connecting your custom domain...")
        is GitHubPublishStep.WaitingForPagesBuild -> ProgressRow("Waiting for GitHub Pages to build (${step.attempt}/${step.maxAttempts})...")
        is GitHubPublishStep.Done -> DoneCard(step, context)
        is GitHubPublishStep.Failed -> FailedCard(step.reason, onRetry)
    }
}

@Composable
private fun ProgressRow(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BrainPurplePrimary, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(label, color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DoneCard(step: GitHubPublishStep.Done, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrainSuccessGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Published", color = BrainSuccessGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(step.pagesUrl, color = BrainCyanAccent, style = MaterialTheme.typography.bodyMedium)
        if (step.stillBuilding) {
            Spacer(Modifier.height(6.dp))
            Text(
                "GitHub is still building the page - it can take a few more minutes to actually go live at this URL.",
                color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(step.pagesUrl)))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary, contentColor = androidx.compose.ui.graphics.Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Site URL", step.pagesUrl))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, step.pagesUrl)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share link"))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Repository: ${step.repoUrl}",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(step.repoUrl)))
            }
        )
        if (step.customDomain != null) {
            Spacer(Modifier.height(14.dp))
            DnsInstructionsCard(domain = step.customDomain, githubIoUrl = step.githubIoUrl, context = context)
        }
    }
}

/**
 * Custom-domain feature (additive) - the real, exact DNS records the user
 * still has to add at their own domain registrar's DNS settings page for
 * [domain] to actually start pointing at GitHub Pages. Two real cases,
 * shown together since either can apply depending on what [domain] is:
 *  - An apex/root domain (e.g. "mysite.com") needs 4 real A records
 *    pointing at GitHub Pages' own IP addresses.
 *  - A subdomain (e.g. "www.mysite.com") needs one real CNAME record
 *    pointing at [githubIoUrl]'s own host (the `owner.github.io` part).
 * This app has already done the two things it genuinely can (committing
 * the real `CNAME` file and calling the real Pages cname-update API - see
 * [com.brain.offlineai.data.github.GitHubPublishRepository]) - this card
 * only ever displays the remaining, real manual step, never claims to
 * have done it.
 */
@Composable
private fun DnsInstructionsCard(domain: String, githubIoUrl: String?, context: Context) {
    val githubIoHost = githubIoUrl?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')
    val isApex = domain.count { it == '.' } <= 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrainBgPrimary)
            .padding(12.dp)
    ) {
        Text(
            "One more real step - add these DNS record(s) at wherever you bought \"$domain\" (GoDaddy, Namecheap, Cloudflare, etc.). This app can't do this part for you - it's on your domain registrar's own site, not GitHub's.",
            color = BrainTextPrimary, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        if (isApex) {
            Text("Add 4 A records (host \"@\") pointing to:", color = BrainTextSecondary, style = MaterialTheme.typography.labelMedium)
            GITHUB_PAGES_IPS.forEach { ip ->
                Text(ip, color = BrainCyanAccent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
            }
        } else if (githubIoHost != null) {
            Text("Add a CNAME record (host \"${domain.substringBefore('.')}\") pointing to:", color = BrainTextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(githubIoHost, color = BrainCyanAccent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "DNS changes can take anywhere from a few minutes to ~24 hours to spread. Until then, the *.github.io link above still works.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = if (isApex) GITHUB_PAGES_IPS.joinToString("\n") else (githubIoHost ?: "")
                clipboard.setPrimaryClip(ClipData.newPlainText("DNS record value", text))
            },
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy record value(s)", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Real, GitHub-published IP addresses for GitHub Pages apex-domain A records - see https://docs.github.com/pages for the current, authoritative list if GitHub ever changes these. */
private val GITHUB_PAGES_IPS = listOf(
    "185.199.108.153",
    "185.199.109.153",
    "185.199.110.153",
    "185.199.111.153"
)

@Composable
private fun FailedCard(reason: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = BrainDangerRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Publish failed", color = BrainDangerRed, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(reason, color = BrainTextPrimary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary, contentColor = androidx.compose.ui.graphics.Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Retry")
        }
    }
}

/** Real, GitHub-repo-name-safe default derived from the artifact's own file name - editable before publishing. */
private fun suggestRepoName(fileName: String): String {
    val base = fileName.substringBeforeLast('.', fileName).ifBlank { "site" }
    val sanitized = base.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-')
    return (sanitized.ifBlank { "site" }) + "-site"
}

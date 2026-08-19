package com.brain.offlineai.ui.screens.github

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.ui.theme.*

/**
 * GitHub Hosting feature - real settings screen for the user's own,
 * optional GitHub Personal Access Token. Reached from General Settings'
 * "More" section, same sub-destination pattern as
 * [com.brain.offlineai.ui.screens.websearch.WebSearchSettingsScreen]. This
 * app previews generated sites fully offline with nothing entered here
 * (see [com.brain.offlineai.ui.screens.preview.WebPreviewScreen]) - a
 * token only unlocks the separate, explicit "Publish to GitHub" action
 * on an artifact, never anything automatic.
 */
@Composable
fun GitHubSettingsScreen(
    viewModel: GitHubSettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrainTextPrimary)
            }
            Text("GitHub Publishing", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Optional. Brain previews generated websites fully offline without this. When a real " +
                "GitHub token is saved here, the \"Publish to GitHub\" action on a generated site " +
                "can push it to a real repository on your own GitHub account and turn on GitHub " +
                "Pages, giving it a genuine public URL. Nothing here runs automatically - publishing " +
                "only ever happens when you tap Publish on a specific artifact.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BrainBgCard)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, contentDescription = null, tint = BrainPurplePrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("GitHub Personal Access Token", color = BrainTextSecondary, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))

            if (viewModel.hasStoredToken) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrainSuccessGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        viewModel.storedUsername?.let { "Connected as $it" } ?: "A validated token is saved and active.",
                        color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.clearToken(); tokenInput = "" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disconnect")
                }
            } else {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    placeholder = { Text("ghp_...", color = BrainTextMuted) },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showToken) "Hide token" else "Show token",
                                tint = BrainTextMuted
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrainPurplePrimary,
                        unfocusedBorderColor = BrainBorder,
                        focusedTextColor = BrainTextPrimary,
                        unfocusedTextColor = BrainTextPrimary,
                        cursorColor = BrainPurplePrimary
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Needs the \"repo\" scope (classic token) so it can create repositories and turn on Pages.",
                    color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Create one at github.com/settings/tokens",
                    color = BrainCyanAccent,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.validateAndSaveToken(tokenInput) },
                    enabled = viewModel.validationState != GitHubTokenValidationState.VALIDATING,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary, contentColor = androidx.compose.ui.graphics.Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (viewModel.validationState == GitHubTokenValidationState.VALIDATING) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Validate & Save")
                    }
                }
                viewModel.errorMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = BrainDangerRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "The token is stored on-device only, encrypted the same way (Android Keystore-backed " +
                "AES-256) this app already protects the API Keys it issues and the Tavily search key - " +
                "never sent anywhere except real, direct HTTPS calls to api.github.com under your own account.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
    }
}

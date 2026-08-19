package com.brain.offlineai.ui.screens.websearch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
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
 * Phase 22 (Master Plan v2, revised scope) - real settings screen for the
 * user's own, optional Tavily web-search API key. Reached from General
 * Settings' "More" section, same sub-destination pattern as
 * [com.brain.offlineai.ui.screens.storage.StorageScreen]. This app works
 * fully offline with nothing entered here - see the honest explanation
 * text below, same "explicitly not faked" transparency every earlier
 * phase's own screens already practice.
 */
@Composable
fun WebSearchSettingsScreen(
    viewModel: WebSearchSettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

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
            Text("Web Search", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Optional. Brain runs fully offline without this. When a real Tavily API key is " +
                "saved here and the device has internet, Brain may run a real web search in two " +
                "narrow, real situations: building something new that needs current/outside " +
                "information, or inspecting an attached project that references something " +
                "unfamiliar. It never searches on an ordinary, already-clear message, and it " +
                "never sends your project's source code to Tavily - only a short search query.",
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
                Icon(Icons.Filled.Public, contentDescription = null, tint = BrainPurplePrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tavily API Key", color = BrainTextSecondary, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))

            if (viewModel.hasStoredKey) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrainSuccessGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("A validated key is saved and active.", color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.clearKey(); keyInput = "" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove Key")
                }
            } else {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("tvly-...", color = BrainTextMuted) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key",
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
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.validateAndSaveKey(keyInput) },
                    enabled = viewModel.validationState != KeyValidationState.VALIDATING,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary, contentColor = androidx.compose.ui.graphics.Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (viewModel.validationState == KeyValidationState.VALIDATING) {
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
            "The key is stored on-device only, encrypted the same way (Android Keystore-backed " +
                "AES-256) this app already protects the API Keys it issues - never sent anywhere " +
                "except a real, direct HTTPS call to api.tavily.com.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
    }
}

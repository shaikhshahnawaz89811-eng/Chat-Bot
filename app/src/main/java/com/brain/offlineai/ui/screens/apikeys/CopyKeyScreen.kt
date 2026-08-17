package com.brain.offlineai.ui.screens.apikeys

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.apikeys.ApiKeyEntity
import com.brain.offlineai.ui.theme.*

/**
 * Screen 9 from the mockup ("Copy Key - Secure Copy Animation"). Reached
 * only after Key Details / Key Options already made a real
 * ClipboardManager write (see copyKeyToClipboard in ApiKeyUiCommon.kt) -
 * this confirms something that actually happened, it doesn't perform the
 * copy itself.
 */
@Composable
fun CopyKeyScreen(
    keyId: String,
    viewModel: ApiKeysViewModel = viewModel(),
    onDone: () -> Unit
) {
    var key by remember { mutableStateOf<ApiKeyEntity?>(null) }
    LaunchedEffect(keyId) { key = viewModel.getKey(keyId) }

    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale.value)
                .clip(CircleShape)
                .background(BrainSuccessGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = BrainSuccessGreen, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("API Key Copied!", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        key?.let {
            Text(truncateKey(it.keyValue), color = BrainTextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "The key has been copied to your clipboard.",
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done")
        }
    }
}

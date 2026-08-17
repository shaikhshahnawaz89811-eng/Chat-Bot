package com.brain.offlineai.ui.screens.about

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brain.offlineai.ui.components.FooterBadges
import com.brain.offlineai.ui.theme.*

private data class AppVersionInfo(val versionName: String, val versionCode: Long)

/**
 * Screen 14 from the mockup ("About"). Replaces the Phase 1-5 Placeholder
 * route. Version number and device info are read from the real
 * PackageManager / Build fields at render time - not hardcoded strings
 * that would silently go stale on the next release (Rule 10/17).
 */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val versionInfo = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION") info.versionCode.toLong()
            }
            AppVersionInfo(info.versionName ?: "unknown", code)
        } catch (e: PackageManager.NameNotFoundException) {
            AppVersionInfo("unknown", 0L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(BrainPurplePrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Psychology, contentDescription = null, tint = BrainPurplePrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("BRAIN", color = BrainTextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Text("Offline AI Engine", color = BrainTextMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Version ${versionInfo.versionName} (build ${versionInfo.versionCode})",
                color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(24.dp))

        InfoCard(title = "This Device") {
            InfoLine("Android Version", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            InfoLine("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
            InfoLine("CPU Cores", "${Runtime.getRuntime().availableProcessors()}")
        }
        Spacer(Modifier.height(14.dp))

        InfoCard(title = "Built On") {
            InfoLine("App logic", "Kotlin + Jetpack Compose (Material3)")
            InfoLine("Local AI engine", "llama.cpp (JNI/NDK)")
            InfoLine("Secure storage", "Room + SQLCipher (AES-256)")
            InfoLine("Local API server", "NanoHTTPD, loopback-only")
        }
        Spacer(Modifier.height(14.dp))

        InfoCard(title = "License & Data") {
            Text(
                "Brain runs entirely on this device. No account, no analytics leave your phone, no model or conversation data is uploaded anywhere by this app.",
                color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))
        FooterBadges(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(14.dp)
    ) {
        Text(title, color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = BrainTextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

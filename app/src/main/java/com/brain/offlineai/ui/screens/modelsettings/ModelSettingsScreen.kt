package com.brain.offlineai.ui.screens.modelsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.settings.ModelSettingsRepository
import com.brain.offlineai.engine.EngineState
import com.brain.offlineai.ui.theme.*

/**
 * Screen 11 from the mockup ("Model Settings" - context length,
 * temperature, top-p, thread-count sliders). Every slider is backed by a
 * real, persisted value (see ModelSettingsRepository) that the real engine
 * (Models screen load, Chat screen generate) actually reads - there is no
 * decorative slider here that doesn't affect anything.
 */
@Composable
fun ModelSettingsScreen(
    viewModel: ModelSettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val settings = viewModel.settings
    val engineState by viewModel.engineState.collectAsState()
    val modelIsLoaded = engineState is EngineState.Loaded

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrainTextPrimary)
            }
            Text("Model Settings", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        SettingSlider(
            label = "Context Length",
            valueLabel = "${settings.contextLength} tokens",
            value = settings.contextLength.toFloat(),
            valueRange = ModelSettingsRepository.MIN_CONTEXT_LENGTH.toFloat()..ModelSettingsRepository.MAX_CONTEXT_LENGTH.toFloat(),
            steps = ((ModelSettingsRepository.MAX_CONTEXT_LENGTH - ModelSettingsRepository.MIN_CONTEXT_LENGTH) / 512) - 1,
            onValueChange = { viewModel.updateSettings(settings.copy(contextLength = (it / 512).toInt() * 512)) }
        )
        Text(
            "How much conversation the model can consider at once. Higher uses more RAM.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(22.dp))

        SettingSlider(
            label = "Temperature",
            valueLabel = "%.2f".format(settings.temperature),
            value = settings.temperature,
            valueRange = ModelSettingsRepository.MIN_TEMPERATURE..ModelSettingsRepository.MAX_TEMPERATURE,
            onValueChange = { viewModel.updateSettings(settings.copy(temperature = it)) }
        )
        Text(
            "Higher is more creative/random, lower is more focused/deterministic.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(22.dp))

        SettingSlider(
            label = "Top-P",
            valueLabel = "%.2f".format(settings.topP),
            value = settings.topP,
            valueRange = ModelSettingsRepository.MIN_TOP_P..ModelSettingsRepository.MAX_TOP_P,
            onValueChange = { viewModel.updateSettings(settings.copy(topP = it)) }
        )
        Text(
            "Limits word choices to the most likely set. Lower is more predictable.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(22.dp))

        SettingSlider(
            label = "CPU Threads",
            valueLabel = "${settings.threads}",
            value = settings.threads.toFloat(),
            valueRange = ModelSettingsRepository.MIN_THREADS.toFloat()..ModelSettingsRepository.maxThreads().toFloat(),
            steps = (ModelSettingsRepository.maxThreads() - ModelSettingsRepository.MIN_THREADS - 1).coerceAtLeast(0),
            onValueChange = { viewModel.updateSettings(settings.copy(threads = it.toInt())) }
        )
        Text(
            "This device has ${ModelSettingsRepository.maxThreads()} CPU cores available.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BrainBgCard)
                .padding(14.dp)
        ) {
            Text(
                if (modelIsLoaded)
                    "A model is currently loaded. Temperature and Top-P apply to your next message automatically. Context Length and Threads need a reload to take effect."
                else
                    "Temperature and Top-P apply to your next message automatically. Context Length and Threads apply the next time you load a model.",
                color = BrainTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            if (modelIsLoaded) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.applySettingsToRunningModel() },
                    enabled = !viewModel.reloadInProgress,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (viewModel.reloadInProgress) "Reloading..." else "Apply & Reload Now")
                }
            }
        }

        // Rule 14 - a reload this screen itself triggered can fail (bad
        // file moved/deleted, OOM, etc.); surface that real failure here
        // too rather than only on the Models screen, since the user may
        // not navigate back there right away.
        val loadError = engineState as? EngineState.Error
        if (loadError != null) {
            Spacer(Modifier.height(10.dp))
            Text(loadError.message, color = BrainDangerRed, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = BrainTextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, color = BrainPurplePrimary, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = BrainPurplePrimary,
                activeTrackColor = BrainPurplePrimary,
                inactiveTrackColor = BrainBorder
            )
        )
    }
}

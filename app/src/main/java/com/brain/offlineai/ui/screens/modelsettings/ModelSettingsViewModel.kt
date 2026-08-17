package com.brain.offlineai.ui.screens.modelsettings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.data.settings.ModelSettings
import com.brain.offlineai.data.settings.ModelSettingsRepository
import com.brain.offlineai.engine.BrainEngine
import com.brain.offlineai.engine.EngineState
import com.brain.offlineai.engine.ModelFileManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen 11 from the mockup ("Model Settings" - context length,
 * temperature, top-p, thread-count). Real settings persisted via
 * [ModelSettingsRepository] - read by both this screen and, on the next
 * load/generate call, [com.brain.offlineai.ui.screens.models.ModelsViewModel]
 * / [com.brain.offlineai.ui.screens.chat.ChatViewModel].
 *
 * Rule 17 (endpoint correctness, not just existence): temperature/top-p
 * apply to the very next chat generation automatically (ChatViewModel reads
 * the repository fresh each send), but context length + thread count are
 * llama_context construction parameters (see llama_bridge.cpp
 * nativeLoadModel) - they can't change on an already-running context. If a
 * model is currently loaded when the user changes those two, [applySettings]
 * offers a real unload+reload with the new values instead of leaving the
 * screen looking like it did something when it silently didn't.
 */
class ModelSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = ModelSettingsRepository(application)
    private val fileManager = ModelFileManager(application)

    var settings by mutableStateOf(settingsRepository.getSettings())
        private set

    val engineState: StateFlow<EngineState> = BrainEngine.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineState.Unloaded)

    var reloadInProgress by mutableStateOf(false)
        private set

    fun updateSettings(newSettings: ModelSettings) {
        settings = newSettings
        settingsRepository.saveSettings(newSettings)
    }

    /**
     * If a model is currently loaded, unloads and reloads it with the
     * now-saved context length + thread count so the change takes effect
     * immediately rather than silently waiting for the user to notice
     * nothing changed. Temperature/top-p need no reload - already applied
     * (see class doc). No-op if nothing is loaded, since there's nothing
     * to reload; the saved settings still take effect on the next real load.
     */
    fun applySettingsToRunningModel() {
        val current = BrainEngine.state.value
        if (current !is EngineState.Loaded) return
        val installed = fileManager.getLastInstalledModel() ?: return
        reloadInProgress = true
        viewModelScope.launch {
            BrainEngine.unloadModel()
            BrainEngine.loadModel(
                installed.file.absolutePath,
                installed.name,
                nCtx = settings.contextLength,
                nThreads = settings.threads
            )
            reloadInProgress = false
        }
    }
}

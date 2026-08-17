package com.brain.offlineai.ui.screens.models

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.data.settings.ModelSettingsRepository
import com.brain.offlineai.engine.BrainEngine
import com.brain.offlineai.engine.EngineState
import com.brain.offlineai.engine.ImportProgress
import com.brain.offlineai.engine.InstalledModel
import com.brain.offlineai.engine.ModelFileManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelsViewModel(application: Application) : AndroidViewModel(application) {

    private val fileManager = ModelFileManager(application)
    // Phase 5: context length + thread count now come from the real Model
    // Settings screen instead of BrainEngine.loadModel()'s hardcoded
    // nCtx=2048/nThreads=4 defaults.
    private val settingsRepository = ModelSettingsRepository(application)

    val engineState: StateFlow<EngineState> = BrainEngine.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineState.Unloaded)

    var installedModel by mutableStateOf(fileManager.getLastInstalledModel())
        private set

    var importProgress by mutableStateOf<ImportProgress?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun importModel(uri: Uri, displayName: String) {
        viewModelScope.launch {
            fileManager.importModel(uri, displayName).collect { progress ->
                importProgress = progress
                when (progress) {
                    is ImportProgress.Done -> {
                        installedModel = InstalledModel(progress.file, progress.file.name, progress.file.length())
                        importProgress = null
                    }
                    is ImportProgress.Failed -> {
                        errorMessage = progress.reason
                        importProgress = null
                    }
                    else -> Unit
                }
            }
        }
    }

    fun loadModel() {
        val model = installedModel ?: return
        errorMessage = null
        val settings = settingsRepository.getSettings()
        viewModelScope.launch {
            BrainEngine.loadModel(model.file.absolutePath, model.name, nCtx = settings.contextLength, nThreads = settings.threads)
            val state = BrainEngine.state.value
            if (state is EngineState.Error) errorMessage = state.message
        }
    }

    fun unloadModel() {
        viewModelScope.launch { BrainEngine.unloadModel() }
    }

    fun deleteModel() {
        val model = installedModel ?: return
        viewModelScope.launch {
            if (BrainEngine.isLoaded) BrainEngine.unloadModel()
            fileManager.deleteModel(model)
            installedModel = null
        }
    }

    fun dismissError() {
        errorMessage = null
    }
}

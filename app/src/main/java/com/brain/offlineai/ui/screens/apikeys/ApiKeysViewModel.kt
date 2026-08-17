package com.brain.offlineai.ui.screens.apikeys

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.data.apikeys.ApiKeyEntity
import com.brain.offlineai.data.apikeys.ApiKeyRepository
import com.brain.offlineai.data.apikeys.ExpirationOption
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ApiKeysViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ApiKeyRepository(application)

    /** Real Room Flow -> StateFlow, same WhileSubscribed(5000) pattern as ModelsViewModel's engineState. */
    val keys: StateFlow<List<ApiKeyEntity>> = repository.observeKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun createKey(name: String, expiration: ExpirationOption, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            repository.createKey(name, expiration).fold(
                onSuccess = { entity ->
                    errorMessage = null
                    onCreated(entity.id)
                },
                onFailure = { errorMessage = it.message }
            )
        }
    }

    fun renameKey(id: String, newName: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.renameKey(id, newName).fold(
                onSuccess = {
                    errorMessage = null
                    onDone()
                },
                onFailure = { errorMessage = it.message }
            )
        }
    }

    fun revokeKey(id: String) {
        viewModelScope.launch { repository.revokeKey(id) }
    }

    fun deleteKey(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteKey(id)
            onDeleted()
        }
    }

    suspend fun getKey(id: String): ApiKeyEntity? = repository.getKey(id)

    fun dismissError() {
        errorMessage = null
    }
}

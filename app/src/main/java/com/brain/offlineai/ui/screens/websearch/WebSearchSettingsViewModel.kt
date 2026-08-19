package com.brain.offlineai.ui.screens.websearch

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.data.websearch.ConnectivityChecker
import com.brain.offlineai.data.websearch.TavilySearchClient
import com.brain.offlineai.data.websearch.WebSearchKeyStore
import kotlinx.coroutines.launch

enum class KeyValidationState { IDLE, VALIDATING, VALID, INVALID }

/**
 * Phase 22 - real save/validate/clear flow for the user's own Tavily API
 * key. A key is only ever persisted via [WebSearchKeyStore] after a real
 * [TavilySearchClient.validateKey] call has genuinely confirmed it works
 * (a real HTTP 200 from Tavily's own endpoint) - never saved unchecked,
 * and never faked as "valid" when there's no real connectivity to check
 * it (an honest, real error is shown instead).
 */
class WebSearchSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStore = WebSearchKeyStore(application)

    var hasStoredKey by mutableStateOf(keyStore.hasKey())
        private set

    var validationState by mutableStateOf(KeyValidationState.IDLE)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun validateAndSaveKey(rawKey: String) {
        val key = rawKey.trim()
        if (key.isEmpty()) {
            errorMessage = "Enter a Tavily API key first."
            return
        }
        if (!ConnectivityChecker.hasInternet(getApplication())) {
            errorMessage = "No internet connectivity right now - can't validate a key without a real connection."
            return
        }
        validationState = KeyValidationState.VALIDATING
        errorMessage = null
        viewModelScope.launch {
            val isValid = TavilySearchClient.validateKey(key)
            if (isValid) {
                keyStore.saveKey(key)
                hasStoredKey = true
                validationState = KeyValidationState.VALID
            } else {
                validationState = KeyValidationState.INVALID
                errorMessage = "Tavily rejected this key. Double-check it and try again."
            }
        }
    }

    fun clearKey() {
        keyStore.clearKey()
        hasStoredKey = false
        validationState = KeyValidationState.IDLE
        errorMessage = null
    }
}

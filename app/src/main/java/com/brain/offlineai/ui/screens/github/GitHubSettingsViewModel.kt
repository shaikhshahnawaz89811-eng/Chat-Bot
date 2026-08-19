package com.brain.offlineai.ui.screens.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.data.github.GitHubApiClient
import com.brain.offlineai.data.github.GitHubApiResult
import com.brain.offlineai.data.github.GitHubKeyStore
import com.brain.offlineai.data.websearch.ConnectivityChecker
import kotlinx.coroutines.launch

enum class GitHubTokenValidationState { IDLE, VALIDATING, VALID, INVALID }

/**
 * GitHub Hosting feature - real save/validate/clear flow for the user's
 * own GitHub Personal Access Token, same "never persisted unchecked"
 * convention [com.brain.offlineai.ui.screens.websearch.WebSearchSettingsViewModel]
 * already follows for the Tavily key: a token is only ever saved after a
 * real `GET /user` call has genuinely confirmed it works, and the real
 * GitHub username that call returns is cached for reuse by
 * [com.brain.offlineai.data.github.GitHubPublishRepository].
 */
class GitHubSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStore = GitHubKeyStore(application)

    var hasStoredToken by mutableStateOf(keyStore.hasToken())
        private set

    var storedUsername by mutableStateOf(keyStore.getCachedUsername())
        private set

    var validationState by mutableStateOf(GitHubTokenValidationState.IDLE)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun validateAndSaveToken(rawToken: String) {
        val token = rawToken.trim()
        if (token.isEmpty()) {
            errorMessage = "Enter a GitHub Personal Access Token first."
            return
        }
        if (!ConnectivityChecker.hasInternet(getApplication())) {
            errorMessage = "No internet connectivity right now - can't validate a token without a real connection."
            return
        }
        validationState = GitHubTokenValidationState.VALIDATING
        errorMessage = null
        viewModelScope.launch {
            when (val result = GitHubApiClient.getAuthenticatedUser(token)) {
                is GitHubApiResult.Success -> {
                    keyStore.saveToken(token)
                    keyStore.cacheUsername(result.value)
                    hasStoredToken = true
                    storedUsername = result.value
                    validationState = GitHubTokenValidationState.VALID
                }
                is GitHubApiResult.Failure -> {
                    validationState = GitHubTokenValidationState.INVALID
                    errorMessage = result.reason
                }
            }
        }
    }

    fun clearToken() {
        keyStore.clearToken()
        hasStoredToken = false
        storedUsername = null
        validationState = GitHubTokenValidationState.IDLE
        errorMessage = null
    }
}

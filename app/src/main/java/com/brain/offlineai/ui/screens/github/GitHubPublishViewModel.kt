package com.brain.offlineai.ui.screens.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.data.github.GitHubPublishRepository
import com.brain.offlineai.data.github.GitHubPublishStep
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * GitHub Hosting feature - owns one real publish run for
 * [com.brain.offlineai.ui.screens.github.GitHubPublishScreen]. Every state
 * exposed here is a real [GitHubPublishStep] emitted by
 * [GitHubPublishRepository.publish] - this ViewModel never fabricates a
 * step the repository didn't actually reach, same "own the bytes"
 * standard the rest of the artifact pipeline already holds itself to.
 *
 * Custom-domain feature (additive) - [customDomain] is plain UI text
 * state, always passed straight through to
 * [GitHubPublishRepository.publish] exactly as typed; all real
 * validation/normalization happens once, in the repository (see
 * `sanitizeDomain` there), never duplicated here.
 */
class GitHubPublishViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GitHubPublishRepository(application)

    val hasStoredToken: Boolean get() = repository.hasStoredToken()

    var step by mutableStateOf<GitHubPublishStep>(GitHubPublishStep.Idle)
        private set

    var repoName by mutableStateOf("")
        private set

    var customDomain by mutableStateOf("")
        private set

    private var publishJob: Job? = null

    fun updateRepoName(value: String) {
        repoName = value
    }

    fun updateCustomDomain(value: String) {
        customDomain = value
    }

    fun publish(files: List<Pair<String, String>>, makePrivate: Boolean) {
        if (publishJob?.isActive == true) return
        publishJob = viewModelScope.launch {
            repository.publish(files, repoName, makePrivate, customDomain.takeIf { it.isNotBlank() }).collect { newStep ->
                step = newStep
            }
        }
    }

    fun reset() {
        publishJob?.cancel()
        step = GitHubPublishStep.Idle
    }
}

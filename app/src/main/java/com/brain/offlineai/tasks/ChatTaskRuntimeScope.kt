package com.brain.offlineai.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide scope for user-started long-running chat work.
 *
 * A ChatViewModel is a UI owner, not the owner of a long-running task.  The
 * previous implementation launched the real llama.cpp job in
 * viewModelScope, which meant navigation could cancel a valid user task.
 * This scope keeps the task alive while the Android process itself remains
 * alive; the persisted execution row handles recovery after process death.
 */
object ChatTaskRuntimeScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

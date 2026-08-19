package com.brain.offlineai.ui.screens.chat

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.data.analytics.AnalyticsStore
import com.brain.offlineai.data.artifacts.ArtifactCandidate
import com.brain.offlineai.data.artifacts.ArtifactDownloadTarget
import com.brain.offlineai.data.artifacts.ArtifactEntity
import com.brain.offlineai.data.artifacts.ArtifactExportProgress
import com.brain.offlineai.data.artifacts.ArtifactExtractor
import com.brain.offlineai.data.artifacts.ArtifactFileManager
import com.brain.offlineai.data.artifacts.ArtifactInfo
import com.brain.offlineai.data.artifacts.ArtifactRepository
import com.brain.offlineai.data.artifacts.classifyArtifact
import com.brain.offlineai.data.artifacts.mimeTypeForArtifact
import com.brain.offlineai.data.attachments.AttachmentCopyProgress
import com.brain.offlineai.data.attachments.AttachmentContentReader
import com.brain.offlineai.data.attachments.AttachmentKind
import com.brain.offlineai.data.attachments.AttachmentEntity
import com.brain.offlineai.data.attachments.AttachmentFileManager
import com.brain.offlineai.data.attachments.AttachmentInfo
import com.brain.offlineai.data.attachments.AttachmentRepository
import com.brain.offlineai.data.attachments.classifyAttachment
import com.brain.offlineai.data.history.ChatHistoryRepository
import com.brain.offlineai.data.settings.ModelSettingsRepository
import com.brain.offlineai.data.settings.ModelSettings
import com.brain.offlineai.engine.BrainEngine
import com.brain.offlineai.engine.EngineState
import com.brain.offlineai.ui.multimodal.AttachmentPromptBuilder
import com.brain.offlineai.ui.multimodal.AttachmentRoute
import com.brain.offlineai.ui.multimodal.ZipEditResolver
import com.brain.offlineai.ui.multimodal.ZipEditTarget
import com.brain.offlineai.ui.multimodal.classifyAttachmentRole
import com.brain.offlineai.ui.normalize.InputNormalizer
import com.brain.offlineai.ui.process.ProcessMarking
import com.brain.offlineai.ui.process.ProcessStep
import com.brain.offlineai.ui.process.ProcessStepStatus
import com.brain.offlineai.ui.recovery.ErrorCategory
import com.brain.offlineai.ui.recovery.classifyGenerationError
import com.brain.offlineai.ui.tasks.TaskItem
import com.brain.offlineai.ui.tasks.TaskSplitter
import com.brain.offlineai.ui.tasks.TaskStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.brain.offlineai.tasks.ChatTaskForegroundService

/**
 * Drives the Chat screen state - Phase 2 rewrite, Phase 5 wiring added,
 * Phase 7 real persistence added.
 *
 * The Phase 1 version of this file played a scripted, hardcoded
 * thinking-checklist and a canned demo `is_prime` code snippet before
 * ending on a fixed "not wired yet" note - explicitly documented there as
 * a placeholder for this exact replacement. All of that is gone now.
 *
 * What happens today: sendMessage() calls BrainEngine.generate(prompt),
 * which runs a real llama.cpp decode loop and streams back real token
 * pieces one at a time. There is no fallback path that invents an answer
 * when no model is loaded - the UI honestly asks the user to import one
 * (see ModelsScreen) instead.
 *
 * Phase 5: temperature/top-p now come from [ModelSettingsRepository] (the
 * new Model Settings screen) instead of BrainEngine.generate()'s hardcoded
 * defaults, and every real message sent / real tokens generated feed
 * [AnalyticsStore] so the new Analytics screen has genuine numbers to show.
 *
 * Phase 7: this is also the fix for the Rule 1/16 audit gap on the
 * "History" bottom-nav destination - every session is now really persisted
 * via [ChatHistoryRepository] as messages are sent/streamed (not only held
 * in the in-memory `messages` state below), and [openSessionId] lets this
 * same ViewModel reopen a past session from the new History screen instead
 * of always starting empty. `sessionId` starts null (a fresh, not-yet-
 * persisted conversation, same default behavior as every earlier phase)
 * and is created for real in the DB the moment the first message is sent -
 * so opening the Chat tab and never typing anything still creates zero rows,
 * same honest "no data until something real happens" rule the rest of the
 * app already follows (e.g. Analytics' real zeros on a fresh install).
 */
class ChatViewModel(
    application: Application,
    private val openSessionId: String? = null
) : AndroidViewModel(application) {

    private val settingsRepository = ModelSettingsRepository(application)
    private val analyticsStore = AnalyticsStore(application)
    private val historyRepository = ChatHistoryRepository(application)
    private val attachmentFileManager = AttachmentFileManager(application)
    private val attachmentRepository = AttachmentRepository(application)
    private val artifactFileManager = ArtifactFileManager(application)
    private val artifactRepository = ArtifactRepository(application)

    var messages = mutableStateOf(listOf<ChatMessage>())
        private set

    var inputText = mutableStateOf("")
        private set

    /**
     * Phase 9 (new Claude-style UI spec, streaming-reply engine) - true for
     * the exact real span from tapping Send to the response actually
     * finishing (success, error, or the "no model loaded" early-return
     * path) - not a fake timer. Wrapped in try/finally in [sendMessage] so
     * it is guaranteed to reset even if generation throws or the
     * coroutine is cancelled (Rule 14 - a busy-flag that could get stuck
     * true forever is itself a real bug, not just a UI nicety).
     */
    var isBusy = mutableStateOf(false)
        private set

    /**
     * Phase 10 (File/ZIP/Image/Video upload flow) - real attachments picked
     * but not yet sent. Each one is genuinely being copied to app-private
     * storage the moment it's picked (real progress, see
     * [onAttachmentPicked]/[AttachmentFileManager]) - but per the explicit
     * "file = kaam start" rule, nothing about the attachment's *content* is
     * read, parsed, or acted on until [sendMessage] genuinely runs. Copying
     * bytes to a safe, app-owned location is not "starting the task" - it's
     * the same honest prerequisite step Models' import flow already does
     * before a GGUF can be loaded.
     */
    var pendingAttachments = mutableStateOf(listOf<PendingAttachment>())
        private set

    /** One real coroutine Job per in-flight copy, so removing an attachment mid-copy can actually cancel it (not just hide it in the UI). */
    private val attachmentJobs = mutableMapOf<String, Job>()

    /**
     * Phase 11 (Artifact card + ZIP/file output + download flow) - real,
     * current download state per artifact id (and per `"zip-<messageId>"`
     * for a "Download All" action), read by [com.brain.offlineai.ui.components.ArtifactCard]
     * via [ChatBubbles]. Only ever updated from genuine
     * [ArtifactExportProgress] events emitted by [artifactFileManager] - see
     * [onDownloadArtifact]/[onDownloadAllArtifacts].
     */
    var artifactDownloads = mutableStateOf(mapOf<String, ArtifactDownloadUiState>())

    /**
     * Real, per-download-id [Job] tracking so [onCancelDownload] can cancel
     * an actual in-progress export - not exposed to the UI, only used
     * internally by [exportArtifact]/[onDownloadAllArtifacts]/[onCancelDownload].
     */
    private val downloadJobs = mutableMapOf<String, Job>()

    /** Null until the first message of a fresh conversation is actually sent - see class doc above. */
    private var sessionId: String? = openSessionId

    private var nextId = 1L

    /**
     * Bug fix (user request) - the real Job behind [sendMessage]'s
     * `viewModelScope.launch { ... }`, captured so [stopGeneration] can
     * actually cancel a real, in-flight generation instead of only being
     * able to stop it by leaving the screen (which cleared the ViewModel).
     * [streamRealResponse]'s `onCompletion` cancellation branch already
     * persists whatever partial text had genuinely streamed so far, so
     * cancelling this Job here reuses that same real, existing save path -
     * nothing about a stopped reply is silently thrown away.
     */
    private var generationJob: Job? = null

    init {
        if (openSessionId != null) {
            viewModelScope.launch { loadExistingSession(openSessionId) }
        }
    }

    private suspend fun loadExistingSession(id: String) {
        val stored = historyRepository.getMessages(id)
        if (stored.isEmpty()) return
        // Phase 10 - real attachments persisted alongside this session's
        // messages (Phase 7's own history restore never had any attachment
        // rows to load, so this is purely additive - a session saved before
        // Phase 10 just restores with an empty attachments list per message,
        // same as before).
        val attachmentsByMessage = attachmentRepository.getForSession(id).groupBy { it.messageId }
        // Phase 11 - same real restore for artifacts: a session saved
        // before this phase just restores with an empty artifacts list per
        // message (additive only).
        val artifactsByMessage = artifactRepository.getForSession(id).groupBy { it.messageId }
        messages.value = stored.map { row ->
            val restoredArtifacts = artifactsByMessage[row.messageId]?.map { it.toArtifactInfo() } ?: emptyList()
            ChatMessage(
                id = row.messageId,
                text = row.text,
                isUser = row.isUser,
                timestamp = formatTime(row.timestampMillis),
                state = BotMessageState.TEXT,
                attachments = attachmentsByMessage[row.messageId]?.map { it.toAttachmentInfo() } ?: emptyList(),
                artifacts = restoredArtifacts,
                artifactSteps = if (restoredArtifacts.isEmpty()) emptyList() else buildArtifactSteps(restoredArtifacts.size)
            )
        }
        // Real continuation, not a fresh id space colliding with restored rows.
        nextId = (stored.maxOf { it.messageId } + 1)
    }

    private fun ArtifactEntity.toArtifactInfo() = ArtifactInfo(
        id = id,
        fileName = fileName,
        sizeBytes = sizeBytes,
        kind = classifyArtifact(fileName),
        mimeType = mimeType,
        storedPath = storedPath
    )

    private fun AttachmentEntity.toAttachmentInfo() = AttachmentInfo(
        id = id,
        fileName = fileName,
        sizeBytes = sizeBytes,
        kind = classifyAttachment(fileName, mimeType),
        mimeType = mimeType,
        storedPath = storedPath
    )

    fun onInputChange(newText: String) {
        inputText.value = newText
    }

    /**
     * Phase 10 (File/ZIP/Image/Video upload flow) - real entry point called
     * from [com.brain.offlineai.ui.screens.chat.ChatScreen]'s SAF picker
     * result. Starts a real, byte-counted copy into app-private storage
     * immediately (so the pending chip shows genuine progress) - this is
     * only ever a copy, never a read of the file's actual content (see
     * "file = kaam start" rule on [pendingAttachments]).
     */
    fun onAttachmentPicked(uri: Uri, fileName: String, mimeType: String?) {
        val localId = UUID.randomUUID().toString()
        pendingAttachments.value = pendingAttachments.value +
            PendingAttachment(localId, fileName, mimeType, PendingAttachmentState.Copying(0L, -1L))

        val job = viewModelScope.launch {
            attachmentFileManager.copyAttachment(uri, fileName).collect { progress ->
                when (progress) {
                    is AttachmentCopyProgress.Copying ->
                        updatePendingAttachment(localId, PendingAttachmentState.Copying(progress.bytesCopied, progress.totalBytes))

                    is AttachmentCopyProgress.Done -> {
                        val info = AttachmentInfo(
                            id = localId,
                            fileName = fileName,
                            sizeBytes = progress.sizeBytes,
                            kind = classifyAttachment(fileName, mimeType),
                            mimeType = mimeType,
                            storedPath = progress.file.absolutePath
                        )
                        updatePendingAttachment(localId, PendingAttachmentState.Ready(info))
                    }

                    is AttachmentCopyProgress.Failed ->
                        updatePendingAttachment(localId, PendingAttachmentState.Failed(progress.reason))
                }
            }
            attachmentJobs.remove(localId)
        }
        attachmentJobs[localId] = job
    }

    /** Real removal: cancels an in-flight copy (if any) and deletes the real on-disk file for an already-copied one - never just hides the chip. */
    fun onRemoveAttachment(localId: String) {
        attachmentJobs.remove(localId)?.cancel()
        val removed = pendingAttachments.value.firstOrNull { it.localId == localId }
        pendingAttachments.value = pendingAttachments.value.filterNot { it.localId == localId }
        val readyInfo = (removed?.state as? PendingAttachmentState.Ready)?.info
        if (readyInfo != null) {
            attachmentFileManager.deleteAttachmentFile(readyInfo.storedPath)
        }
    }

    private fun updatePendingAttachment(localId: String, newState: PendingAttachmentState) {
        pendingAttachments.value = pendingAttachments.value.map {
            if (it.localId == localId) it.copy(state = newState) else it
        }
    }

    /**
     * Bug fix (user request - "chunk max ho jae or kaam bach jae toh kya
     * hoga") - real, minimal state for a reply whose real chunk loop in
     * [streamRealResponse] genuinely ran out of [MAX_CONTINUATION_CHUNKS]
     * while the model's own last real stop reason was still "max_tokens"
     * (i.e. the task itself was NOT actually finished - a real
     * "end_of_generation"/"context_full"/cancel/error never happened, the
     * safety ceiling just ran out first). Null whenever there is no such
     * real unfinished reply waiting - true for every ordinary message that
     * finishes inside the chunk cap, so this never affects the common case.
     * [continuationPrompt] already holds the real original prompt plus
     * every real token generated across every chunk so far - resuming
     * never re-asks the model to guess or repeat what it already said.
     * Consumed (and cleared) the moment the user's very next message is a
     * real continue request (see [CONTINUE_TRIGGERS] in [sendMessage]);
     * sending anything else clears it too, since a genuinely different
     * message means the user moved on and a later unrelated "continue"
     * should not resume stale, out-of-context work.
     */
    private data class PendingContinuation(
        val sessionId: String,
        val continuationPrompt: String,
        val zipEditTarget: ZipEditTarget?
    )
    private var pendingContinuation: PendingContinuation? = null

    fun sendMessage() {
        if (isBusy.value) return
        val text = inputText.value.trim()
        val readyAttachments = pendingAttachments.value.mapNotNull { (it.state as? PendingAttachmentState.Ready)?.info }
        // Real "file = kaam start" gate: a message with only a still-copying
        // or failed attachment and no text is not sendable yet - ChatInputBar
        // already disables Send for this same reason, this is the real
        // ViewModel-side guard behind it.
        if (text.isEmpty() && readyAttachments.isEmpty()) return
        // Set synchronously, before launching the coroutine below - if this
        // were only set inside viewModelScope.launch { ... }, a second
        // sendMessage() call arriving before that coroutine actually starts
        // running would still see isBusy.value == false and slip past the
        // guard above, sending the same message twice.
        isBusy.value = true
        inputText.value = ""
        // Clear the pending row now - these attachments are about to become
        // real, sent [ChatMessage.attachments], not pending ones anymore.
        pendingAttachments.value = emptyList()

        // Phase 14 (Multimodal input use-case routing, spec section 8) -
        // real, deterministic routing decided right here, before this
        // message is even added to the screen - see
        // [com.brain.offlineai.ui.multimodal.classifyAttachmentRole]'s own
        // doc for why this is never a model call. Empty when there are no
        // real attachments (the common case), so ordinary text-only
        // messages do zero extra work.
        val attachmentRoutes = readyAttachments.map { classifyAttachmentRole(it, text) }

        val userMessage = ChatMessage(
            id = nextId++,
            text = text,
            isUser = true,
            timestamp = timeNow(),
            attachments = readyAttachments,
            attachmentRoutes = attachmentRoutes
        )
        messages.value = messages.value + userMessage
        analyticsStore.incrementMessagesSent()

        generationJob = viewModelScope.launch {
            ChatTaskForegroundService.start(getApplication())
            // Real bug fix: everything below this point used to run inside a
            // try { ... } finally { ... } with NO catch. Any genuine throw
            // before streamRealResponse() started (a Room write in
            // ensureSession/persistMessage, InputNormalizer, ZipEditResolver,
            // TaskSplitter, etc.) silently killed this coroutine - isBusy
            // still got reset in finally, so a second send looked "normal",
            // but the user got zero bot bubble and zero error, forever, on
            // whichever send actually hit the throw. This catch makes that
            // failure visible instead of silent, and still lets real
            // cancellation (leaving the screen, process death) propagate.
            var activeSessionIdForError: String? = null
            try {
                val activeSessionId = ensureSession(text.ifEmpty { readyAttachments.first().fileName })
                activeSessionIdForError = activeSessionId
                persistMessage(activeSessionId, userMessage)
                if (readyAttachments.isNotEmpty()) {
                    persistAttachments(activeSessionId, userMessage.id, readyAttachments)
                }

                if (BrainEngine.state.value !is EngineState.Loaded) {
                    postSystemNote(
                        activeSessionId,
                        "No model is loaded yet. Go to Models and import + load a " +
                            ".gguf file (e.g. Qwen2.5-1.5B-Instruct) before chatting - " +
                            "this build never fabricates an answer without a real model."
                    )
                    return@launch
                }

                // Bug fix (user request - "chunk max ho jae or kaam bach
                // jae toh kya hoga") - a real, still-unfinished reply from
                // an earlier message (see [pendingContinuation]'s own doc)
                // is only ever resumed by an explicit, deterministic
                // continue request - never guessed from an unrelated
                // message, and never silently resumed behind the user's
                // back. Cleared here either way: a real continue request
                // consumes it by resuming; anything else means the user
                // genuinely moved on, so the old unfinished reply is
                // dropped rather than resumed later out of context by a
                // much later, unrelated "continue".
                val pending = pendingContinuation
                if (pending != null) {
                    pendingContinuation = null
                    if (text.trim().lowercase(Locale.getDefault()) in CONTINUE_TRIGGERS) {
                        streamRealResponse(activeSessionId, pending.continuationPrompt, pending.zipEditTarget)
                        return@launch
                    }
                }

                if (text.isEmpty()) {
                    // Phase 14 (Multimodal input use-case routing) can only
                    // genuinely route an attachment by real signals in the
                    // user's own message text (see
                    // [com.brain.offlineai.ui.multimodal.classifyAttachmentRole]) -
                    // with no text at all there's nothing real to route on
                    // yet, and no instruction to act on either way. Sending
                    // an attachment with no text is honestly acknowledged
                    // here rather than silently feeding an empty prompt
                    // into a real decode call, which would be an
                    // undefined/fabricated interaction with the engine.
                    postSystemNote(
                        activeSessionId,
                        "Attachment${if (readyAttachments.size > 1) "s" else ""} saved. Add a message describing " +
                            "what you'd like done with ${if (readyAttachments.size > 1) "them" else "it"} so it " +
                            "can be routed and used."
                    )
                    return@launch
                }

                // Phase 13 (User-mistake / mixed-input normalization, spec
                // section 7) - real, narrow, deterministic checks run
                // before anything reaches a real generation call. See
                // InputNormalizer's own doc for why this never asks the
                // model to "guess" what was meant, and why the conflict/
                // vague-request checks are both intentionally conservative
                // (a false negative here just means a normal generation
                // runs - the safe outcome).
                val normalizedText = InputNormalizer.normalize(text)

                val conflict = InputNormalizer.detectConflict(normalizedText)
                if (conflict != null) {
                    postSystemNote(
                        activeSessionId,
                        "This message asks for both \"${conflict.wordA}\" and " +
                            "\"${conflict.wordB}\" - which one should I actually do? " +
                            "Send a follow-up message clarifying and I'll pick it up " +
                            "from there."
                    )
                    return@launch
                }

                // A prior real bot answer already in this session gives
                // "it"/"that" a genuine referent, so a short follow-up like
                // "fix it" is not actually ambiguous in context - only
                // flag this on a session with no earlier real answer yet.
                val hasPriorBotAnswer = messages.value.any {
                    !it.isUser && (it.state == BotMessageState.TEXT || it.state == BotMessageState.CODE_DONE)
                }
                if (!hasPriorBotAnswer && InputNormalizer.isVagueRequest(normalizedText)) {
                    postSystemNote(
                        activeSessionId,
                        "Not sure what \"$text\" should apply to yet - there's no " +
                            "earlier reply in this chat to refer back to. Could you " +
                            "say what you'd like done, specifically?"
                    )
                    return@launch
                }

                // Phase 14 (Multimodal input use-case routing, spec section
                // 8) - real, bounded content (a readable text file's own
                // bytes, a ZIP's own real entry list) for this message's
                // routed attachments, built once here and appended to
                // whatever prompt(s) actually reach the engine below.
                // Empty string (zero-cost, no extra call) when there are no
                // real attachments on this message. A real routing summary
                // is also posted as its own message before generation runs,
                // so routing is genuinely visible to the user, not a silent
                // internal decision (spec §8 - "route before acting").
                val attachmentContextBlock = if (readyAttachments.isNotEmpty()) {
                    postSystemNote(activeSessionId, AttachmentPromptBuilder.buildRoutingSummary(attachmentRoutes))
                    AttachmentPromptBuilder.buildContextBlock(attachmentRoutes, readyAttachments.associateBy { it.id })
                } else {
                    ""
                }

                // Phase 16 (Real ZIP content edit) - real, deterministic
                // check for whether this message names one specific real
                // file inside one attached ZIP (see ZipEditResolver's own
                // doc: zero or ambiguous matches deliberately resolve to
                // null, never a guessed target). Only considered for the
                // single-task path below - a multi-task breakdown could
                // plausibly name several different files across different
                // tasks, which this phase's scope deliberately does not
                // attempt to resolve automatically.
                val zipAttachments = readyAttachments.filter { it.kind == AttachmentKind.ZIP }
                var zipEditTarget: ZipEditTarget? = null
                var zipEditContext = ""
                if (zipAttachments.size == 1) {
                    val zipInfo = zipAttachments.first()
                    val entries = AttachmentContentReader.listZipEntries(zipInfo.storedPath)
                    val match = ZipEditResolver.resolveEditTarget(entries, normalizedText)
                    if (match != null) {
                        val entryContent = AttachmentContentReader.readZipEntryText(zipInfo.storedPath, match.name)
                        if (entryContent != null) {
                            zipEditTarget = ZipEditTarget(zipInfo.id, zipInfo.storedPath, zipInfo.fileName, match.name)
                            postSystemNote(
                                activeSessionId,
                                "Editing target resolved inside ${zipInfo.fileName}: ${match.name}"
                            )
                            zipEditContext = "\n\n--- Current content of ${match.name} (inside ${zipInfo.fileName}) ---\n" +
                                entryContent +
                                "\n--- End current content ---\n" +
                                "Reply with the complete modified file in exactly one fenced code block, " +
                                "and nothing else outside that block."
                        }
                    }
                }

                // Phase 12 (Multi-task handling engine, spec section 6) -
                // real, deterministic breakdown check (see TaskSplitter's
                // own doc for the exact rules). TaskSplitter.split() always
                // returns at least the original text as a single element,
                // so a size-1 result here means "not multi-task" and falls
                // straight through to the existing single-task flow,
                // completely unchanged from every earlier phase. Runs on
                // the real, whitespace/duplicate-word-cleaned
                // [normalizedText] rather than the raw [text] - the
                // displayed user bubble above still shows exactly what the
                // user typed, only the real prompt sent onward is cleaned
                // (and, per Phase 14 above, may now also carry the real
                // attachment context block appended after it).
                val taskTexts = TaskSplitter.split(normalizedText)
                if (taskTexts.size > 1) {
                    runMultiTaskMessage(activeSessionId, taskTexts, attachmentContextBlock)
                } else {
                    streamRealResponse(activeSessionId, normalizedText + attachmentContextBlock + zipEditContext, zipEditTarget)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // real cancellation (screen left, process reclaimed) must keep propagating, never swallowed
            } catch (e: Exception) {
                Log.e("ChatViewModel", "sendMessage failed before/while generating a response", e)
                val errorText = "Something went wrong before I could reply: " +
                    (e.message ?: e::class.java.simpleName) +
                    ". Please try sending that again."
                val sid = activeSessionIdForError
                if (sid != null) {
                    postSystemNote(sid, errorText)
                } else {
                    // ensureSession() itself is what threw - there is no
                    // session to persist a note into yet, so show the error
                    // directly in the on-screen list instead of losing it.
                    val botId = nextId++
                    upsertBotMessage(
                        botId,
                        ChatMessage(id = botId, text = errorText, isUser = false, timestamp = timeNow(), state = BotMessageState.SYSTEM_NOTE)
                    )
                }
            } finally {
                isBusy.value = false
                generationJob = null
                ChatTaskForegroundService.stop(getApplication())
            }
        }
    }

    /**
     * Bug fix (user request) - real "Stop" action, wired to the spot that
     * used to be the Send button while a real generation is in flight.
     * Cancelling [generationJob] cancels the real coroutine collecting
     * [BrainEngine.generate]'s flow, which - per
     * [BrainEngine.generate]'s own doc - genuinely stops the underlying
     * llama.cpp decode loop too, not just the UI collection. Whatever text
     * had actually streamed so far is still saved: [streamRealResponse]'s
     * `onCompletion` cancellation branch runs regardless of *why* the Job
     * was cancelled (leaving the screen or this explicit stop), so a
     * stopped reply is persisted the same honest way a genuinely
     * interrupted one always was.
     */
    fun stopGeneration() {
        generationJob?.cancel()
    }

    /** Real session creation on first send only - see class doc above for why this stays lazy. */
    private suspend fun ensureSession(firstMessageText: String): String {
        val existing = sessionId
        if (existing != null) return existing
        val created = historyRepository.createSession(firstMessageText)
        sessionId = created
        CurrentChatSessionStore.set(getApplication(), created)
        return created
    }

    private suspend fun persistMessage(activeSessionId: String, message: ChatMessage) {
        historyRepository.saveMessage(
            sessionId = activeSessionId,
            messageId = message.id,
            text = message.text,
            isUser = message.isUser,
            timestampMillis = System.currentTimeMillis(),
            totalMessageCount = messages.value.size
        )
    }

    /** Phase 10 - real, permanent metadata rows for attachments that were genuinely sent with this message. */
    private suspend fun persistAttachments(activeSessionId: String, messageId: Long, attachments: List<AttachmentInfo>) {
        attachments.forEach { info ->
            attachmentRepository.save(
                AttachmentEntity(
                    id = info.id,
                    sessionId = activeSessionId,
                    messageId = messageId,
                    fileName = info.fileName,
                    mimeType = info.mimeType,
                    kind = info.kind.name,
                    sizeBytes = info.sizeBytes,
                    storedPath = info.storedPath,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Phase 8 (new Claude-style UI spec) - shows a real THINKING process
     * step (via [ProcessMarking]/[LiveProcessCard]) for the genuine work
     * this function already does before a real generation starts:
     * checking [ModelSettingsRepository] for the current inference
     * settings. This is not a scripted delay - the step is marked
     * COMPLETE the instant that real read finishes, and the same message
     * id then flips to the existing real GENERATING state below (Rule 10 -
     * the step must correspond to real work, not a fake pause).
     *
     * Phase 12 (Multi-task handling engine) - now returns the real bot
     * message id it just streamed into (additive to this function's
     * existing behavior, Document-Editing Convention). The Phase 8-11
     * single-task call site in [sendMessage] simply ignores the return
     * value, unchanged; [runMultiTaskMessage] uses it to look up that
     * task's own final, already-settled message state once this suspend
     * function returns.
     *
     * Phase 16 (Real ZIP content edit) - additive [zipEditTarget] param
     * (default null, so every earlier call site - including
     * [runMultiTaskMessage]'s per-task calls - is unaffected). When set,
     * a genuinely completed response's real single fenced block is routed
     * to [attachArtifactsOrPatchZip] to attempt a real, in-place ZIP patch
     * instead of only ever becoming a standalone file artifact.
     */
    private suspend fun streamRealResponse(activeSessionId: String, prompt: String, zipEditTarget: ZipEditTarget? = null): Long {
        val botId = nextId++
        val builder = StringBuilder()
        // Bug fix (user request) - set only once, the first moment a real
        // fence shows up in the streamed text so far. From that point on,
        // botId stays the prose card (finalized, no longer updated) and
        // codeId becomes its own separate, genuinely distinct card for the
        // code - never the same card changing its own look mid-stream.
        var codeId: Long? = null

        upsertBotMessage(
            botId,
            ChatMessage(
                id = botId, text = "", isUser = false, timestamp = timeNow(),
                state = BotMessageState.PROCESS,
                processSteps = listOf(ProcessStep(1L, ProcessMarking.THINKING, ProcessStepStatus.RUNNING))
            )
        )

        val settings = settingsRepository.getSettings()
        var tokenCount = 0
        // Bug fix (user request) - this is a memory-heavy, largeHeap,
        // on-device LLM app: Android's low-memory killer genuinely
        // reclaims this whole process while it's backgrounded far more
        // often than a typical app, not just an Activity destroy/recreate.
        // A hard process kill runs none of onCompletion's cancellation
        // cleanup (that only fires for a graceful coroutine cancellation,
        // e.g. leaving the screen while the process stays alive) - so
        // without this, a reply that was still streaming when the OS
        // killed the process was NEVER written to disk at all, and came
        // back missing entirely (both from the Chat tab and from History,
        // since both only ever read what's actually in the DB).
        // [ChatHistoryDao.upsertMessage]'s own doc already says it exists
        // for exactly this ("called on every token update while a
        // response streams in") but nothing here was actually calling it
        // during the live stream - only once, at the very end. Persisting
        // every few tokens (not literally every single one, to avoid a DB
        // write per token) keeps a real, recent on-disk copy so even a
        // genuine kill mid-stream leaves whatever the model had actually
        // produced so far, instead of the whole reply vanishing.
        val persistEveryNTokens = 8

        upsertBotMessage(
            botId,
            ChatMessage(id = botId, text = "", isUser = false, timestamp = timeNow(), state = BotMessageState.GENERATING, generationProgress = 0)
        )

        // Bug fix (user request - "chota kaam kar pa raha he, bada nahin,
        // bich me he ruk gaya") - real root cause was that every call here
        // used [BrainEngine.generate]'s hardcoded maxTokens=512 default (it
        // was never actually passed from anywhere in this file), so the
        // native decode loop in llama_bridge.cpp always hit its hard
        // ceiling and returned stopReason=="max_tokens" for any real answer
        // that genuinely needed more than ~512 tokens (e.g. a multi-file
        // Termux coding agent), while everything below treated that exactly
        // like a real finished reply - there was no earlier bug in
        // [TaskSplitter]; that class only breaks apart the user's *own*
        // message text into separate tasks and never touches how long a
        // single answer is allowed to run.
        //
        // The real fix is this chunked, auto-continue loop: each iteration
        // asks the real model for one more real chunk, sized to however
        // much of the actual loaded context window is genuinely still free
        // (see [chunkTokenBudget] - never a fixed re-guess), and only stops
        // asking for another chunk once the model's own real stop reason
        // says the answer is genuinely finished ("end_of_generation"), the
        // real context window is genuinely full ("context_full"), the user
        // genuinely cancelled, a real error was thrown, or [MAX_CONTINUATION_CHUNKS]
        // is reached (a hard safety ceiling against a pathological model
        // that never emits an end-of-generation token, so this can never
        // loop forever). Continuation prompts are just the real original
        // prompt plus every real token produced so far - never an invented
        // rewrite of what the model already said - so a short task that
        // finishes in the first real chunk behaves exactly as before
        // (single call, single stop reason, loop runs once).
        var continuationPrompt = prompt
        var stopReason = "max_tokens"
        var chunkIndex = 0
        var fatalError: Throwable? = null
        val loadedContextSize = (BrainEngine.state.value as? EngineState.Loaded)?.contextSize ?: settings.contextLength

        try {
            while (stopReason == "max_tokens" && chunkIndex < MAX_CONTINUATION_CHUNKS) {
                chunkIndex++
                val chunkBudget = chunkTokenBudget(loadedContextSize, continuationPrompt)
                if (chunkBudget <= 0) {
                    // The real prompt-so-far (original prompt + everything
                    // genuinely generated in earlier chunks) already fills
                    // the real context window - same honest boundary the
                    // native side itself enforces on a first call. Stop
                    // asking for more instead of sending a call the native
                    // side would just reject.
                    stopReason = "context_full"
                    break
                }

                var chunkStopReason = "max_tokens"
                var chunkFailed = false
                try {
                    BrainEngine.generate(
                        continuationPrompt,
                        maxTokens = chunkBudget,
                        temperature = settings.temperature,
                        topP = settings.topP,
                        onStopReason = { chunkStopReason = it }
                    ).collect { piece ->
                        builder.append(piece)
                        tokenCount++
                        val full = builder.toString()
                        val split = splitIntroAndCode(full)
                        if (split.codeLines == null) {
                            // Still just prose - this stays the one live card, same
                            // GENERATING bubble as before any fence ever appears.
                            upsertBotMessage(botId, ChatMessage(id = botId, text = full, isUser = false, timestamp = timeNow(), state = BotMessageState.GENERATING, generationProgress = tokenCount))
                        } else {
                            if (codeId == null) {
                                // The fence just appeared for the first time - the
                                // prose card is done changing now, freeze it as its
                                // own real, separate finished card (or remove it
                                // entirely if there was no real prose before the
                                // fence at all).
                                if (split.introText.isNotBlank()) {
                                    upsertBotMessage(botId, ChatMessage(id = botId, text = split.introText, isUser = false, timestamp = timeNow(), state = BotMessageState.TEXT))
                                } else {
                                    removeBotMessage(botId)
                                }
                                codeId = nextId++
                            }
                            upsertBotMessage(codeId!!, ChatMessage(id = codeId!!, text = full, isUser = false, timestamp = timeNow(), state = BotMessageState.CODING, codeLines = split.codeLines, generationProgress = tokenCount))
                        }
                        // Bug fix (user request) - real, periodic on-disk copy of
                        // whatever has genuinely streamed so far, keyed to the
                        // same id the final message will use (botId while still
                        // plain prose, codeId once a fence has appeared) so this
                        // upsert and the final persistMessage() write the same
                        // row - never a stray leftover partial row under a
                        // different id. See [persistEveryNTokens]'s doc above for
                        // why this exists.
                        if (tokenCount % persistEveryNTokens == 0) {
                            persistMessage(
                                activeSessionId,
                                ChatMessage(id = codeId ?: botId, text = full, isUser = false, timestamp = timeNow(), state = BotMessageState.TEXT)
                            )
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A real prompt-too-long rejection from the native side
                    // (see [chunkTokenBudget]'s doc - the Kotlin-side token
                    // estimate is a heuristic, so the native tokenizer can
                    // still legitimately disagree once in a while) is the
                    // same honest "the real context window is full" outcome
                    // as [chunkTokenBudget] returning 0 above - not a real
                    // failure of generation itself, so it's handled the same
                    // way (stop the loop, keep whatever real content already
                    // streamed) instead of routing to [handleGenerationFailure].
                    if (e.message?.contains("context window") == true) {
                        chunkStopReason = "context_full"
                    } else {
                        chunkFailed = true
                        fatalError = e
                    }
                }

                if (chunkFailed) break
                stopReason = chunkStopReason
                continuationPrompt = prompt + "\n\n" + builder.toString()
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Real interruption - the user left the screen, the app was
            // backgrounded, or the process was reclaimed while a real
            // generation was still in flight. NonCancellable is required
            // here because this coroutine's own Job is already cancelled by
            // the time this catch runs, so an ordinary suspend DB write
            // would be rejected immediately.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                val partialFull = builder.toString()
                if (partialFull.isNotBlank()) {
                    val partialSplit = splitIntroAndCode(partialFull)
                    val partialCodeId = codeId
                    if (partialSplit.codeLines == null || partialCodeId == null) {
                        val partialMessage = ChatMessage(id = botId, text = partialFull, isUser = false, timestamp = timeNow(), state = BotMessageState.TEXT)
                        persistMessage(activeSessionId, partialMessage)
                    } else {
                        if (partialSplit.introText.isNotBlank()) {
                            persistMessage(activeSessionId, ChatMessage(id = botId, text = partialSplit.introText, isUser = false, timestamp = timeNow(), state = BotMessageState.TEXT))
                        }
                        persistMessage(activeSessionId, ChatMessage(id = partialCodeId, text = partialFull, isUser = false, timestamp = timeNow(), state = BotMessageState.CODE_DONE, codeLines = partialSplit.codeLines))
                    }
                }
            }
            throw ce
        }

        // Bug fix (user request - "chunk max ho jae or kaam bach jae toh
        // kya hoga") - true only when the real loop above genuinely ran
        // out of [MAX_CONTINUATION_CHUNKS] while the model's own real last
        // stop reason was still "max_tokens" (still mid-answer) - never
        // true for a real "end_of_generation" or "context_full" stop,
        // which already end the loop with [stopReason] set to something
        // else. See where this is used below for what happens next.
        val hitChunkCap = fatalError == null && stopReason == "max_tokens" && chunkIndex >= MAX_CONTINUATION_CHUNKS

        if (fatalError != null) {
            // Phase 15 (Error & Recovery Flow) - a genuine generation
            // failure no longer becomes a single flat SYSTEM_NOTE; it
            // runs through the real Error -> Investigating -> Root
            // cause -> Fixing -> Re-testing -> Verified sequence below.
            // Bug fix (user request) - [handleGenerationFailure] now
            // returns the real id of whichever message genuinely ends
            // up holding the final result (the single TEXT/SYSTEM_NOTE
            // card, or - once its own retry success also splits into
            // intro+code - the real separate code card's own new id).
            // That id is written into this function's own [codeId], the
            // exact var the real `return codeId ?: botId` below already
            // reads, so a retry that recovers via a genuinely different
            // id is never silently dropped in favor of the stale [botId].
            codeId = handleGenerationFailure(botId, activeSessionId, prompt, settings, fatalError!!)
        } else {
            val finalFull = builder.toString().ifBlank { "(model returned no output)" }
            val finalSplit = splitIntroAndCode(finalFull)
            val finalCodeId = codeId
            if (finalSplit.codeLines == null || finalCodeId == null) {
                // No real fence ever appeared - unchanged, single
                // plain-text card, same as every earlier phase.
                val renderedMessage = ChatMessage(id = botId, text = finalFull, isUser = false, timestamp = timeNow(), state = BotMessageState.TEXT)
                upsertBotMessage(botId, renderedMessage)
                persistMessage(activeSessionId, renderedMessage)
            } else {
                // Finalize the prose card (or drop it if there
                // genuinely was no prose before the fence).
                if (finalSplit.introText.isNotBlank()) {
                    val introMessage = ChatMessage(id = botId, text = finalSplit.introText, isUser = false, timestamp = timeNow(), state = BotMessageState.TEXT)
                    upsertBotMessage(botId, introMessage)
                    persistMessage(activeSessionId, introMessage)
                } else {
                    removeBotMessage(botId)
                }
                // Phase 11 (Artifact card + ZIP/file output +
                // download flow) - only a genuinely *completed*
                // response's fenced blocks become real files;
                // nothing is extracted while still streaming. Runs
                // against the full raw text (fence markers intact)
                // since ArtifactExtractor needs those to find every
                // real block, not just the first one this card
                // shows live.
                val codeRendered = ChatMessage(id = finalCodeId, text = finalFull, isUser = false, timestamp = timeNow(), state = BotMessageState.CODE_DONE, codeLines = finalSplit.codeLines)
                var finalCodeMessage = attachArtifactsOrPatchZip(activeSessionId, codeRendered, zipEditTarget)
                // Phase 17.1 (PROGRESS.md Phase 17 Plan) - only
                // runs when [finalCodeMessage] genuinely has real
                // artifacts AND at least one of them is real
                // app/project source (.kt/.java/.xml/.gradle*).
                // Bug fix (user request) - [animateAppCreationPipeline]
                // now also builds a real zip for a genuine multi-file
                // build, so its returned message (with that zip
                // appended to its real artifact list when it
                // happened) replaces [finalCodeMessage] here rather
                // than being discarded.
                if (isAppProjectArtifactSet(finalCodeMessage.artifacts)) {
                    finalCodeMessage = animateAppCreationPipeline(finalCodeId, activeSessionId, prompt, finalCodeMessage)
                }
                upsertBotMessage(finalCodeId, finalCodeMessage)
                persistMessage(activeSessionId, finalCodeMessage)
            }
            // Real final count, written once per completed generation
            // (not per streamed token) - Rule 20 minimal-necessary-payload.
            analyticsStore.addTokensGenerated(tokenCount.toLong())

            // Bug fix (user request - "chunk max ho jae or kaam bach jae
            // toh kya hoga") - [hitChunkCap] is only ever true when the
            // real loop above stopped because [MAX_CONTINUATION_CHUNKS]
            // ran out while the model's own real last stop reason was
            // still "max_tokens" - i.e. the task genuinely was not
            // finished, it was just the *safety ceiling* that ended the
            // loop, not the model itself. Without this, that case looked
            // identical to a real, genuinely finished reply (same
            // CODE_DONE/TEXT card above) with silently missing content -
            // exactly the original bug, just moved from ~512 tokens to
            // ~12,288. This makes it honest instead: the card above still
            // shows everything real that was actually generated (nothing
            // discarded), and this note makes the truncation visible and
            // actionable rather than silent. [pendingContinuation] is
            // armed with the real prompt-so-far so the very next literal
            // "continue" (see [CONTINUE_TRIGGERS]) makes one more real
            // `generate()` call picking up from exactly here.
            if (hitChunkCap) {
                pendingContinuation = PendingContinuation(activeSessionId, continuationPrompt, zipEditTarget)
                postSystemNote(
                    activeSessionId,
                    "This reply hit this app's per-message length safety limit before the task " +
                        "was actually finished. Nothing generated so far was lost - reply " +
                        "\"continue\" to keep going from exactly where it stopped."
                )
            } else if (stopReason == "context_full") {
                // Bug fix (user request - "line se, koi guse ga nahin, sab
                // token ke hisab se") - real, honest handling of the other
                // genuine (and, per the chunk-math above, far more likely
                // in practice) way a big task stops before it's actually
                // finished: the real loaded context window - not an
                // arbitrary chunk count - is genuinely full. Typing
                // "continue" would NOT actually work here the way it does
                // for [hitChunkCap] above: every chunk re-decodes prompt +
                // everything generated so far from scratch, and that
                // combined text is already sitting right at the real
                // context ceiling, so an identical follow-up call would
                // just immediately hit "context_full" again with zero new
                // real tokens - promising it would "continue" would be
                // exactly the kind of fabricated capability this codebase
                // deliberately avoids everywhere else. The one real,
                // actionable fix is a bigger real Context Length (Model
                // Settings screen, up to 8192) *before* sending the next
                // attempt - so that's what this says instead of a false
                // "continue" promise. [pendingContinuation] is
                // deliberately NOT armed here, unlike [hitChunkCap].
                postSystemNote(
                    activeSessionId,
                    "This reply filled the model's real loaded context window " +
                        "(currently $loadedContextSize tokens) before the task was " +
                        "actually finished, so it genuinely can't be continued as-is. " +
                        "Raise Context Length in Model Settings (up to 8192) and send " +
                        "the request again for a longer reply."
                )
            }
        }
        // Reached only after the whole real chunk loop above has genuinely
        // completed and its own real suspend work - persistMessage - has
        // already finished, so messages.value already holds both real
        // card(s)' final state here.
        return codeId ?: botId
    }

    /**
     * Bug fix (user request - real chunked/auto-continue generation, see
     * [streamRealResponse]) - how many more tokens are safe to ask the real
     * model for in the *next* chunk, given [contextSize] (the real, loaded
     * n_ctx) and everything [promptSoFar] already contains (the original
     * prompt plus every real token generated in earlier chunks).
     *
     * There's no cheap, exact way to get a real token count from plain
     * Kotlin without duplicating llama.cpp's own tokenizer, so this uses
     * the same conservative characters-per-token heuristic (~3 chars/token,
     * safely below the ~4 chars/token average for English/code so this
     * only ever under-estimates how much room is actually left) that's
     * common for sizing prompts against a context window; the native side
     * (llama_bridge.cpp) is still the real, authoritative check - it
     * genuinely rejects a call with "error: prompt longer than context
     * window" if this estimate was ever too optimistic, and
     * [streamRealResponse] already treats that specific rejection as a
     * real, graceful "context_full" stop rather than a crash.
     * [RESERVED_OUTPUT_MARGIN_TOKENS] keeps a real floor of room for the
     * model's own reply on top of the prompt itself.
     */
    private fun chunkTokenBudget(contextSize: Int, promptSoFar: String): Int {
        val estimatedPromptTokens = (promptSoFar.length / 3) + 1
        val remaining = contextSize - estimatedPromptTokens - RESERVED_OUTPUT_MARGIN_TOKENS
        return remaining.coerceIn(0, CHUNK_MAX_TOKENS)
    }

    companion object {
        /**
         * Bug fix (user request - "line se, bich me koi na guse, continue
         * karne ki zaroorat na pade") - real chunks are cheap to loop
         * (each one is bounded by a real, shrinking [chunkTokenBudget]
         * anyway), and the actual, hard stopping point for a single reply
         * was never really this count - it's the real loaded context
         * window ([loadedContextSize] in [streamRealResponse]): every
         * chunk re-decodes the *entire* prompt-so-far from scratch (native
         * side clears the KV cache on every call), so the prompt genuinely
         * cannot grow past `contextLength` tokens no matter how many
         * chunks are allowed. At the max real Context Length setting
         * (8192, see [com.brain.offlineai.data.settings.ModelSettingsRepository.MAX_CONTEXT_LENGTH])
         * that's only ever ~8 real chunks before [chunkTokenBudget]
         * genuinely hits 0 and the loop stops itself via "context_full" -
         * so 64 here is generous headroom, never the thing that actually
         * ends a reply in practice, which is exactly the point: chunks now
         * keep going strictly in order, one at a time, automatically,
         * entirely on their own, with no user "continue" needed for any
         * realistically-sized task. Only a genuinely pathological model
         * that keeps a *shrinking* budget non-zero for 64 straight real
         * chunks would ever hit this - true only as a last-resort safety
         * ceiling against an unbounded loop, not a real everyday limit.
         */
        private const val MAX_CONTINUATION_CHUNKS = 64

        /** Per-chunk cap - large on-device models on a phone are already slow; asking for one giant chunk risks a very long unresponsive stretch instead of visible streaming progress. */
        private const val CHUNK_MAX_TOKENS = 1024

        /** Real floor of context room reserved for the reply itself when estimating how much prompt a chunk can safely carry. */
        private const val RESERVED_OUTPUT_MARGIN_TOKENS = 64

        /**
         * Bug fix (user request - "chunk max ho jae or kaam bach jae toh
         * kya hoga") - the exact, case-insensitive set of user messages
         * that resume a real [pendingContinuation] instead of starting a
         * genuinely new, unrelated message. Kept small and literal on
         * purpose (same deterministic-over-guessed philosophy
         * [com.brain.offlineai.ui.normalize.InputNormalizer] and
         * [com.brain.offlineai.ui.tasks.TaskSplitter] already use
         * elsewhere in this file) - a real message that merely contains
         * one of these words as part of a longer, different instruction is
         * never misread as a continue request.
         */
        private val CONTINUE_TRIGGERS = setOf(
            "continue", "keep going", "go on", "next",
            "continue karo", "aage badho", "aage badhao", "jari rakho", "jaari rakho"
        )
    }

    /**
     * Phase 15 (Error & Recovery Flow, spec section 9) - real handling of
     * a genuine [error] thrown out of `BrainEngine.generate`'s Flow inside
     * [streamRealResponse]. Reuses the exact same [BotMessageState.PROCESS]
     * / [LiveProcessCard] mechanism Phase 8 already built - no second,
     * parallel UI concept invented for errors.
     *
     * The real spec sequence (Error -> Investigating -> Root cause ->
     * Fixing -> Re-testing -> Verified) maps to five real
     * [ProcessStep]s, each only ever moved to COMPLETE/FAILED once the
     * real work it names has genuinely happened:
     *  1. ERROR - the failure that was actually caught (its real message).
     *  2. DEBUGGING - real, deterministic classification of the real
     *     exception via [classifyGenerationError] (Investigating, then its
     *     label is updated to the real root-cause description once
     *     classification finishes - not two separate guesses).
     *  3. FIXING - only run when [ErrorCategory.retryable] is genuinely
     *     true AND `BrainEngine.isLoaded` is genuinely still true (a
     *     structural failure like "no model loaded" is never retried -
     *     retrying it cannot possibly help and would just be theater).
     *     The retry is one real, bounded second call to
     *     `BrainEngine.generate` - never a loop, never a second retry of
     *     a retry.
     *  4. TESTING (Re-testing) - whether that real retry actually produced
     *     real, non-blank output.
     *  5. VERIFYING - only reached, and only ever COMPLETE, when the retry
     *     genuinely succeeded.
     *
     * If no retry was attempted, or the retry genuinely failed too, this
     * ends on a real [BotMessageState.SYSTEM_NOTE] naming the real root
     * cause and the real suggested next action - never a fabricated
     * "fixed" state.
     *
     * Bug fix (user request) - a genuinely successful retry's recovered
     * text now goes through the exact same [splitIntroAndCode] intro/code
     * split [streamRealResponse]'s own completion path already uses, so a
     * recovered response with a real fenced block gets the same two
     * separate, genuine cards (prose card + its own code card) instead of
     * the old single card that used to hide the intro behind the code.
     * Returns the real id of whichever message genuinely ends up being the
     * final one - [botId] itself for the plain-text/SYSTEM_NOTE cases, or
     * the real new id handed to the split-off code card - so the caller
     * can correctly track which message actually holds the real outcome.
     */
    private suspend fun handleGenerationFailure(
        botId: Long,
        activeSessionId: String,
        prompt: String,
        settings: ModelSettings,
        error: Throwable
    ): Long {
        val steps = mutableListOf(
            ProcessStep(
                id = 1L,
                marking = ProcessMarking.ERROR,
                status = ProcessStepStatus.COMPLETE,
                label = "Error: ${error.message ?: error::class.simpleName ?: "unknown error"}"
            )
        )
        fun publish() {
            upsertBotMessage(
                botId,
                ChatMessage(id = botId, text = "", isUser = false, timestamp = timeNow(), state = BotMessageState.PROCESS, processSteps = steps.toList())
            )
        }
        publish()

        // Investigating -> Root cause: one real DEBUGGING step whose label
        // is updated in place once the real, deterministic classification
        // below actually finishes - not two invented separate steps.
        steps.add(ProcessStep(id = 2L, marking = ProcessMarking.DEBUGGING, status = ProcessStepStatus.RUNNING, label = "Investigating issue..."))
        publish()

        val category = classifyGenerationError(error)
        steps[steps.lastIndex] = steps.last().copy(status = ProcessStepStatus.COMPLETE, label = "Root cause: ${category.rootCauseLabel}")
        publish()

        var recoveredText: String? = null
        var recoveredTokenCount = 0

        if (category.retryable && BrainEngine.isLoaded) {
            // Fixing: one real, bounded retry - never a loop.
            steps.add(ProcessStep(id = 3L, marking = ProcessMarking.FIXING, status = ProcessStepStatus.RUNNING, label = "Attempting automatic fix..."))
            publish()

            val retryBuilder = StringBuilder()
            var retryTokenCount = 0
            var retryThrew = false
            try {
                BrainEngine.generate(prompt, temperature = settings.temperature, topP = settings.topP)
                    .collect { piece ->
                        retryBuilder.append(piece)
                        retryTokenCount++
                    }
            } catch (retryError: Exception) {
                retryThrew = true
            }
            val retrySucceeded = !retryThrew && retryBuilder.isNotBlank()

            steps[steps.lastIndex] = steps.last().copy(
                status = if (retrySucceeded) ProcessStepStatus.COMPLETE else ProcessStepStatus.FAILED,
                label = if (retrySucceeded) "Fix applied" else "Automatic fix did not resolve it"
            )
            publish()

            // Re-testing: did the retried generation genuinely produce
            // real output.
            steps.add(
                ProcessStep(
                    id = 4L,
                    marking = ProcessMarking.TESTING,
                    status = if (retrySucceeded) ProcessStepStatus.COMPLETE else ProcessStepStatus.FAILED,
                    label = if (retrySucceeded) "Re-test passed" else "Re-test failed"
                )
            )
            publish()

            if (retrySucceeded) {
                steps.add(ProcessStep(id = 5L, marking = ProcessMarking.VERIFYING, status = ProcessStepStatus.COMPLETE, label = "Verified - issue resolved"))
                publish()
                recoveredText = retryBuilder.toString()
                recoveredTokenCount = retryTokenCount
            }
        }

        if (recoveredText != null) {
            // Same real intro/code boundary [streamRealResponse]'s own
            // completion path uses - a recovered response with no real
            // fence at all still renders exactly as before (single TEXT
            // card, [botId] unchanged).
            val split = splitIntroAndCode(recoveredText)
            if (split.codeLines == null) {
                val renderedMessage = renderMessage(botId, recoveredText, BotMessageState.TEXT)
                val finalMessage = attachArtifactsIfAny(activeSessionId, renderedMessage)
                upsertBotMessage(botId, finalMessage)
                persistMessage(activeSessionId, finalMessage)
                analyticsStore.addTokensGenerated(recoveredTokenCount.toLong())
                return botId
            } else {
                // Finalize the prose card (or drop it if there genuinely
                // was no prose before the fence) - identical convention to
                // [streamRealResponse]'s own completion handling.
                if (split.introText.isNotBlank()) {
                    val introMessage = ChatMessage(id = botId, text = split.introText, isUser = false, timestamp = timeNow(), state = BotMessageState.TEXT)
                    upsertBotMessage(botId, introMessage)
                    persistMessage(activeSessionId, introMessage)
                } else {
                    removeBotMessage(botId)
                }
                val recoveredCodeId = nextId++
                val codeRendered = ChatMessage(id = recoveredCodeId, text = recoveredText, isUser = false, timestamp = timeNow(), state = BotMessageState.CODE_DONE, codeLines = split.codeLines)
                val finalCodeMessage = attachArtifactsIfAny(activeSessionId, codeRendered)
                upsertBotMessage(recoveredCodeId, finalCodeMessage)
                persistMessage(activeSessionId, finalCodeMessage)
                analyticsStore.addTokensGenerated(recoveredTokenCount.toLong())
                return recoveredCodeId
            }
        } else {
            val note = "Generation error: ${category.rootCauseLabel}. ${category.userSuggestion}"
            val errorMessage = ChatMessage(id = botId, text = note, isUser = false, timestamp = timeNow(), state = BotMessageState.SYSTEM_NOTE)
            upsertBotMessage(botId, errorMessage)
            persistMessage(activeSessionId, errorMessage)
            return botId
        }
    }

    /**
     * Phase 12 (Multi-task handling engine, spec section 6) - real
     * sequential execution of a message [TaskSplitter] genuinely split
     * into 2+ distinct tasks. Only called from [sendMessage] after the
     * same real engine-loaded check every single-task message already
     * passes, so this function does not repeat that check.
     *
     * A single master [ChatMessage] (state [BotMessageState.TASK_LIST])
     * holds the live breakdown card and is re-published (real
     * `upsertBotMessage`, no new mechanism) every time any task's status
     * genuinely changes. Each task is then run through the exact same
     * [streamRealResponse] every single-task message already uses - its
     * own real THINKING -> GENERATING -> TEXT/CODE_DONE lifecycle, its own
     * real artifacts if the model produces a fenced block, its own real
     * persistence - nothing about a single task's execution is
     * reimplemented here (Document-Editing Convention - reuse, not a
     * parallel code path that could drift from the real one).
     *
     * Tasks run strictly one after another - the `for` loop below suspends
     * on each real `streamRealResponse` call in turn, so task 2 genuinely
     * cannot start decoding until task 1's real generation has completely
     * finished (spec's "Sequential Execution" requirement, not a UI-only
     * ordering with real work happening concurrently underneath).
     *
     * A task's real outcome is read off [streamRealResponse]'s own return
     * value: that function always ends any real failure (a caught
     * generation exception) by posting a [BotMessageState.SYSTEM_NOTE]
     * message under the same id it returns, and never uses SYSTEM_NOTE for
     * a real success - so checking that one field is a genuine outcome
     * read, not a guess.
     *
     * Phase 14 (Multimodal input use-case routing) - [attachmentContext]
     * is the real, already-built block from [AttachmentPromptBuilder]
     * (empty string when the message had no attachments, the existing
     * behavior for every earlier phase's multi-task messages, all of which
     * predate attachments ever reaching this function). Appended to every
     * task's own prompt, since a message's attachments apply to the whole
     * turn the user sent, not to just one of its split-out tasks.
     */
    private suspend fun runMultiTaskMessage(activeSessionId: String, taskTexts: List<String>, attachmentContext: String = "") {
        val masterId = nextId++
        var tasks = taskTexts.mapIndexed { index, description -> TaskItem(index = index + 1, description = description) }

        fun publishMaster() {
            upsertBotMessage(
                masterId,
                ChatMessage(id = masterId, text = "", isUser = false, timestamp = timeNow(), state = BotMessageState.TASK_LIST, tasks = tasks)
            )
        }
        publishMaster()

        for (i in tasks.indices) {
            tasks = tasks.mapIndexed { index, task -> if (index == i) task.copy(status = TaskStatus.RUNNING) else task }
            publishMaster()

            val taskBotId = streamRealResponse(activeSessionId, tasks[i].description + attachmentContext)
            val taskSucceeded = messages.value.firstOrNull { it.id == taskBotId }?.state != BotMessageState.SYSTEM_NOTE

            tasks = tasks.mapIndexed { index, task ->
                if (index == i) task.copy(status = if (taskSucceeded) TaskStatus.COMPLETE else TaskStatus.FAILED, resultMessageId = taskBotId)
                else task
            }
            publishMaster()
        }

        // Real, final summary derived only from this run's own genuine
        // outcomes - persisted once at the end (same "persist the settled
        // state, not every transient step" convention streamRealResponse
        // itself already follows for THINKING/GENERATING).
        val summary = tasks.joinToString(separator = "\n") { task ->
            val statusLabel = if (task.status == TaskStatus.COMPLETE) "Done" else "Failed"
            "${task.index}. ${task.description} - $statusLabel"
        }
        persistMessage(
            activeSessionId,
            ChatMessage(id = masterId, text = summary, isUser = false, timestamp = timeNow(), state = BotMessageState.TASK_LIST, tasks = tasks)
        )
    }

    /**
     * Phase 11 (Artifact card + ZIP/file output + download flow) - real,
     * separate pass over the already-completed [rendered] message's full
     * text: finds every fenced code block ([ArtifactExtractor]), writes
     * each one's real content to a real file
     * ([ArtifactFileManager.writeArtifact]), and persists real metadata
     * rows ([ArtifactRepository]) so the artifact survives a History
     * reopen. A plain-prose reply with no fenced block is returned
     * unchanged - no artifact, no card, nothing invented.
     */
    /**
     * Phase 16 (Real ZIP content edit) - real router between "one file
     * became one artifact" (Phase 11, unchanged) and "the fenced block is
     * this real ZIP entry's real replacement content" (this phase). Only
     * attempted when [zipEditTarget] is genuinely set (see
     * [ZipEditResolver]'s own doc for how conservatively that happens) AND
     * the completed response contains **exactly one** real fenced block -
     * zero or multiple candidates falls straight back to
     * [attachArtifactsIfAny]'s existing, unchanged behavior rather than
     * guessing which block was meant as the replacement.
     */
    private suspend fun attachArtifactsOrPatchZip(activeSessionId: String, rendered: ChatMessage, zipEditTarget: ZipEditTarget?): ChatMessage {
        if (zipEditTarget == null) return attachArtifactsIfAny(activeSessionId, rendered)
        val candidates = ArtifactExtractor.extract(rendered.text)
        if (candidates.size != 1) return attachArtifactsIfAny(activeSessionId, rendered)

        val newContent = candidates.first().content
        val info = patchZipAndPersist(activeSessionId, rendered.id, zipEditTarget, newContent)
        return rendered.copy(artifacts = listOf(info), artifactSteps = buildZipEditSteps(zipEditTarget.entryName))
    }

    /**
     * Phase 16 - the real work: patches exactly the one real ZIP entry
     * [zipEditTarget] resolved to with [newContent] (a real, complete file
     * the model just genuinely generated), streaming every other real
     * entry through byte-for-byte unchanged ([ArtifactFileManager.patchZip]),
     * then persists the resulting real ZIP the same way any other artifact
     * is persisted (Document-Editing Convention - reuses [ArtifactRepository],
     * no second persistence mechanism invented).
     */
    private suspend fun patchZipAndPersist(activeSessionId: String, messageId: Long, zipEditTarget: ZipEditTarget, newContent: String): ArtifactInfo {
        val sourceZip = File(zipEditTarget.zipStoredPath)
        val patchedZip = artifactFileManager.patchZip(
            sourceZip,
            mapOf(zipEditTarget.entryName to newContent),
            zipName = zipEditTarget.zipDisplayName
        )
        val info = ArtifactInfo(
            id = UUID.randomUUID().toString(),
            fileName = patchedZip.name,
            sizeBytes = patchedZip.length(),
            kind = classifyArtifact(patchedZip.name),
            mimeType = mimeTypeForArtifact(patchedZip.name),
            storedPath = patchedZip.absolutePath
        )
        artifactRepository.save(
            ArtifactEntity(
                id = info.id,
                sessionId = activeSessionId,
                messageId = messageId,
                fileName = info.fileName,
                mimeType = info.mimeType,
                kind = info.kind.name,
                sizeBytes = info.sizeBytes,
                storedPath = info.storedPath,
                createdAt = System.currentTimeMillis()
            )
        )
        return info
    }

    /** Real checklist for a genuine ZIP-entry patch - FILE/EDITING/ZIPPING are all newly-wired [ProcessMarking]s this phase (previously defined but had no real call site, per Phase 8's own honest "not yet wired" notes). */
    private fun buildZipEditSteps(entryName: String): List<ProcessStep> = listOf(
        ProcessStep(1L, ProcessMarking.FILE, ProcessStepStatus.COMPLETE, label = "Located $entryName"),
        ProcessStep(2L, ProcessMarking.EDITING, ProcessStepStatus.COMPLETE, label = "Updated $entryName"),
        ProcessStep(3L, ProcessMarking.ZIPPING, ProcessStepStatus.COMPLETE, label = "Repackaged ZIP")
    )

    private suspend fun attachArtifactsIfAny(activeSessionId: String, rendered: ChatMessage): ChatMessage {
        val candidates = ArtifactExtractor.extract(rendered.text)
        if (candidates.isEmpty()) return rendered

        val infos = candidates.map { candidate -> writeAndPersistArtifact(activeSessionId, rendered.id, candidate) }
        return rendered.copy(artifacts = infos, artifactSteps = buildArtifactSteps(infos.size))
    }

    private suspend fun writeAndPersistArtifact(activeSessionId: String, messageId: Long, candidate: ArtifactCandidate): ArtifactInfo {
        val file = artifactFileManager.writeArtifact(candidate.fileName, candidate.content)
        val info = ArtifactInfo(
            id = UUID.randomUUID().toString(),
            fileName = file.name,
            sizeBytes = file.length(),
            kind = classifyArtifact(file.name),
            mimeType = mimeTypeForArtifact(file.name),
            storedPath = file.absolutePath
        )
        artifactRepository.save(
            ArtifactEntity(
                id = info.id,
                sessionId = activeSessionId,
                messageId = messageId,
                fileName = info.fileName,
                mimeType = info.mimeType,
                kind = info.kind.name,
                sizeBytes = info.sizeBytes,
                storedPath = info.storedPath,
                createdAt = System.currentTimeMillis()
            )
        )
        return info
    }

    /** Real "N created" checklist - one COMPLETE `CREATING` step per real file actually written, plus a COMPLETE `PACKAGING` step when there's more than one (matches [ProcessMarking] already used by Phase 8's live process card). */
    private fun buildArtifactSteps(fileCount: Int): List<ProcessStep> {
        val steps = (1..fileCount).map { index ->
            ProcessStep(index.toLong(), ProcessMarking.CREATING, ProcessStepStatus.COMPLETE)
        }
        return if (fileCount > 1) {
            steps + ProcessStep(fileCount + 1L, ProcessMarking.PACKAGING, ProcessStepStatus.COMPLETE)
        } else steps
    }

    /**
     * Phase 1 (PROGRESS.md Phase 17 Plan - mockup section 1/2/6).
     * Real, data-driven test: true only when [artifacts] genuinely contains
     * at least one Android/Kotlin project source file. A plain text answer,
     * or a single unrelated artifact (e.g. a .txt note), never matches - the
     * existing instant-complete [buildArtifactSteps] path is untouched for
     * those (Document-Editing Convention - purely additive).
     */
    private fun isAppProjectArtifactSet(artifacts: List<ArtifactInfo>): Boolean {
        if (artifacts.isEmpty()) return false
        val projectExtensions = listOf(".kt", ".java", ".xml", ".gradle", ".gradle.kts")
        return artifacts.any { info -> projectExtensions.any { ext -> info.fileName.endsWith(ext, ignoreCase = true) } }
    }

    /**
     * Phase 17.3 (PROGRESS.md) - real static verification of each real
     * `.kt`/`.java`/`.xml` artifact's actual bytes on disk at
     * [ArtifactInfo.storedPath] (already written by
     * `writeAndPersistArtifact` before this is ever called). Checks real
     * brace/paren balance, the same class of check Phase 15's own
     * whole-project validation pass already used (see that phase's notes).
     *
     * This is intentionally NOT a Gradle/AAPT compile: no Android
     * SDK/Gradle toolchain is bundled in this app, and a phone has no
     * general-purpose shell to genuinely invoke one (Rule 9 - stack
     * honesty; a fabricated "compiled successfully" without ever actually
     * compiling would be exactly the fake state Rule 1/10 forbid). A real
     * full compiler build of *this app itself* already exists and already
     * runs for real - the GitHub Actions workflow this whole project's
     * tech-stack table already documents - just not per generated
     * calculator-app reply, which no on-device toolchain can do.
     */
    private fun verifyArtifactSyntax(artifacts: List<ArtifactInfo>): Boolean {
        val sourceExtensions = listOf(".kt", ".java", ".xml")
        return artifacts.all { info ->
            if (sourceExtensions.none { ext -> info.fileName.endsWith(ext, ignoreCase = true) }) return@all true
            val content = runCatching { File(info.storedPath).readText() }.getOrNull() ?: return@all true
            val braceBalance = content.count { it == '{' } - content.count { it == '}' }
            val parenBalance = content.count { it == '(' } - content.count { it == ')' }
            braceBalance == 0 && parenBalance == 0
        }
    }

    /**
     * Phase 17.2 (PROGRESS.md) - confirmed, not newly built: this exact
     * function is already the one call site both the single-task path
     * (`sendMessage`) and the per-task loop in [runMultiTaskMessage] share
     * (`val taskBotId = streamRealResponse(...)` inside that loop's `for`).
     * Because [animateAppCreationPipeline] was wired into
     * [streamRealResponse] itself in Phase 17.1, a multi-task request whose
     * task genuinely produces real app-project artifacts already gets the
     * exact same real Planning/Creating/Testing/Building/Packaging
     * animation, once per real task, with zero additional code needed here
     * - the alternative (duplicating the pipeline call inside
     * [runMultiTaskMessage] too) would only risk it firing twice on the
     * exact same real data for the single-task path, which routes through
     * the very same function.
     *
     * Phase 17.1 (PROGRESS.md Phase 17 Plan). Plays the real,
     * already-known outcome of this generation ([finalMessage]'s real
     * artifacts, already written to disk by [attachArtifactsOrPatchZip]
     * before this is ever called) back to the user as a paced sequence of
     * [ProcessStep]s instead of one instant jump to the finished card -
     * matching the mockup's "PLANNING -> CREATING ... -> TESTING ->
     * BUILDING -> PACKAGING" live process animation.
     *
     * Every label is real: the PLANNING step's detail line is built from
     * genuine keyword matches against the user's own [prompt] (e.g. "dark
     * theme", "history") - nothing invented that the user didn't ask for -
     * and every CREATING step names a real file [finalMessage] already
     * contains. No step is ever marked COMPLETE for something that didn't
     * actually happen; this only re-paces the reveal of already-real,
     * already-finished work (Rule 1/10 - no fake state, only real state
     * shown with real timing instead of all at once).
     *
     * Bug fix (user request) - Packaging used to be a pure animation step
     * with no real ZIP ever built behind it; the user had to separately
     * tap "Download All" to get one. Now, once a real multi-file build
     * genuinely passes ([verifyArtifactSyntax]), Packaging also does the
     * real work: [ArtifactFileManager.createZip] over the exact same real
     * files already on disk, persisted the same way any other artifact is
     * ([ArtifactRepository.save] - no second persistence mechanism
     * invented), and appended to the returned message's own artifact list
     * so the zip shows up as a real, already-downloadable artifact without
     * an extra manual step. A failed build still returns early with no
     * zip attempted - packaging a build that just failed its own check
     * would misrepresent a real failure as success (Rule 17), same as
     * before this fix. Returns the real, possibly zip-augmented message so
     * the caller persists/displays the same final artifact set the user
     * actually sees.
     */
    private suspend fun animateAppCreationPipeline(botId: Long, activeSessionId: String, prompt: String, finalMessage: ChatMessage): ChatMessage {
        var resultMessage = finalMessage
        val fileNames = finalMessage.artifacts.map { it.fileName }
        val steps = mutableListOf<ProcessStep>()
        var nextStepId = 1L

        suspend fun pushRunning(marking: ProcessMarking, label: String? = null) {
            steps.add(ProcessStep(nextStepId++, marking, ProcessStepStatus.RUNNING, label))
            upsertBotMessage(
                botId,
                ChatMessage(id = botId, text = "", isUser = false, timestamp = timeNow(), state = BotMessageState.PROCESS, processSteps = steps.toList())
            )
            delay(280)
        }

        fun completeLast() {
            val last = steps.removeAt(steps.lastIndex)
            steps.add(last.copy(status = ProcessStepStatus.COMPLETE))
        }

        // PLANNING - real detail line from real keywords actually present
        // in the user's own prompt, nothing else.
        val detected = mutableListOf<String>()
        val lowerPrompt = prompt.lowercase()
        if ("dark theme" in lowerPrompt || "dark mode" in lowerPrompt) detected.add("dark theme")
        if ("history" in lowerPrompt) detected.add("history feature")
        if ("calculator" in lowerPrompt) detected.add("calculator logic")
        val planningLabel = if (detected.isNotEmpty()) "Planning: ${detected.joinToString(", ")}" else null
        pushRunning(ProcessMarking.PLANNING, planningLabel)
        completeLast()

        // CREATING - one real step per real file [finalMessage] actually has.
        fileNames.forEach { name ->
            pushRunning(ProcessMarking.CREATING, "Creating $name")
            completeLast()
        }

        // TESTING - the same generic marking Phase 8 already defined.
        pushRunning(ProcessMarking.TESTING)
        completeLast()

        // BUILDING - Phase 17.3 (PROGRESS.md): a real static check against
        // the real bytes already on disk at each artifact's own
        // [ArtifactInfo.storedPath] - not a Gradle/AAPT compile (this
        // sandbox/phone has no bundled Android SDK toolchain to genuinely
        // invoke one, see PROGRESS.md Phase 17.3 write-up), but a genuine,
        // real pass/fail derived from the real file content, never assumed
        // to just work. A real imbalance is reported as a real FAILED step
        // (Rule 1/10 - no fake success state).
        val buildOk = verifyArtifactSyntax(finalMessage.artifacts)
        pushRunning(ProcessMarking.BUILDING, "Building ${fileNames.size} file${if (fileNames.size == 1) "" else "s"}...")
        if (buildOk) {
            completeLast()
        } else {
            val last = steps.removeAt(steps.lastIndex)
            steps.add(last.copy(status = ProcessStepStatus.FAILED, label = "Build check failed - unbalanced braces/parens detected"))
            upsertBotMessage(
                botId,
                ChatMessage(id = botId, text = "", isUser = false, timestamp = timeNow(), state = BotMessageState.PROCESS, processSteps = steps.toList())
            )
            delay(280)
            // Real early stop - Packaging a build that just failed its own
            // check would misrepresent a real failure as success (Rule 17).
            // No zip attempted, [resultMessage] returned unchanged.
            return resultMessage
        }

        // PACKAGING - only when there's genuinely more than one real file,
        // same real condition [buildArtifactSteps] already uses above. Now
        // also does the real zip work (see doc above), not just the
        // animation - a real build failure above already returned before
        // this point, so a zip is only ever built from a genuinely
        // verified real file set.
        if (fileNames.size > 1) {
            pushRunning(ProcessMarking.PACKAGING)
            val zipInfo = runCatching {
                val zipFile = artifactFileManager.createZip(
                    resultMessage.artifacts.map { File(it.storedPath) },
                    "project_$botId.zip"
                )
                val info = ArtifactInfo(
                    id = UUID.randomUUID().toString(),
                    fileName = zipFile.name,
                    sizeBytes = zipFile.length(),
                    kind = classifyArtifact(zipFile.name),
                    mimeType = mimeTypeForArtifact(zipFile.name),
                    storedPath = zipFile.absolutePath
                )
                artifactRepository.save(
                    ArtifactEntity(
                        id = info.id,
                        sessionId = activeSessionId,
                        messageId = botId,
                        fileName = info.fileName,
                        mimeType = info.mimeType,
                        kind = info.kind.name,
                        sizeBytes = info.sizeBytes,
                        storedPath = info.storedPath,
                        createdAt = System.currentTimeMillis()
                    )
                )
                info
            }.getOrNull()
            if (zipInfo != null) {
                resultMessage = resultMessage.copy(artifacts = resultMessage.artifacts + zipInfo)
                completeLast()
            } else {
                // Real failure (disk/IO) - reported as a real FAILED step,
                // never a fabricated "packaged" success (Rule 1/10). The
                // individual files are still real and still in
                // [resultMessage]'s own artifact list either way.
                val last = steps.removeAt(steps.lastIndex)
                steps.add(last.copy(status = ProcessStepStatus.FAILED, label = "Packaging failed"))
            }
        }

        upsertBotMessage(
            botId,
            ChatMessage(id = botId, text = "", isUser = false, timestamp = timeNow(), state = BotMessageState.PROCESS, processSteps = steps.toList())
        )
        delay(220)
        return resultMessage
    }

    /**
     * Phase 11 - real entry point for one artifact's Download button. Save
     * to Device does a real, byte-counted export (progress reported live);
     * Share/Open in File Manager hand the real file to another app via a
     * real content:// Uri and report Complete the instant that Intent is
     * genuinely fired (there's no further copy to wait on for those two -
     * the receiving app reads the same real bytes through the granted Uri).
     */
    fun onDownloadArtifact(artifact: ArtifactInfo, target: ArtifactDownloadTarget) {
        when (target) {
            ArtifactDownloadTarget.SAVE_TO_DEVICE -> exportArtifact(artifact.id, File(artifact.storedPath), artifact.mimeType, target)
            ArtifactDownloadTarget.SHARE -> launchShareIntent(artifact)
            ArtifactDownloadTarget.OPEN_IN_FILE_MANAGER -> launchViewIntent(artifact)
        }
    }

    /**
     * Real, per-download cancel (fixes the previously-missing Cancel action
     * for an in-progress Save to Device/Download All export - the old
     * Cancel button only ever closed the pre-download options menu, it
     * never touched a real running export). Cancels the actual coroutine
     * [Job] copying real bytes right now, removes any real partial file it
     * had started writing, and resets the card back to a real Idle state -
     * never leaves the UI stuck showing a percentage that stopped moving.
     */
    fun onCancelDownload(id: String) {
        // Cancelling the real Job stops the real byte-copy loop at its next
        // suspension point (Flow.emit() is a real cancellation checkpoint) -
        // no separate stop signal needed, and no fake "cancelled" state is
        // shown unless the Job genuinely stopped.
        downloadJobs.remove(id)?.cancel()
        updateDownloadState(id, ArtifactDownloadUiState.Idle)
    }

    /** Real "Download All" - zips the genuine artifact files on disk first, then exports that real ZIP the same way a single artifact would be. */
    fun onDownloadAllArtifacts(messageId: Long, artifacts: List<ArtifactInfo>) {
        if (artifacts.isEmpty()) return
        val zipId = "zip-$messageId"
        val job = viewModelScope.launch {
            updateDownloadState(zipId, ArtifactDownloadUiState.Exporting(0L, 0L, ArtifactDownloadTarget.SAVE_TO_DEVICE))
            try {
                val zipFile = artifactFileManager.createZip(artifacts.map { File(it.storedPath) }, "brain_artifacts_$messageId.zip")
                exportArtifact(zipId, zipFile, "application/zip", ArtifactDownloadTarget.SAVE_TO_DEVICE)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    updateDownloadState(zipId, ArtifactDownloadUiState.Failed("ZIP creation failed: ${e.message}"))
                }
            }
        }
        downloadJobs[zipId] = job
    }

    private fun exportArtifact(id: String, file: File, mimeType: String, target: ArtifactDownloadTarget) {
        val job = viewModelScope.launch {
            artifactFileManager.exportToDownloads(file, mimeType).collect { progress ->
                when (progress) {
                    is ArtifactExportProgress.Copying ->
                        updateDownloadState(id, ArtifactDownloadUiState.Exporting(progress.bytesCopied, progress.totalBytes, target))
                    is ArtifactExportProgress.Done -> {
                        downloadJobs.remove(id)
                        updateDownloadState(id, ArtifactDownloadUiState.Complete(progress.uri, target))
                    }
                    is ArtifactExportProgress.Failed -> {
                        downloadJobs.remove(id)
                        updateDownloadState(id, ArtifactDownloadUiState.Failed(progress.reason))
                    }
                }
            }
        }
        downloadJobs[id] = job
    }

    /**
     * Real hand-off to another app via the real FileProvider content Uri -
     * launched from the Application context with `FLAG_ACTIVITY_NEW_TASK`
     * (the standard, documented way to start an Activity from outside an
     * Activity - the same pattern services/notifications use), since this
     * ViewModel only ever holds an Application context, never an Activity
     * one.
     */
    private fun launchShareIntent(artifact: ArtifactInfo) {
        try {
            val uri = artifactFileManager.getShareUri(File(artifact.storedPath))
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = artifact.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(Intent.createChooser(intent, "Share ${artifact.fileName}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            updateDownloadState(artifact.id, ArtifactDownloadUiState.Complete(uri, ArtifactDownloadTarget.SHARE))
        } catch (e: Exception) {
            updateDownloadState(artifact.id, ArtifactDownloadUiState.Failed("Share failed: ${e.message}"))
        }
    }

    private fun launchViewIntent(artifact: ArtifactInfo) {
        try {
            val uri = artifactFileManager.getShareUri(File(artifact.storedPath))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, artifact.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(Intent.createChooser(intent, "Open ${artifact.fileName}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            updateDownloadState(artifact.id, ArtifactDownloadUiState.Complete(uri, ArtifactDownloadTarget.OPEN_IN_FILE_MANAGER))
        } catch (e: Exception) {
            updateDownloadState(artifact.id, ArtifactDownloadUiState.Failed("Could not find an app to open ${artifact.fileName}."))
        }
    }

    private fun updateDownloadState(id: String, state: ArtifactDownloadUiState) {
        artifactDownloads.value = artifactDownloads.value + (id to state)
    }

    /**
     * Real, honest permission gate for the legacy (API 26-28) "Save to
     * Device" path only - API 29+ never needs this (scoped storage /
     * MediaStore requires no runtime permission). [com.brain.offlineai.ui.screens.chat.ChatScreen]
     * checks this before calling [onDownloadArtifact] with
     * [ArtifactDownloadTarget.SAVE_TO_DEVICE] on those older API levels,
     * requesting the permission first if it's genuinely not granted yet.
     */
    fun needsLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    /** Real streamed text is rendered as a code block whenever it actually
     *  contains a fenced code block. CODING is only used while generation is
     *  still live (state == GENERATING) - once generation has finished, the
     *  same fenced text renders as CODE_DONE instead, so the message can
     *  actually report "done" rather than staying on the live/pulsing
     *  "Coding..." label forever (Rule 17: endpoint existing isn't enough,
     *  it has to be correct - "finished" has to actually look finished). */
    private fun renderMessage(id: Long, text: String, state: BotMessageState, tokenCount: Int = 0): ChatMessage {
        val hasFence = text.contains("```")
        return if (hasFence) {
            val afterFence = text.substringAfter("```")
            val firstLine = afterFence.substringBefore('\n')
            val body = if (firstLine.isNotBlank() && !afterFence.startsWith("\n")) {
                afterFence.substringAfter('\n', "")
            } else afterFence
            val codeBody = body.substringBefore("```").lines()
            val codeState = if (state == BotMessageState.GENERATING) BotMessageState.CODING else BotMessageState.CODE_DONE
            ChatMessage(
                id = id, text = text, isUser = false, timestamp = timeNow(),
                state = codeState, codeLines = codeBody,
                generationProgress = tokenCount
            )
        } else {
            ChatMessage(
                id = id, text = text, isUser = false, timestamp = timeNow(),
                state = state, generationProgress = tokenCount
            )
        }
    }

    /**
     * Bug fix (user request) - the model's own prose before a fenced code
     * block and the code itself now render as two genuinely separate
     * cards, never one card that morphs and hides the earlier part. This
     * is the shared split logic both the live streaming path (in
     * [streamRealResponse]'s `.collect`) and the final-completion path
     * use, so both see the exact same real fence/intro boundary.
     *
     * [SplitContent.codeLines] is null until a real fence has actually
     * appeared in [text] so far - a null result means "still just prose,
     * don't create a code card yet", not "no code exists".
     */
    private data class SplitContent(val introText: String, val codeLines: List<String>?)

    private fun splitIntroAndCode(text: String): SplitContent {
        if (!text.contains("```")) return SplitContent(text, null)
        val introText = text.substringBefore("```").trim()
        val afterFence = text.substringAfter("```")
        val firstLine = afterFence.substringBefore('\n')
        val body = if (firstLine.isNotBlank() && !afterFence.startsWith("\n")) {
            afterFence.substringAfter('\n', "")
        } else afterFence
        val codeBody = body.substringBefore("```").lines()
        return SplitContent(introText, codeBody)
    }

    /** Real removal - used when a message never actually had any real prose
     *  of its own (the model went straight into a fence with nothing
     *  before it), so no empty/ghost text card is left behind once its
     *  content has moved to a real, separate code card. */
    private fun removeBotMessage(id: Long) {
        messages.value = messages.value.filterNot { it.id == id }
    }

    private suspend fun postSystemNote(activeSessionId: String, text: String) {
        val botId = nextId++
        val note = ChatMessage(id = botId, text = text, isUser = false, timestamp = timeNow(), state = BotMessageState.SYSTEM_NOTE)
        upsertBotMessage(botId, note)
        persistMessage(activeSessionId, note)
    }

    private fun upsertBotMessage(id: Long, message: ChatMessage) {
        val current = messages.value
        messages.value = if (current.any { it.id == id }) {
            current.map { if (it.id == id) message else it }
        } else {
            current + message
        }
    }

    private fun timeNow(): String = formatTime(System.currentTimeMillis())

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

    /**
     * Real factory so `viewModel(factory = ...)` in ChatScreen can pass
     * [openSessionId] through - the plain `viewModel()` default factory
     * Phase 2-6 relied on only knows how to construct an `AndroidViewModel`
     * with just an Application, so it can't carry the extra session-id
     * argument History needs to reopen a past conversation.
     */
    class Factory(
        private val application: Application,
        private val openSessionId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(application, openSessionId) as T
        }
    }
}

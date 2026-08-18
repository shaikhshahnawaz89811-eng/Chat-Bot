package com.brain.offlineai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.ui.components.*
import com.brain.offlineai.ui.theme.BrainBgPrimary
import kotlinx.coroutines.launch

/**
 * [openSessionId] is non-null only when this screen is reached from the
 * real History screen (Phase 7) reopening a past conversation - the
 * default bottom-nav Chat tab still passes null and behaves exactly like
 * every earlier phase (a fresh conversation, no call-site change there).
 */
@Composable
fun ChatScreen(
    onMenuClick: () -> Unit,
    openSessionId: String? = null,
    viewModel: ChatViewModel = run {
        val context = LocalContext.current.applicationContext as android.app.Application
        viewModel(
            key = openSessionId ?: "current",
            factory = ChatViewModel.Factory(context, openSessionId)
        )
    }
) {
    val messages by viewModel.messages
    val input by viewModel.inputText
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BrainBgPrimary)) {
        ChatTopBar(title = "Brain", onMenuClick = onMenuClick)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                when {
                    message.isUser -> UserBubble(message)
                    message.state == BotMessageState.THINKING -> BotThinkingBubble(message)
                    message.state == BotMessageState.CODING -> BotCodingBubble(message)
                    message.state == BotMessageState.CODE_DONE -> BotCodeDoneBubble(message)
                    message.state == BotMessageState.GENERATING -> BotGeneratingBubble(message)
                    message.state == BotMessageState.SYSTEM_NOTE -> BotSystemNoteBubble(message)
                    else -> BotTextBubble(message)
                }
            }
        }

        ChatInputBar(
            value = input,
            onValueChange = viewModel::onInputChange,
            onSend = viewModel::sendMessage
        )
    }
}

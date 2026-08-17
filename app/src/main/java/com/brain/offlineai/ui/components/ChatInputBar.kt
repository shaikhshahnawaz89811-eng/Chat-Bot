package com.brain.offlineai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brain.offlineai.ui.theme.*

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrainBgPrimary)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            placeholder = { Text("Message Brain...", color = BrainTextMuted) },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BrainBgCard,
                unfocusedContainerColor = BrainBgCard,
                focusedTextColor = BrainTextPrimary,
                unfocusedTextColor = BrainTextPrimary,
                focusedBorderColor = BrainPurplePrimary,
                unfocusedBorderColor = BrainBorder,
                cursorColor = BrainPurplePrimary
            ),
            singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(BrainPurplePrimary),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onSend) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

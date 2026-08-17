package com.brain.offlineai.ui.screens.apikeys

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.apikeys.ExpirationOption
import com.brain.offlineai.ui.theme.*

/** Screen 6 from the mockup ("Create API Key - New Secure Key"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateApiKeyScreen(
    viewModel: ApiKeysViewModel = viewModel(),
    onBack: () -> Unit,
    onKeyCreated: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var expiration by remember { mutableStateOf(ExpirationOption.NEVER) }
    var expanded by remember { mutableStateOf(false) }
    val errorMessage = viewModel.errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrainTextPrimary)
            }
            Text("Create API Key", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))

        Text("Key Name", color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; viewModel.dismissError() },
            placeholder = { Text("Rani Phone", color = BrainTextMuted) },
            singleLine = true,
            isError = errorMessage != null,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrainPurplePrimary,
                unfocusedBorderColor = BrainBorder,
                focusedTextColor = BrainTextPrimary,
                unfocusedTextColor = BrainTextPrimary,
                cursorColor = BrainPurplePrimary
            )
        )
        errorMessage?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = BrainDangerRed, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(22.dp))

        Text("Expiration", color = BrainTextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = expiration.label,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrainPurplePrimary,
                    unfocusedBorderColor = BrainBorder,
                    focusedTextColor = BrainTextPrimary,
                    unfocusedTextColor = BrainTextPrimary
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ExpirationOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { expiration = option; expanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = BrainTextMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (expiration == ExpirationOption.NEVER) "Key will never expire"
                else "Key will expire ${expiration.days} days after creation",
                color = BrainTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { viewModel.createKey(name, expiration) { id -> onKeyCreated(id) } },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BrainPurplePrimary, disabledContainerColor = BrainBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Generate Key")
        }
    }
}

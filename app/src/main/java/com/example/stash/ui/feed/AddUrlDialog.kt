package com.example.stash.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.stash.ui.components.AnimatedBlurGradient
import com.example.stash.ui.components.GeminiMark

/** Manual URL entry, the in-app counterpart to the share-sheet entry point. */
@Composable
fun AddUrlDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    // rememberSaveable rather than remember: a rotation mid-typing should not discard the URL.
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedBlurGradient(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        blurRadius = 24.dp,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color.Black.copy(alpha = 0.25f)),
                    )
                    Icon(
                        imageVector = GeminiMark,
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Add to Stash")
            }
        },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(url) },
                enabled = url.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


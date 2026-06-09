package com.onyxlauncher.ui.home

import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onyxlauncher.ui.component.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuSheet(
    state: ContextMenuState,
    viewModel: HomeViewModel,
    onStartRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = state.app

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1F),
        dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            // ── App header ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app = app, iconSize = 48.dp, showLabel = false)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = app.customLabel ?: app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // ── Dynamic shortcuts ─────────────────────────────────────────
            if (state.shortcuts.isNotEmpty()) {
                state.shortcuts.forEach { shortcut ->
                    val userManager = context.getSystemService(UserManager::class.java)!!
                    val userHandle = userManager.getUserForSerialNumber(app.userSerial)
                    ShortcutRow(
                        label = shortcut.shortLabel?.toString() ?: shortcut.id,
                        icon = Icons.Default.PlayArrow,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            if (userHandle != null) {
                                viewModel.launchShortcut(context, shortcut, userHandle)
                            }
                            onDismiss()
                        },
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }

            // ── Standard actions ──────────────────────────────────────────
            ShortcutRow(
                label = "Remove from Home",
                icon = Icons.Default.Close,
                tint = Color(0xFFFF6B6B),
                onClick = {
                    viewModel.removeItem(state.item)
                    onDismiss()
                },
            )
            ShortcutRow(
                label = "Rename",
                icon = Icons.Default.Edit,
                onClick = {
                    onDismiss()
                    onStartRename()
                },
            )
            ShortcutRow(
                label = "Change icon",
                icon = Icons.Default.Image,
                onClick = {
                    onDismiss()
                    viewModel.openIconChooser(app)
                },
            )
            ShortcutRow(
                label = "App info",
                icon = Icons.Outlined.Info,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${app.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun ShortcutRow(
    label: String,
    icon: ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rename dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun RenameDialog(
    currentLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename app") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("App name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = Color(0xFF1A1A1F),
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )
}

package com.onyxlauncher.ui.folder

import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.domain.model.Folder
import com.onyxlauncher.ui.component.AppIcon
import com.onyxlauncher.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSheet(
    state: com.onyxlauncher.ui.home.FolderSheetState,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
) {
    val folder = state.folder
    val apps = state.apps
    val context = LocalContext.current
    var editingName by remember { mutableStateOf(false) }
    var nameInput by remember(folder.name) { mutableStateOf(folder.name) }
    val keyboard = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1F),
        dragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .size(36.dp, 4.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(8.dp))

                // Folder name
                if (editingName) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.renameFolder(folder.id, nameInput)
                            editingName = false
                            keyboard?.hide()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { editingName = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename folder",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .navigationBarsPadding(),
        ) {
            items(
                count = folder.items.size,
                key = { folder.items[it].flattenToString() },
            ) { idx ->
                val comp = folder.items[idx]
                val app = apps.find { it.componentName == comp }
                if (app != null) {
                    AppIcon(
                        app = app,
                        iconSize = 52.dp,
                        showLabel = true,
                        labelSizeSp = 10,
                        modifier = Modifier
                            .clickable {
                                val launcherApps = context.getSystemService(LauncherApps::class.java)!!
                                val userManager = context.getSystemService(UserManager::class.java)!!
                                val uh = userManager.getUserForSerialNumber(app.userSerial)
                                    ?: return@clickable
                                launcherApps.startMainActivity(app.componentName, uh, null, null)
                                onDismiss()
                            }
                            .padding(4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Folder icon: 2×2 mini grid of the first 4 app icons inside the folder
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FolderIcon(
    folder: Folder,
    apps: List<App>,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val preview = remember(folder.items, apps) {
        folder.items.take(4).mapNotNull { comp -> apps.find { it.componentName == comp } }
    }
    val half = size / 2 - 2.dp
    Box(
        modifier = modifier
            .size(size)
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(size * 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        if (preview.isEmpty()) {
            Text("📁", textAlign = TextAlign.Center)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val rows = preview.chunked(2)
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.Center) {
                        row.forEach { app ->
                            AppIcon(
                                app = app,
                                iconSize = half,
                                showLabel = false,
                                modifier = Modifier.padding(1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

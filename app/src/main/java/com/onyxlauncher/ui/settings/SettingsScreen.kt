package com.onyxlauncher.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onyxlauncher.domain.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
) {
    val s by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)

    // SAF document pickers for backup/restore.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val r = viewModel.exportTo(uri)
            Toast.makeText(context, if (r.isSuccess) "Backup exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val r = viewModel.importFrom(uri)
            Toast.makeText(
                context,
                if (r.isSuccess) "Backup restored" else "Import failed: ${r.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0B0B0F),
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0B0F)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Layout ────────────────────────────────────────────────────
            SettingsSection("LAYOUT") {
                SettingsSlider("Columns", s.homeColumns, 3..8) { viewModel.setColumns(it) }
                SettingsDivider()
                SettingsSlider("Rows", s.homeRows, 3..8) { viewModel.setRows(it) }
                SettingsDivider()
                SettingsSlider("Dock slots", s.dockSlots, 3..7) { viewModel.setDockSlots(it) }
                SettingsDivider()
                SettingsSlider("Icon size", s.iconSizeDp, 40..72, suffix = "dp") { viewModel.setIconSize(it) }
            }

            // ── Labels ────────────────────────────────────────────────────
            SettingsSection("LABELS") {
                SettingsSwitch("Show labels", checked = s.showLabels) { viewModel.setShowLabels(it) }
                if (s.showLabels) {
                    SettingsDivider()
                    SettingsSlider("Label size", s.labelSizeSp, 8..16, suffix = "sp") { viewModel.setLabelSize(it) }
                }
            }

            // ── Appearance ────────────────────────────────────────────────
            SettingsSection("APPEARANCE") {
                SettingsSegmented(
                    "Theme",
                    options = listOf(
                        ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark", ThemeMode.AMOLED to "AMOLED",
                    ),
                    selected = s.themeMode,
                ) { viewModel.setThemeMode(it) }
                SettingsDivider()
                SettingsSwitch(
                    "Dynamic color",
                    sublabel = "Use Material You colors from your wallpaper",
                    checked = s.useDynamicColor,
                ) { viewModel.setDynamicColor(it) }
                SettingsDivider()
                SettingsSegmented(
                    "Page indicator",
                    options = listOf(
                        PageIndicatorStyle.DOTS to "Dots", PageIndicatorStyle.LINES to "Lines",
                        PageIndicatorStyle.NONE to "None",
                    ),
                    selected = s.pageIndicator,
                ) { viewModel.setPageIndicator(it) }
                SettingsDivider()
                SettingsSegmented(
                    "Folder shape",
                    options = listOf(FolderStyle.ROUND to "Round", FolderStyle.SQUARE to "Square"),
                    selected = s.folderStyle,
                ) { viewModel.setFolderStyle(it) }
            }

            // ── Gestures ──────────────────────────────────────────────────
            SettingsSection("GESTURES") {
                GestureRow("Swipe up", s.swipeUp) { viewModel.setSwipeUp(it) }
                SettingsDivider()
                GestureRow("Swipe down", s.swipeDown) { viewModel.setSwipeDown(it) }
                SettingsDivider()
                GestureRow("Double tap", s.doubleTap) { viewModel.setDoubleTap(it) }
                SettingsDivider()
                GestureRow("Two-finger swipe up", s.twoFingerSwipeUp) { viewModel.setTwoFingerSwipeUp(it) }
            }

            // ── Wallpaper ─────────────────────────────────────────────────
            SettingsSection("WALLPAPER") {
                SettingsSwitch(
                    "Time-of-day colors",
                    sublabel = "Palette shifts from dawn to night",
                    checked = s.useTimeOfDayColor,
                ) { viewModel.setUseTimeOfDayColor(it) }
                SettingsDivider()
                SettingsSwitch(
                    "React to usage",
                    sublabel = "Needs usage access; more energetic when busy",
                    checked = s.useUsageSignal,
                ) { viewModel.setUseUsageSignal(it) }
                SettingsDivider()
                SettingsSwitch("Auto-refresh daily", checked = s.autoRefresh) { viewModel.setAutoRefresh(it) }
            }

            // ── Backup & restore ──────────────────────────────────────────
            SettingsSection("BACKUP & RESTORE") {
                SettingsButtonRow("Export backup", "Save settings + layout to a JSON file") {
                    exportLauncher.launch("onyx-backup.json")
                }
                SettingsDivider()
                SettingsButtonRow("Import backup", "Restore from a JSON file") {
                    importLauncher.launch(arrayOf("application/json"))
                }
            }

            Spacer(Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun GestureRow(label: String, value: GestureAction, onSelect: (GestureAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = GestureAction.entries
    Box {
        SettingsButtonRow(label, sublabel = value.displayName()) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.displayName()) },
                    onClick = { onSelect(action); expanded = false },
                )
            }
        }
    }
}

private fun GestureAction.displayName(): String = when (this) {
    GestureAction.NONE -> "None"
    GestureAction.OPEN_DRAWER -> "Open app drawer"
    GestureAction.OPEN_SEARCH -> "Open search"
    GestureAction.NOTIFICATIONS -> "Notifications"
    GestureAction.LOCK_SCREEN -> "Lock screen"
    GestureAction.CUSTOM_APP -> "Custom app"
}

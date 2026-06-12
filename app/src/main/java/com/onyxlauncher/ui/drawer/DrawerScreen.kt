package com.onyxlauncher.ui.drawer

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.ui.component.AppIcon
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun DrawerScreen(
    viewModel: DrawerViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val apps by viewModel.apps.collectAsState()
    val query by viewModel.query.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // Back closes the drawer (the launcher is HOME, so back is otherwise a no-op).
    androidx.activity.compose.BackHandler(enabled = visible) {
        viewModel.setQuery("")
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xF0101013)),  // near-opaque deep black, clearly distinct
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp, bottom = 4.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(2.dp),
                        )
                        .clickable { onDismiss() },
                )

                SearchBar(
                    query = query,
                    onQueryChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                AppGrid(
                    apps = apps,
                    columns = settings.homeColumns,
                    iconSizeDp = settings.iconSizeDp,
                    showLabels = settings.showLabels,
                    labelSizeSp = settings.labelSizeSp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search apps…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            focusedBorderColor = Color.White.copy(alpha = 0.5f),
            unfocusedTextColor = Color.White,
            focusedTextColor = Color.White,
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
            focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.5f),
            focusedLeadingIconColor = Color.White,
        ),
    )
}

@Composable
private fun AppGrid(
    apps: List<App>,
    columns: Int,
    iconSizeDp: Int,
    showLabels: Boolean,
    labelSizeSp: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            count = apps.size,
            key = { apps[it].key },
        ) { index ->
            val app = apps[index]
            AppIcon(
                app = app,
                iconSize = iconSizeDp.dp,
                showLabel = showLabels,
                labelSizeSp = labelSizeSp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { launchApp(context, app) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

private fun launchApp(context: android.content.Context, app: App) {
    val launcherApps = context.getSystemService(LauncherApps::class.java)!!
    val userManager = context.getSystemService(UserManager::class.java)!!
    val userHandle = userManager.getUserForSerialNumber(app.userSerial) ?: return
    launcherApps.startMainActivity(app.componentName, userHandle, null, null)
}

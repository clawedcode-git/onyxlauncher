package com.onyxlauncher.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.onyxlauncher.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val widgetManager = remember { AppWidgetManager.getInstance(context) }
    val providers = remember {
        @Suppress("DEPRECATION")
        widgetManager.getInstalledProviders()
            .sortedBy { it.loadLabel(context.packageManager) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Add Widget",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(providers) { providerInfo ->
                WidgetProviderRow(
                    providerInfo = providerInfo,
                    onClick = {
                        viewModel.startWidgetAdd(providerInfo)
                        onDismiss()
                    },
                )
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun WidgetProviderRow(
    providerInfo: AppWidgetProviderInfo,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager

    val label = remember(providerInfo) { providerInfo.loadLabel(pm) }
    val iconBitmap: ImageBitmap? = remember(providerInfo) {
        runCatching {
            pm.getApplicationIcon(providerInfo.provider.packageName).toBitmap().asImageBitmap()
        }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = label,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = providerInfo.provider.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

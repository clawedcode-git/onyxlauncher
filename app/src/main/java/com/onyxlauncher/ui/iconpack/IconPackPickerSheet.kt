package com.onyxlauncher.ui.iconpack

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.onyxlauncher.domain.model.IconPack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPackPickerSheet(
    packs: List<IconPack>,
    activePackage: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1F),
    ) {
        Text(
            text = "Icon Pack",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // System default (reset) row
            item {
                IconPackRow(
                    label = "System default",
                    packageName = null,
                    selected = activePackage.isNullOrEmpty(),
                    onClick = { onSelect(null) },
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }

            if (packs.isEmpty()) {
                item {
                    Text(
                        text = "No icon packs installed.\nInstall one from the Play Store or F-Droid.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
                items(packs, key = { it.packageName }) { pack ->
                    IconPackRow(
                        label = pack.label,
                        packageName = pack.packageName,
                        selected = pack.packageName == activePackage,
                        onClick = { onSelect(pack.packageName) },
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun IconPackRow(
    label: String,
    packageName: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val iconBmp = remember(packageName) {
        if (packageName == null) null
        else runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconBmp != null) {
            Image(bitmap = iconBmp, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

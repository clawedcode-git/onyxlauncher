package com.onyxlauncher.ui.iconpack

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.domain.model.IconPack
import com.onyxlauncher.onyxApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconChooserSheet(
    app: App,
    packs: List<IconPack>,
    onSelectPack: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1F),
    ) {
        Text(
            text = "Change icon · ${app.customLabel ?: app.label}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Reset to default
            item {
                IconPreviewTile(
                    app = app,
                    packPackage = null,   // null = system / active pack default
                    label = "Default",
                    onClick = onReset,
                )
            }
            items(packs, key = { it.packageName }) { pack ->
                IconPreviewTile(
                    app = app,
                    packPackage = pack.packageName,
                    label = pack.label,
                    onClick = { onSelectPack(pack.packageName) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun IconPreviewTile(
    app: App,
    packPackage: String?,
    label: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val repo = remember { (context.applicationContext as android.app.Application).onyxApp.iconPackRepository }
    val sizePx = with(density) { 48.dp.toPx() }.toInt()

    var preview by remember(app.key, packPackage) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(app.key, packPackage) {
        preview = withContext(Dispatchers.IO) {
            val base = runCatching {
                val la = context.getSystemService(android.content.pm.LauncherApps::class.java)!!
                val um = context.getSystemService(android.os.UserManager::class.java)!!
                val uh = um.getUserForSerialNumber(app.userSerial) ?: return@runCatching null
                la.getActivityList(app.packageName, uh)
                    .firstOrNull { it.componentName.className == app.activityName }
                    ?.getIcon(context.resources.displayMetrics.densityDpi)
            }.getOrNull() ?: return@withContext null

            if (packPackage == null) base
            else runCatching { repo.getThemedIcon(app.componentName, packPackage, base, sizePx) }
                .getOrDefault(base)
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val d = preview
        if (d != null) {
            Image(
                bitmap = remember(d) { d.toBitmap(sizePx, sizePx).asImageBitmap() },
                contentDescription = label,
                modifier = Modifier.size(48.dp),
            )
        } else {
            Box(
                Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

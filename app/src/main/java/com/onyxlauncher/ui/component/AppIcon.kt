package com.onyxlauncher.ui.component

import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.onyxlauncher.domain.model.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIcon(
    app: App,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelSizeSp: Int = 11,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var drawable by remember(app.key) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(app.key) {
        drawable = withContext(Dispatchers.IO) {
            runCatching {
                val launcherApps = context.getSystemService(LauncherApps::class.java)!!
                val userManager = context.getSystemService(UserManager::class.java)!!
                val userHandle: UserHandle = userManager.getUserForSerialNumber(app.userSerial)
                    ?: return@runCatching null
                launcherApps.getActivityList(app.packageName, userHandle)
                    .firstOrNull { it.componentName.className == app.activityName }
                    ?.getIcon(context.resources.displayMetrics.densityDpi)
            }.getOrNull()
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        drawable?.let { d ->
            val bmp = remember(d) { d.toBitmap(iconSize.value.toInt(), iconSize.value.toInt()) }
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = app.customLabel ?: app.label,
                modifier = Modifier.size(iconSize),
            )
        } ?: Box(Modifier.size(iconSize)) // placeholder while loading

        if (showLabel) {
            Text(
                text = app.customLabel ?: app.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = labelSizeSp.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = iconSize + 8.dp)
                    .paddingFromBaseline(top = 4.dp),
            )
        }
    }
}

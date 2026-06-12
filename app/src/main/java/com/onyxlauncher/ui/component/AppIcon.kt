package com.onyxlauncher.ui.component

import android.content.Context
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.onyxlauncher.data.iconpack.IconPackRepository
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.onyxApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The package name of the currently active icon pack, or null for system icons.
 * Non-static so that switching the pack recomposes every [AppIcon] live.
 */
val LocalActiveIconPack = compositionLocalOf<String?> { null }

@Composable
fun AppIcon(
    app: App,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelSizeSp: Int = 11,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val activePack = LocalActiveIconPack.current
    val sizePx = with(density) { iconSize.toPx() }.toInt().coerceAtLeast(1)
    val repo = remember { (context.applicationContext as android.app.Application).onyxApp.iconPackRepository }

    // Re-resolve whenever the app, the active pack, or a per-icon override changes.
    var drawable by remember(app.key, activePack, app.customIconKey, sizePx) {
        mutableStateOf<Drawable?>(null)
    }

    LaunchedEffect(app.key, activePack, app.customIconKey, sizePx) {
        drawable = withContext(Dispatchers.IO) {
            resolveIcon(context, repo, app, activePack, sizePx)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        drawable?.let { d ->
            val bmp = remember(d, sizePx) { d.toBitmap(sizePx, sizePx) }
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = app.customLabel ?: app.label,
                modifier = Modifier.size(iconSize),
            )
        } ?: Box(Modifier.size(iconSize)) // placeholder while loading

        if (showLabel) {
            Text(
                text = app.customLabel ?: app.label,
                // White + drop shadow so labels stay readable over any wallpaper.
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = labelSizeSp.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f),
                        offset = androidx.compose.ui.geometry.Offset(0f, 1.5f),
                        blurRadius = 4f,
                    ),
                ),
                color = androidx.compose.ui.graphics.Color.White,
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

/**
 * Resolution order:
 *   1. per-icon override "packPkg/drawableName" → that exact drawable
 *   2. active icon pack → themed (or normalised) icon
 *   3. system icon
 */
private fun resolveIcon(
    context: Context,
    repo: IconPackRepository,
    app: App,
    activePack: String?,
    sizePx: Int,
): Drawable? {
    val base = loadSystemIcon(context, app) ?: return null

    // 1. Per-icon override
    app.customIconKey?.let { key ->
        when {
            // "pack:<packPkg>" → render this app through that pack (themed or normalised)
            key.startsWith("pack:") -> {
                val packPkg = key.removePrefix("pack:")
                if (packPkg.isNotEmpty()) {
                    return runCatching {
                        repo.getThemedIcon(app.componentName, packPkg, base, sizePx)
                    }.getOrDefault(base)
                }
            }
            // "<packPkg>/<drawableName>" → that exact drawable
            key.contains('/') -> {
                val slash = key.indexOf('/')
                val packPkg = key.substring(0, slash)
                val drawableName = key.substring(slash + 1)
                repo.loadDrawableByName(packPkg, drawableName, sizePx)?.let { return it }
            }
        }
    }

    // 2. Active icon pack
    if (!activePack.isNullOrEmpty()) {
        return runCatching {
            repo.getThemedIcon(app.componentName, activePack, base, sizePx)
        }.getOrDefault(base)
    }

    // 3. System icon
    return base
}

private fun loadSystemIcon(context: Context, app: App): Drawable? = runCatching {
    val launcherApps = context.getSystemService(LauncherApps::class.java)!!
    val userManager = context.getSystemService(UserManager::class.java)!!
    val userHandle: UserHandle = userManager.getUserForSerialNumber(app.userSerial)
        ?: return@runCatching null
    launcherApps.getActivityList(app.packageName, userHandle)
        .firstOrNull { it.componentName.className == app.activityName }
        ?.getIcon(context.resources.displayMetrics.densityDpi)
}.getOrNull()

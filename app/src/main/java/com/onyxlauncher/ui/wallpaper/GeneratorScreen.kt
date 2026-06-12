package com.onyxlauncher.ui.wallpaper

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyxlauncher.data.wallpaper.WallpaperRepository
import com.onyxlauncher.domain.model.WallpaperStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel,
    onClose: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onConfigureLiveWallpaper: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // The launcher is the HOME activity — back must close this overlay, not no-op.
    androidx.activity.compose.BackHandler(onBack = onClose)

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0B0B0F),
        topBar = {
            TopAppBar(
                title = { Text("Wallpaper", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveFavorite() }) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Save favorite", tint = Color.White)
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Live preview ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF15151B)),
                contentAlignment = Alignment.Center,
            ) {
                state.preview?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Wallpaper preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                    )
                }
                if (state.rendering) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
                }
            }

            // ── Style selector ────────────────────────────────────────────
            StyleSelector(
                selected = state.preset.style,
                onSelect = viewModel::setStyle,
            )

            Spacer(Modifier.height(12.dp))

            // ── Quick actions row ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionChip("Shuffle", Icons.Default.Shuffle, onClick = viewModel::shuffleSeed)
                if (state.paletteLocked) {
                    ActionChip("Unlock", Icons.Default.LockOpen, active = true, onClick = viewModel::unlockPalette)
                } else {
                    ActionChip("Lock palette", Icons.Default.Lock, onClick = viewModel::lockPalette)
                }
                ActionChip(
                    if (state.preset.isLive) "Live ✓" else "Live",
                    Icons.Default.Animation,
                    active = state.preset.isLive,
                    onClick = {
                        // Capture the pre-toggle value: the StateFlow update is async,
                        // so reading state after toggleLive() races recomposition.
                        val wasLive = state.preset.isLive
                        viewModel.toggleLive()
                        if (!wasLive) onConfigureLiveWallpaper()
                    },
                )
            }

            // ── Usage-access hint ─────────────────────────────────────────
            if (!state.hasUsageAccess) {
                Spacer(Modifier.height(8.dp))
                UsageAccessHint(onGrant = onRequestUsageAccess)
            }

            // ── Favorites ─────────────────────────────────────────────────
            if (state.favorites.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Favorites",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Start),
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.favorites, key = { it.id }) { fav ->
                        Box(
                            modifier = Modifier
                                .size(44.dp, 72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1C1C24))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                .clickable { viewModel.loadPreset(fav) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                fav.style.name.take(1),
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            // ── Apply buttons ─────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.apply(WallpaperRepository.Target.HOME) },
                    modifier = Modifier.weight(1f),
                ) { Text("Home") }
                OutlinedButton(
                    onClick = { viewModel.apply(WallpaperRepository.Target.LOCK) },
                    modifier = Modifier.weight(1f),
                ) { Text("Lock") }
                Button(
                    onClick = { viewModel.apply(WallpaperRepository.Target.BOTH) },
                    modifier = Modifier.weight(1f),
                ) { Text("Both") }
            }
        }
    }
}

@Composable
private fun StyleSelector(selected: WallpaperStyle, onSelect: (WallpaperStyle) -> Unit) {
    val styles = listOf(
        WallpaperStyle.GRADIENT_MESH to "Mesh",
        WallpaperStyle.FLOW_FIELD to "Flow",
        WallpaperStyle.GEOMETRIC_FACETS to "Facets",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        styles.forEach { (style, label) ->
            val active = style == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFF1A1A22))
                    .border(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelect(style) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Color.White else Color.White.copy(alpha = 0.7f),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun UsageAccessHint(onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A22))
            .clickable(onClick = onGrant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            "Grant usage access to let your wallpaper react to how you use your phone (optional).",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

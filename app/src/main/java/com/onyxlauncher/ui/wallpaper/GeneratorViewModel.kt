package com.onyxlauncher.ui.wallpaper

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.data.wallpaper.WallpaperRepository
import com.onyxlauncher.domain.model.WallpaperPreset
import com.onyxlauncher.domain.model.WallpaperStyle
import com.onyxlauncher.onyxApp
import com.onyxlauncher.wallpaper.engine.GenerativeParams
import com.onyxlauncher.wallpaper.engine.TimeOfDayPalette
import com.onyxlauncher.wallpaper.engine.UsageSignalMapper
import com.onyxlauncher.wallpaper.engine.WallpaperGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GeneratorUiState(
    val preset: WallpaperPreset,
    val preview: Bitmap? = null,
    val rendering: Boolean = false,
    val paletteLocked: Boolean = false,
    val favorites: List<WallpaperPreset> = emptyList(),
    val hasUsageAccess: Boolean = false,
    val useUsageSignal: Boolean = false,
    val message: String? = null,
)

class GeneratorViewModel(
    app: Application,
    private val repository: WallpaperRepository,
    private val generator: WallpaperGenerator,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(
        GeneratorUiState(
            preset = WallpaperPreset(seed = System.currentTimeMillis(), style = WallpaperStyle.GRADIENT_MESH),
        )
    )
    val state: StateFlow<GeneratorUiState> = _state.asStateFlow()

    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            repository.favorites.collect { favs -> _state.update { it.copy(favorites = favs) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _state.update { it.copy(useUsageSignal = s.useUsageSignal) }
            }
        }
        _state.update { it.copy(hasUsageAccess = repository.hasUsageAccess()) }
        renderPreview()
    }

    // ── editing actions ────────────────────────────────────────────────────
    fun shuffleSeed() {
        _state.update { it.copy(preset = it.preset.copy(id = 0, seed = System.nanoTime())) }
        renderPreview()
    }

    fun setStyle(style: WallpaperStyle) {
        _state.update { it.copy(preset = it.preset.copy(style = style)) }
        renderPreview()
    }

    fun toggleLive() {
        _state.update { it.copy(preset = it.preset.copy(isLive = !it.preset.isLive)) }
    }

    /** Pin the current time-of-day palette so the look stops drifting. */
    fun lockPalette() {
        val palette = generator.paletteFor(_state.value.preset, TimeOfDayPalette.nowFraction())
        _state.update {
            it.copy(preset = it.preset.copy(lockedPalette = palette.colors), paletteLocked = true)
        }
        renderPreview()
    }

    fun unlockPalette() {
        _state.update { it.copy(preset = it.preset.copy(lockedPalette = null), paletteLocked = false) }
        renderPreview()
    }

    // ── persistence ──────────────────────────────────────────────────────────
    fun saveFavorite() {
        viewModelScope.launch {
            val id = repository.save(_state.value.preset.copy(isFavorite = true))
            _state.update { it.copy(preset = it.preset.copy(id = id, isFavorite = true), message = "Saved to favorites") }
        }
    }

    fun loadPreset(preset: WallpaperPreset) {
        _state.update { it.copy(preset = preset, paletteLocked = preset.lockedPalette != null) }
        renderPreview()
    }

    fun deleteFavorite(preset: WallpaperPreset) {
        viewModelScope.launch { repository.delete(preset.id) }
    }

    // ── apply ──────────────────────────────────────────────────────────────
    fun apply(target: WallpaperRepository.Target) {
        viewModelScope.launch {
            _state.update { it.copy(rendering = true) }
            val (w, h) = fullResolution()
            val bmp = renderBitmap(w, h)
            repository.applyWallpaper(bmp, target)
            // Persist active preset id so the live wallpaper / refresh can reuse it.
            val id = if (_state.value.preset.id == 0L) repository.save(_state.value.preset) else _state.value.preset.id
            settingsRepository.update { activePresetId = id }
            _state.update { it.copy(rendering = false, preset = it.preset.copy(id = id), message = "Wallpaper applied") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ── rendering ────────────────────────────────────────────────────────────
    private fun renderPreview() {
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _state.update { it.copy(rendering = true) }
            // Preview at reduced resolution for snappy interaction.
            val w = 480
            val h = 1040
            val bmp = renderBitmap(w, h)
            _state.update { it.copy(preview = bmp, rendering = false) }
        }
    }

    private suspend fun renderBitmap(w: Int, h: Int): Bitmap = withContext(Dispatchers.Default) {
        val preset = _state.value.preset
        val dayFraction = TimeOfDayPalette.nowFraction()
        val palette = generator.paletteFor(preset, dayFraction)
        val warmth = generator.warmthFor(dayFraction)
        val params = paramsFor(warmth)
        generator.render(preset, w, h, params, palette)
    }

    private suspend fun paramsFor(warmth: Float): GenerativeParams {
        val useUsage = _state.value.useUsageSignal && repository.hasUsageAccess()
        return if (useUsage) {
            UsageSignalMapper.map(repository.readUsageSignal(), warmth)
        } else {
            UsageSignalMapper.calm(warmth)
        }
    }

    private fun fullResolution(): Pair<Int, Int> {
        val dm = getApplication<Application>().resources.displayMetrics
        return dm.widthPixels.coerceAtLeast(720) to dm.heightPixels.coerceAtLeast(1280)
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val a = app.onyxApp
            return GeneratorViewModel(app, a.wallpaperRepository, a.wallpaperGenerator, a.settingsRepository) as T
        }
    }
}

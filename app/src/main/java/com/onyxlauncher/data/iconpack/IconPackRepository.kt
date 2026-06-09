package com.onyxlauncher.data.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.onyxlauncher.domain.model.IconPack
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Resolves app icons through an installed third-party icon pack.
 *
 * Resolution per [ComponentName]:
 *   1. appfilter has an explicit drawable → load it from the pack.
 *   2. no explicit drawable but the pack defines iconback/mask/scale →
 *      generate a normalised icon from the original system icon.
 *   3. otherwise → return the original system icon unchanged.
 *
 * A two-level cache keeps switching instant:
 *   - parsedPacks : packPackage → ParsedIconPack  (the appfilter, parsed once)
 *   - bitmapCache : LruCache keyed by (component, packPackage, packVersion, sizePx)
 */
class IconPackRepository(
    private val context: Context,
    private val parser: IconPackParser = IconPackParser(context),
) {
    private val pm: PackageManager = context.packageManager

    // packPackage → parsed appfilter (lazy, parsed on first use)
    private val parsedPacks = ConcurrentHashMap<String, ParsedIconPack>()

    // packPackage → loaded Resources
    private val packResources = ConcurrentHashMap<String, Resources>()

    // Themed bitmaps. ~8 MB ceiling is plenty for visible icons.
    private val bitmapCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // ── installed pack discovery ───────────────────────────────────────────

    /** All intent actions that identify a third-party icon pack. */
    private val iconPackActions = listOf(
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "ch.deletescape.lawnchair.ICONPACK",
        "app.lawnchair.icons.THEMED_ICON",
    )

    /**
     * Enumerate every installed icon pack. De-duplicated by package name.
     * Pure PackageManager work — call off the main thread.
     */
    fun getInstalledPacks(): List<IconPack> {
        val seen = HashMap<String, IconPack>()
        for (action in iconPackActions) {
            val intent = Intent(action)
            val matches = pm.queryIntentActivities(intent, 0)
            for (ri in matches) {
                val pkg = ri.activityInfo.packageName
                if (pkg in seen) continue
                val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
                val version = packVersion(pkg)
                seen[pkg] = IconPack(packageName = pkg, label = label, version = version)
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    private fun packVersion(pkg: String): Int =
        runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionCode
        }.getOrDefault(0)

    // ── icon resolution ────────────────────────────────────────────────────

    /**
     * Return a themed [Drawable] for [component] using [packPackage].
     *
     * @param baseIcon  the original system icon (used for normalised generation
     *                  and as the ultimate fallback).
     * @param sizePx    desired output edge length in pixels.
     */
    fun getThemedIcon(
        component: ComponentName,
        packPackage: String,
        baseIcon: Drawable,
        sizePx: Int,
    ): Drawable {
        val version = packVersion(packPackage)
        val cacheKey = cacheKey(component, packPackage, version, sizePx)
        bitmapCache.get(cacheKey)?.let { return BitmapDrawable(context.resources, it) }

        val parsed = parsedPack(packPackage)
        val res = packResources(packPackage) ?: return baseIcon

        val flat = "${component.packageName}/${component.className}"
        val drawableName = parsed.componentToDrawable[flat]

        val result: Bitmap = when {
            drawableName != null ->
                loadPackBitmap(res, packPackage, drawableName, sizePx)
                    ?: generateNormalised(parsed, res, packPackage, baseIcon, sizePx)

            parsed.hasMaskingConfig ->
                generateNormalised(parsed, res, packPackage, baseIcon, sizePx)

            else -> baseIcon.toSizedBitmap(sizePx)
        }

        bitmapCache.put(cacheKey, result)
        return BitmapDrawable(context.resources, result)
    }

    /** True if the pack has an explicit themed drawable for [component]. */
    fun hasExplicitIcon(component: ComponentName, packPackage: String): Boolean {
        val flat = "${component.packageName}/${component.className}"
        return parsedPack(packPackage).componentToDrawable.containsKey(flat)
    }

    /** The pack's drawable resource name for [component], or null. */
    fun explicitDrawableName(component: ComponentName, packPackage: String): String? {
        val flat = "${component.packageName}/${component.className}"
        return parsedPack(packPackage).componentToDrawable[flat]
    }

    /**
     * Load a specific drawable by name from a pack — used by the per-icon
     * override picker ("change icon" → choose any drawable from any pack).
     */
    fun loadDrawableByName(packPackage: String, drawableName: String, sizePx: Int): Drawable? {
        val res = packResources(packPackage) ?: return null
        val bmp = loadPackBitmap(res, packPackage, drawableName, sizePx) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    // ── normalised icon generation ─────────────────────────────────────────

    /**
     * Compose a consistent icon from the original system icon using the pack's
     * iconback / iconmask / iconupon / scale so un-themed apps match the set.
     */
    private fun generateNormalised(
        parsed: ParsedIconPack,
        res: Resources,
        packPackage: String,
        baseIcon: Drawable,
        sizePx: Int,
    ): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 1. iconback (pseudo-random but deterministic per package name)
        val back = parsed.iconBacks
            .takeIf { it.isNotEmpty() }
            ?.let { it[abs(packPackage.hashCode()) % it.size] }
            ?.let { loadPackBitmap(res, packPackage, it, sizePx) }
        back?.let { canvas.drawBitmap(it, null, Rect(0, 0, sizePx, sizePx), paint) }

        // 2. scaled original icon
        val scaledSize = (sizePx * parsed.scale).toInt().coerceAtLeast(1)
        val offset = (sizePx - scaledSize) / 2
        val baseBmp = baseIcon.toSizedBitmap(scaledSize)

        // 3. apply mask (keep base only where mask is opaque)
        val mask = parsed.iconMasks.firstOrNull()
            ?.let { loadPackBitmap(res, packPackage, it, scaledSize) }
        if (mask != null) {
            val masked = Bitmap.createBitmap(scaledSize, scaledSize, Bitmap.Config.ARGB_8888)
            val mc = Canvas(masked)
            mc.drawBitmap(baseBmp, 0f, 0f, null)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            mc.drawBitmap(mask, 0f, 0f, maskPaint)
            canvas.drawBitmap(masked, offset.toFloat(), offset.toFloat(), paint)
        } else {
            canvas.drawBitmap(baseBmp, offset.toFloat(), offset.toFloat(), paint)
        }

        // 4. iconupon overlay
        parsed.iconUpons.firstOrNull()
            ?.let { loadPackBitmap(res, packPackage, it, sizePx) }
            ?.let { canvas.drawBitmap(it, null, Rect(0, 0, sizePx, sizePx), paint) }

        return out
    }

    // ── low-level loaders ──────────────────────────────────────────────────

    private fun loadPackBitmap(
        res: Resources,
        packPackage: String,
        drawableName: String,
        sizePx: Int,
    ): Bitmap? {
        val id = res.getIdentifier(drawableName, "drawable", packPackage)
        if (id == 0) return null
        return runCatching {
            // Try a straight bitmap decode first (most pack icons are PNG).
            val opts = BitmapFactory.Options()
            val decoded = BitmapFactory.decodeResource(res, id, opts)
            if (decoded != null) {
                Bitmap.createScaledBitmap(decoded, sizePx, sizePx, true)
            } else {
                // Vector / adaptive drawable — render to a canvas.
                @Suppress("DEPRECATION")
                val d = res.getDrawable(id, null) ?: return null
                d.toSizedBitmap(sizePx)
            }
        }.getOrNull()
    }

    private fun parsedPack(packPackage: String): ParsedIconPack =
        parsedPacks.getOrPut(packPackage) { parser.parse(packPackage) }

    private fun packResources(packPackage: String): Resources? =
        packResources.getOrPut(packPackage) {
            runCatching { pm.getResourcesForApplication(packPackage) }.getOrNull()
                ?: return null
        }

    private fun cacheKey(c: ComponentName, pack: String, version: Int, size: Int): String =
        "${c.packageName}/${c.className}|$pack|$version|$size"

    // ── lifecycle ──────────────────────────────────────────────────────────

    /** Drop caches when a pack is updated/removed or the user resets to system. */
    fun invalidate(packPackage: String? = null) {
        if (packPackage == null) {
            parsedPacks.clear()
            packResources.clear()
            bitmapCache.evictAll()
        } else {
            parsedPacks.remove(packPackage)
            packResources.remove(packPackage)
            // bitmapCache keys embed packPackage; a full evict is simplest & cheap.
            bitmapCache.evictAll()
        }
    }
}

// ── Drawable → square Bitmap helper ────────────────────────────────────────
private fun Drawable.toSizedBitmap(size: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return Bitmap.createScaledBitmap(bitmap, size, size, true)
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bmp
}

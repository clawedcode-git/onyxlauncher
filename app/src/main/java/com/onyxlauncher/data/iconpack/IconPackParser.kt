package com.onyxlauncher.data.iconpack

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Result of parsing an icon pack's appfilter / theme configuration.
 *
 * @param componentToDrawable  Flattened "pkg/activity" → drawable resource name.
 *                             Note: many packs key by ComponentInfo{pkg/activity};
 *                             we normalise to "pkg/activity".
 * @param iconBacks  Background layers chosen pseudo-randomly per app.
 * @param iconMasks  Alpha masks applied to the (scaled) base icon.
 * @param iconUpons  Foreground overlays drawn on top.
 * @param scale      Fraction (0..1] the original icon is scaled to before masking.
 */
data class ParsedIconPack(
    val componentToDrawable: Map<String, String>,
    val iconBacks: List<String>,
    val iconMasks: List<String>,
    val iconUpons: List<String>,
    val scale: Float,
) {
    val hasMaskingConfig: Boolean
        get() = iconBacks.isNotEmpty() || iconMasks.isNotEmpty() || iconUpons.isNotEmpty()

    companion object {
        val EMPTY = ParsedIconPack(emptyMap(), emptyList(), emptyList(), emptyList(), 1f)
    }
}

/**
 * Parses the de-facto Nova/ADW icon-pack format.
 *
 * Lookup order for appfilter source:
 *   1. assets/appfilter.xml      (most common)
 *   2. res/xml/appfilter.xml     (resource id)
 *
 * The XML contains a flat list of:
 *   <item component="ComponentInfo{pkg/activity}" drawable="icon_name"/>
 * plus optional theming directives:
 *   <iconback img1="..." img2="..."/>
 *   <iconmask img1="..."/>
 *   <iconupon img1="..."/>
 *   <scale factor="0.8"/>
 */
class IconPackParser(private val context: Context) {

    /**
     * Parse the given installed icon-pack package. Returns [ParsedIconPack.EMPTY] on any failure.
     * This is pure I/O + XML work — call it off the main thread.
     */
    fun parse(packPackage: String): ParsedIconPack {
        val res = runCatching {
            context.packageManager.getResourcesForApplication(packPackage)
        }.getOrNull() ?: return ParsedIconPack.EMPTY

        val parser = openAppFilter(res, packPackage) ?: return ParsedIconPack.EMPTY
        return try {
            readAppFilter(parser)
        } finally {
            (parser as? XmlResourceParser)?.close()
        }
    }

    // ── source resolution ──────────────────────────────────────────────────

    private fun openAppFilter(res: Resources, packPackage: String): XmlPullParser? {
        // 1. assets/appfilter.xml
        runCatching {
            val stream: InputStream = res.assets.open("appfilter.xml")
            return android.util.Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(stream, null)
            }
        }

        // 2. res/xml/appfilter.xml
        val xmlId = res.getIdentifier("appfilter", "xml", packPackage)
        if (xmlId != 0) {
            return runCatching { res.getXml(xmlId) as XmlResourceParser }.getOrNull()
        }
        return null
    }

    // ── XML reader ───────────────────────────────────────────────────────────

    /** Test entry point: parse an appfilter XML document from a raw string. */
    fun parseFromXml(xml: String): ParsedIconPack {
        val parser = android.util.Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(java.io.StringReader(xml))
        }
        return readAppFilter(parser)
    }

    private fun readAppFilter(parser: XmlPullParser): ParsedIconPack {
        val componentMap = HashMap<String, String>(2048)
        val iconBacks = ArrayList<String>(4)
        val iconMasks = ArrayList<String>(2)
        val iconUpons = ArrayList<String>(2)
        var scale = 1f

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                            normaliseComponent(component)?.let { key ->
                                // First definition wins (matches Nova behaviour)
                                componentMap.putIfAbsent(key, drawable)
                            }
                        }
                    }
                    "iconback" -> collectImgs(parser, iconBacks)
                    "iconmask" -> collectImgs(parser, iconMasks)
                    "iconupon" -> collectImgs(parser, iconUpons)
                    "scale" -> {
                        parser.getAttributeValue(null, "factor")
                            ?.toFloatOrNull()
                            ?.let { scale = it.coerceIn(0.1f, 1f) }
                    }
                }
            }
            event = parser.next()
        }

        return ParsedIconPack(
            componentToDrawable = componentMap,
            iconBacks = iconBacks,
            iconMasks = iconMasks,
            iconUpons = iconUpons,
            scale = scale,
        )
    }

    /** iconback/mask/upon can declare multiple imgs: img1, img2, … imgN. */
    private fun collectImgs(parser: XmlPullParser, out: MutableList<String>) {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            if (name.startsWith("img")) {
                parser.getAttributeValue(i)?.takeIf { it.isNotBlank() }?.let(out::add)
            }
        }
    }

    /**
     * Turn "ComponentInfo{pkg/activity}" (or a bare "pkg/activity") into "pkg/activity".
     * Returns null for the placeholder ":NULL:" entries some packs include.
     */
    private fun normaliseComponent(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == ":NULL:") return null
        val inner = when {
            trimmed.startsWith("ComponentInfo{") && trimmed.endsWith("}") ->
                trimmed.substring("ComponentInfo{".length, trimmed.length - 1)
            else -> trimmed
        }
        return inner.takeIf { it.contains('/') }
    }
}

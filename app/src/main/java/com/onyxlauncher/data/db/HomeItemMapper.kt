package com.onyxlauncher.data.db

import android.content.ComponentName
import com.onyxlauncher.data.db.entity.HomeItemEntity
import com.onyxlauncher.domain.model.HomeItem

/**
 * Maps between the flat Room entity (type + JSON payload string) and the sealed
 * domain model.  Uses a hand-rolled, minimal JSON encoder/decoder so that this
 * file has zero Android dependencies and can be exercised in plain JVM unit tests.
 */
object HomeItemMapper {

    // ── Entity → Domain ──────────────────────────────────────────────────────

    fun HomeItemEntity.toDomain(): HomeItem? {
        return when (type) {
            "shortcut" -> {
                val m = payload.fields()
                val pkg = m["pkg"] ?: return null
                val cls = m["cls"] ?: return null
                HomeItem.Shortcut(
                    id = id, page = page, gridX = gridX, gridY = gridY,
                    componentName = ComponentName(pkg, cls),
                    userSerial = m["user"]?.toLongOrNull() ?: 0L,
                )
            }
            "folder" -> {
                val m = payload.fields()
                val fid = m["folderId"]?.toLongOrNull() ?: return null
                HomeItem.FolderRef(id = id, page = page, gridX = gridX, gridY = gridY, folderId = fid)
            }
            "widget" -> {
                val m = payload.fields()
                val wid = m["widgetId"]?.toIntOrNull() ?: return null
                HomeItem.WidgetRef(
                    id = id, page = page, gridX = gridX, gridY = gridY,
                    appWidgetId = wid,
                    spanX = m["spanX"]?.toIntOrNull() ?: 1,
                    spanY = m["spanY"]?.toIntOrNull() ?: 1,
                )
            }
            else -> null
        }
    }

    // ── Domain → Entity ──────────────────────────────────────────────────────

    fun HomeItem.toEntity(): HomeItemEntity {
        val (type, payload) = when (this) {
            is HomeItem.Shortcut -> "shortcut" to buildPayload(
                "pkg" to componentName.packageName,
                "cls" to componentName.className,
                "user" to userSerial.toString(),
            )
            is HomeItem.FolderRef -> "folder" to buildPayload(
                "folderId" to folderId.toString(),
            )
            is HomeItem.WidgetRef -> "widget" to buildPayload(
                "widgetId" to appWidgetId.toString(),
                "spanX" to spanX.toString(),
                "spanY" to spanY.toString(),
            )
        }
        return HomeItemEntity(
            id = id, page = page, gridX = gridX, gridY = gridY,
            type = type, payload = payload,
        )
    }

    // ── Minimal JSON helpers (no Android deps) ────────────────────────────────

    /** Encode a fixed set of string key/value pairs as a JSON object. */
    private fun buildPayload(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"$k\":\"$v\""
        }

    /**
     * Decode a simple flat JSON object produced by [buildPayload].
     * Handles only string values (all numbers are stored as strings and
     * parsed back to their types at the call site).
     */
    private fun String.fields(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        // Match "key":"value" pairs — values contain no quotes.
        val pattern = Regex(""""(\w+)":"([^"]*)"""")
        pattern.findAll(this).forEach { m ->
            result[m.groupValues[1]] = m.groupValues[2]
        }
        return result
    }
}

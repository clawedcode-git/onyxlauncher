package com.onyxlauncher.data.db

import android.content.ComponentName
import com.onyxlauncher.data.db.entity.HomeItemEntity
import com.onyxlauncher.domain.model.HomeItem
import org.json.JSONArray
import org.json.JSONObject

object HomeItemMapper {

    fun HomeItemEntity.toDomain(): HomeItem? = when (type) {
        "shortcut" -> {
            val j = JSONObject(payload)
            HomeItem.Shortcut(
                id = id,
                page = page,
                gridX = gridX,
                gridY = gridY,
                componentName = ComponentName(j.getString("pkg"), j.getString("cls")),
                userSerial = j.getLong("user"),
            )
        }
        "folder" -> {
            val j = JSONObject(payload)
            HomeItem.FolderRef(
                id = id,
                page = page,
                gridX = gridX,
                gridY = gridY,
                folderId = j.getLong("folderId"),
            )
        }
        "widget" -> {
            val j = JSONObject(payload)
            HomeItem.WidgetRef(
                id = id,
                page = page,
                gridX = gridX,
                gridY = gridY,
                appWidgetId = j.getInt("widgetId"),
                spanX = j.optInt("spanX", 1),
                spanY = j.optInt("spanY", 1),
            )
        }
        else -> null
    }

    fun HomeItem.toEntity(): HomeItemEntity {
        val (type, payload) = when (this) {
            is HomeItem.Shortcut -> "shortcut" to JSONObject().apply {
                put("pkg", componentName.packageName)
                put("cls", componentName.className)
                put("user", userSerial)
            }.toString()
            is HomeItem.FolderRef -> "folder" to JSONObject().apply {
                put("folderId", folderId)
            }.toString()
            is HomeItem.WidgetRef -> "widget" to JSONObject().apply {
                put("widgetId", appWidgetId)
                put("spanX", spanX)
                put("spanY", spanY)
            }.toString()
        }
        return HomeItemEntity(id = id, page = page, gridX = gridX, gridY = gridY, type = type, payload = payload)
    }
}

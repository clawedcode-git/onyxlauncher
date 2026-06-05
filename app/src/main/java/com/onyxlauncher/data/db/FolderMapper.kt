package com.onyxlauncher.data.db

import android.content.ComponentName
import com.onyxlauncher.data.db.entity.FolderEntity
import com.onyxlauncher.domain.model.Folder
import org.json.JSONArray

object FolderMapper {
    fun FolderEntity.toDomain(): Folder {
        val components = runCatching {
            val arr = JSONArray(items)
            (0 until arr.length()).mapNotNull {
                ComponentName.unflattenFromString(arr.getString(it))
            }
        }.getOrDefault(emptyList())
        return Folder(id = id, name = name, items = components, customIconKey = customIconKey)
    }

    fun Folder.toEntity(): FolderEntity {
        val arr = JSONArray()
        items.forEach { arr.put(it.flattenToString()) }
        return FolderEntity(
            id = id,
            name = name,
            items = arr.toString(),
            customIconKey = customIconKey,
        )
    }
}

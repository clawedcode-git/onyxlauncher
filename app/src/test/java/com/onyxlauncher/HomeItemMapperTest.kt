package com.onyxlauncher

import android.content.ComponentName
import com.onyxlauncher.data.db.HomeItemMapper.toDomain
import com.onyxlauncher.data.db.HomeItemMapper.toEntity
import com.onyxlauncher.data.db.entity.HomeItemEntity
import com.onyxlauncher.domain.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HomeItemMapperTest {

    @Test
    fun `shortcut round-trips through entity`() {
        val original = HomeItem.Shortcut(
            id = 42L,
            page = 0,
            gridX = 2,
            gridY = 3,
            componentName = ComponentName("com.example.app", "com.example.app.MainActivity"),
            userSerial = 0L,
        )
        val entity = original.toEntity()
        val restored = entity.toDomain()

        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `folder ref round-trips through entity`() {
        val original = HomeItem.FolderRef(id = 7L, page = 1, gridX = 0, gridY = 0, folderId = 99L)
        val restored = original.toEntity().toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun `widget ref round-trips through entity`() {
        val original = HomeItem.WidgetRef(
            id = 3L, page = 0, gridX = 1, gridY = 0,
            appWidgetId = 5, spanX = 2, spanY = 1,
        )
        val restored = original.toEntity().toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun `unknown type returns null`() {
        val entity = HomeItemEntity(
            id = 1, page = 0, gridX = 0, gridY = 0,
            type = "unknown", payload = "{}",
        )
        assertNull(entity.toDomain())
    }

    @Test
    fun `payload is valid key-value JSON`() {
        val shortcut = HomeItem.Shortcut(
            id = 1L, page = 0, gridX = 0, gridY = 0,
            componentName = ComponentName("com.foo", "com.foo.Bar"),
            userSerial = 12L,
        )
        val entity = shortcut.toEntity()
        // Verify the payload string contains all expected fields
        assert(entity.payload.contains("\"pkg\":\"com.foo\"")) { "missing pkg: ${entity.payload}" }
        assert(entity.payload.contains("\"cls\":\"com.foo.Bar\"")) { "missing cls: ${entity.payload}" }
        assert(entity.payload.contains("\"user\":\"12\"")) { "missing user: ${entity.payload}" }
    }
}

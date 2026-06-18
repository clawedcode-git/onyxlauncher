package com.onyxlauncher

import androidx.room.Room
import com.onyxlauncher.data.backup.BackupManager
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.data.db.AppDatabase
import com.onyxlauncher.data.db.entity.AppOverrideEntity
import com.onyxlauncher.data.db.entity.FolderEntity
import com.onyxlauncher.data.db.entity.HomeItemEntity
import com.onyxlauncher.data.db.entity.WallpaperPresetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BackupManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var backup: BackupManager

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(ctx)
        backup = BackupManager(ctx, db, settings)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `export then import restores all tables and settings`() = runBlocking {
        // Seed layout
        db.homeItemDao().insertAll(listOf(
            HomeItemEntity(id = 1, page = 0, gridX = 0, gridY = 0, type = "shortcut", payload = """{"pkg":"a","cls":"a.A","user":"0"}"""),
            HomeItemEntity(id = 2, page = -1, gridX = 0, gridY = 0, type = "shortcut", payload = """{"pkg":"b","cls":"b.B","user":"0"}"""),
        ))
        db.folderDao().insert(FolderEntity(id = 1, name = "Tools", items = """["a/a.A"]"""))
        db.appOverrideDao().upsert(AppOverrideEntity(componentName = "a/a.A", userSerial = 0, customLabel = "Renamed"))
        db.wallpaperPresetDao().insert(
            WallpaperPresetEntity(id = 1, seed = 99L, style = "FLOW_FIELD", isFavorite = true, createdAt = 1000L)
        )
        settings.update { homeColumns = 7; themeMode = 3 /* AMOLED */ }

        // Export
        val json = backup.exportToJson()
        assertTrue("backup tagged", json.contains("\"app\""))
        assertTrue(json.contains("onyx-launcher"))

        // Wipe everything to a clean slate
        db.homeItemDao().clear()
        db.folderDao().clear()
        db.appOverrideDao().clear()
        db.wallpaperPresetDao().clear()
        settings.update { homeColumns = 5; themeMode = 2 /* SYSTEM */ }
        assertEquals(0, db.homeItemDao().getAll().size)

        // Import
        backup.importFromJson(json).getOrThrow()

        // Verify layout restored
        assertEquals(2, db.homeItemDao().getAll().size)
        assertEquals(1, db.folderDao().getAll().size)
        assertEquals("Tools", db.folderDao().getAll().first().name)
        val ov = db.appOverrideDao().getAll().first()
        assertEquals("Renamed", ov.customLabel)
        val preset = db.wallpaperPresetDao().getAll().first()
        assertEquals(99L, preset.seed)
        assertEquals("FLOW_FIELD", preset.style)
        assertTrue(preset.isFavorite)

        // Verify settings restored
        val restored = settings.settings.first()
        assertEquals(7, restored.homeColumns)
        assertEquals(com.onyxlauncher.domain.model.ThemeMode.AMOLED, restored.themeMode)
    }

    @Test
    fun `importing a non-onyx json fails cleanly`() = runBlocking {
        val result = backup.importFromJson("""{"app":"some-other-app"}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun `exported json is valid and re-importable twice`() = runBlocking {
        db.homeItemDao().insert(
            HomeItemEntity(id = 5, page = 0, gridX = 2, gridY = 3, type = "widget", payload = """{"widgetId":"7","spanX":"2","spanY":"1"}""")
        )
        val json = backup.exportToJson()
        backup.importFromJson(json).getOrThrow()
        backup.importFromJson(json).getOrThrow()   // idempotent
        assertEquals(1, db.homeItemDao().getAll().size)
        assertEquals("widget", db.homeItemDao().getAll().first().type)
    }
}

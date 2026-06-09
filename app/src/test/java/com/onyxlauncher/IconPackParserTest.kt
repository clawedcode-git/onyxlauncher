package com.onyxlauncher

import com.onyxlauncher.data.iconpack.IconPackParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class IconPackParserTest {

    private val parser get() = IconPackParser(RuntimeEnvironment.getApplication())

    @Test
    fun `parses component to drawable mappings`() {
        val xml = """
            <resources>
                <item component="ComponentInfo{com.android.settings/com.android.settings.Settings}" drawable="settings"/>
                <item component="ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}" drawable="google_chrome"/>
            </resources>
        """.trimIndent()

        val result = parser.parseFromXml(xml)

        assertEquals("settings", result.componentToDrawable["com.android.settings/com.android.settings.Settings"])
        assertEquals("google_chrome", result.componentToDrawable["com.android.chrome/com.google.android.apps.chrome.Main"])
        assertEquals(2, result.componentToDrawable.size)
    }

    @Test
    fun `strips ComponentInfo wrapper and keeps bare components`() {
        val xml = """
            <resources>
                <item component="ComponentInfo{a.b/a.b.C}" drawable="x"/>
                <item component="d.e/d.e.F" drawable="y"/>
            </resources>
        """.trimIndent()

        val result = parser.parseFromXml(xml)

        assertEquals("x", result.componentToDrawable["a.b/a.b.C"])
        assertEquals("y", result.componentToDrawable["d.e/d.e.F"])
    }

    @Test
    fun `first definition wins for duplicate components`() {
        val xml = """
            <resources>
                <item component="ComponentInfo{a/b}" drawable="first"/>
                <item component="ComponentInfo{a/b}" drawable="second"/>
            </resources>
        """.trimIndent()

        val result = parser.parseFromXml(xml)
        assertEquals("first", result.componentToDrawable["a/b"])
    }

    @Test
    fun `ignores NULL and malformed components`() {
        val xml = """
            <resources>
                <item component=":NULL:" drawable="ignored"/>
                <item component="" drawable="empty"/>
                <item component="nocomponentslash" drawable="bad"/>
                <item component="ComponentInfo{ok/ok.Main}" drawable="good"/>
            </resources>
        """.trimIndent()

        val result = parser.parseFromXml(xml)
        assertEquals(1, result.componentToDrawable.size)
        assertEquals("good", result.componentToDrawable["ok/ok.Main"])
    }

    @Test
    fun `parses theme config iconback mask upon and scale`() {
        val xml = """
            <resources>
                <iconback img1="iconback_0" img2="iconback_1"/>
                <iconmask img1="iconmask"/>
                <iconupon img1="iconupon"/>
                <scale factor="0.85"/>
                <item component="ComponentInfo{a/b}" drawable="x"/>
            </resources>
        """.trimIndent()

        val result = parser.parseFromXml(xml)

        assertEquals(listOf("iconback_0", "iconback_1"), result.iconBacks)
        assertEquals(listOf("iconmask"), result.iconMasks)
        assertEquals(listOf("iconupon"), result.iconUpons)
        assertEquals(0.85f, result.scale, 0.0001f)
        assertTrue(result.hasMaskingConfig)
    }

    @Test
    fun `scale is clamped to valid range`() {
        val tooBig = parser.parseFromXml("""<resources><scale factor="5.0"/></resources>""")
        assertEquals(1f, tooBig.scale, 0.0001f)

        val tooSmall = parser.parseFromXml("""<resources><scale factor="0.0"/></resources>""")
        assertEquals(0.1f, tooSmall.scale, 0.0001f)
    }

    @Test
    fun `pack with no theme config reports no masking`() {
        val xml = """
            <resources>
                <item component="ComponentInfo{a/b}" drawable="x"/>
            </resources>
        """.trimIndent()

        val result = parser.parseFromXml(xml)
        assertFalse(result.hasMaskingConfig)
        assertEquals(1f, result.scale, 0.0001f)
    }

    @Test
    fun `empty document yields empty result`() {
        val result = parser.parseFromXml("<resources></resources>")
        assertTrue(result.componentToDrawable.isEmpty())
        assertFalse(result.hasMaskingConfig)
    }
}

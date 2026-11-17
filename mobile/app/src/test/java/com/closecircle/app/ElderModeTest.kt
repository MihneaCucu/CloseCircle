package com.closecircle.app

import androidx.compose.ui.graphics.Color
import com.closecircle.app.data.UserPrefs
import com.closecircle.app.ui.theme.DarkCc
import com.closecircle.app.ui.theme.ELDER_FONT_SCALE
import com.closecircle.app.ui.theme.LightCc
import com.closecircle.app.ui.theme.highContrast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElderModeTest {
    @Test
    fun theChoiceIsOffUntilSetAndSurvivesANewInstanceOnTheSameStore() = runBlocking {
        val store = MemoryDataStore()
        assertFalse(UserPrefs(store).elder.first())
        UserPrefs(store).setElder(true)
        assertTrue(UserPrefs(store).elder.first()) // what "close and reopen the app" reads
        UserPrefs(store).setElder(false)
        assertFalse(UserPrefs(store).elder.first())
    }

    @Test
    fun highContrastUsesTheFullTextColourForSecondaryText() {
        for ((base, dark) in listOf(LightCc to false, DarkCc to true)) {
            val hc = base.highContrast(dark)
            assertEquals(base.text, hc.muted)
            assertEquals(base.text, hc.line)
            assertEquals(base.text, hc.greyFg)
            assertEquals(base.surface, hc.surface) // everything else is unchanged
        }
    }

    @Test
    fun theLightAccentGetsDarkerAndTheDarkOneIsKept() {
        assertNotEquals(LightCc.accent, LightCc.highContrast(false).accent)
        assertEquals(Color(0xFF065F57), LightCc.highContrast(false).accent)
        assertEquals(DarkCc.accent, DarkCc.highContrast(true).accent)
    }

    @Test
    fun textIsMadeLargerNotSmaller() {
        assertTrue(ELDER_FONT_SCALE > 1f)
    }
}

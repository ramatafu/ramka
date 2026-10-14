package com.ramka.app.ui.relay

import com.ramka.app.ui.relayguide.GuideBlock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnGuideContentTest {

    private val allText: String = TurnGuideContent.steps.joinToString("\n") { step ->
        step.title + "\n" + step.blocks.joinToString("\n") { block ->
            when (block) {
                is GuideBlock.Paragraph -> block.text
                is GuideBlock.Command -> block.text
                is GuideBlock.Note -> block.text
            }
        }
    }

    @Test
    fun guideContainsNoHardcodedCredentials() {
        assertFalse(allText.contains("ramka123"))
        assertFalse(allText.contains("ramka:"))
        assertFalse(allText.contains("217.25.238.148"))
    }

    @Test
    fun guideUsesLoginAndPasswordPlaceholders() {
        assertTrue(allText.contains("user=ВАШ_ЛОГИН:ВАШ_ПАРОЛЬ"))
        assertTrue(allText.contains("Логин: ВАШ_ЛОГИН"))
        assertTrue(allText.contains("Пароль: ВАШ_ПАРОЛЬ"))
    }
}

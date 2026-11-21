package com.ramka.app.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class YggmailToggleVisibilityTest {

    @Test fun `Yggmail toggle is visible for every combination of Yggdrasil and Yggmail states`() {
        for (yggdrasil in listOf(false, true)) {
            for (yggmail in listOf(false, true)) {
                assertTrue(
                    "Yggmail должен быть доступен при yggdrasil=$yggdrasil, yggmail=$yggmail",
                    shouldShowYggmailToggle(yggdrasilEnabled = yggdrasil, yggmailEnabled = yggmail)
                )
            }
        }
    }

    @Test fun `Yggmail toggle is visible when both Yggdrasil and Yggmail are off`() =
        assertTrue(shouldShowYggmailToggle(yggdrasilEnabled = false, yggmailEnabled = false))
}

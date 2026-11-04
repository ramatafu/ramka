package com.ramka.app.ui.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAudioPolicyTest {
    @Test fun videoCallStartsOnSpeaker() {
        assertTrue(CallAudioPolicy.speakerOnByDefault(withVideo = true))
    }

    @Test fun audioCallStartsOnEarpiece() {
        assertFalse(CallAudioPolicy.speakerOnByDefault(withVideo = false))
    }
}

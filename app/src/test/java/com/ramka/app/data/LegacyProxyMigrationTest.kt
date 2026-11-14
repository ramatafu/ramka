package com.ramka.app.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import com.ramka.network.proxy.ProxySettings
import com.ramka.network.proxy.ProxyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyProxyMigrationTest {

    @Test
    fun mapsAllFieldsFromOldKeys() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("proxy_is_enabled") to true,
            stringPreferencesKey("proxy_type") to "HTTP",
            stringPreferencesKey("proxy_host") to "proxy.example.org",
            intPreferencesKey("proxy_port") to 8080,
            stringPreferencesKey("proxy_username") to "user",
            stringPreferencesKey("proxy_password") to "secret"
        )
        val s = LegacyProxyMigration.toSettings(prefs)
        assertTrue(s.enabled)
        assertEquals(ProxyType.HTTP, s.type)
        assertEquals("proxy.example.org", s.host)
        assertEquals(8080, s.port)
        assertEquals("user", s.username)
        assertEquals("secret", s.password)
    }

    @Test
    fun emptyAndUnknownValuesFallBackToDefaults() {
        assertEquals(ProxySettings.DISABLED, LegacyProxyMigration.toSettings(emptyPreferences()))
        val s = LegacyProxyMigration.toSettings(mutablePreferencesOf(stringPreferencesKey("proxy_type") to "BOGUS"))
        assertEquals(ProxyType.SOCKS5, s.type)
    }

    @Test
    fun enabledButInvalidIsMigratedDisabled() {
        val invalid = ProxySettings(enabled = true, host = "", port = 0)
        assertFalse(LegacyProxyMigration.normalize(invalid).enabled)
    }

    @Test
    fun validEnabledIsKept() {
        val valid = ProxySettings(enabled = true, host = "10.0.0.1", port = 1080)
        assertEquals(valid, LegacyProxyMigration.normalize(valid))
    }
}

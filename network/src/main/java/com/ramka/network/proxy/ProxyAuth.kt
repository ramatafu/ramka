package com.ramka.network.proxy

import java.util.Base64

/** Заголовок `Proxy-Authorization` для HTTP-прокси (RFC 7617, Basic). Значение секретное: не логировать. */
object ProxyAuth {

    /** `Basic base64(логин:пароль)` в UTF-8. */
    fun basic(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))

    /** Значение заголовка для [settings] или null: не HTTP-прокси либо логин не задан. */
    fun headerFor(settings: ProxySettings): String? =
        if (settings.type == ProxyType.HTTP && settings.hasCredentials) basic(settings.username, settings.password) else null
}

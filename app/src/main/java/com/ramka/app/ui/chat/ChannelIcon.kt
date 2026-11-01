package com.ramka.app.ui.chat

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ramka.app.connection.ColorP2p
import com.ramka.app.connection.ColorRelay
import com.ramka.app.connection.ColorYggdrasil
import com.ramka.domain.model.TransportChannel

/** Значок активного канала в шапке чата: Wi-Fi — локальная сеть, глобус — Yggdrasil, облако — relay. Цвета как у логотипа. */
@Composable
internal fun ChannelBadge(channel: TransportChannel, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val (icon, tint, label) = when (channel) {
        TransportChannel.LOCAL_WIFI -> Triple(Icons.Filled.Wifi, ColorP2p, "Канал: локальная сеть")
        TransportChannel.YGGDRASIL -> Triple(Icons.Filled.Public, ColorYggdrasil, "Канал: Yggdrasil")
        TransportChannel.RELAY -> Triple(Icons.Filled.Cloud, ColorRelay, "Канал: relay")
        TransportChannel.YGGMAIL -> Triple(Icons.Filled.Email, ColorYggmail, "Канал: Yggmail (почта)")
    }
    Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = modifier.size(size))
}

/** Конверт Yggmail: тёплый жёлтый, чтобы отличать от зелёного Wi-Fi, фиолетового Yggdrasil и синего relay. */
private val ColorYggmail = Color(0xFFFFB84D)

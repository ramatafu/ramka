package com.ramka.domain.util

import com.ramka.domain.model.Contact
import com.ramka.domain.model.TransportChannel

/**
 * Какой канал сейчас доступен для контакта — для индикации «активного канала» в чате. Порядок тот же, что у
 * реальной отправки ([com.ramka.data.repository.CompositeTransportRepository]): локальная сеть → Yggdrasil →
 * relay. Сама отправка этим классом не управляется.
 *
 * Проверка Yggdrasil — реальная связность (TCP до порта Ramka у собеседника), а не ICMP-ping: приложениям
 * Android raw-сокеты недоступны, а `InetAddress.isReachable` без прав root проверяет порт 7 и ненадёжен.
 *
 * Yggmail ([yggmailAvailable]) — в самом конце списка; проверка только локальная (включён, настроен, схема шифрования
 * есть, у контакта есть почтовый адрес), без обращения к серверу.
 *
 * @return null, если не доступен ни один канал (relay не настроен, контакт не в локальной сети, без Yggdrasil и Yggmail).
 */
class ChannelSelector(
    private val isLocalNetworkAvailable: suspend (Contact) -> Boolean,
    private val isYggdrasilReachable: suspend (Contact) -> Boolean,
    private val relayAvailable: () -> Boolean,
    private val yggdrasilEnabled: () -> Boolean = { true },
    private val yggmailAvailable: (Contact) -> Boolean = { false }
) {
    suspend fun selectChannel(contact: Contact): TransportChannel? {
        if (isLocalNetworkAvailable(contact)) return TransportChannel.LOCAL_WIFI
        val yggdrasilAddress = contact.yggmailAddress
        if (yggdrasilEnabled() && yggdrasilAddress != null &&
            YggdrasilAddress.isValid(yggdrasilAddress) && isYggdrasilReachable(contact)
        ) {
            return TransportChannel.YGGDRASIL
        }
        if (relayAvailable()) return TransportChannel.RELAY
        // Yggmail — самый последний резерв (store-and-forward): только если прямых каналов и relay нет.
        return if (yggmailAvailable(contact)) TransportChannel.YGGMAIL else null
    }
}

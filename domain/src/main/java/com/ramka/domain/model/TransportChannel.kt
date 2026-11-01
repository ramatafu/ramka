package com.ramka.domain.model

/**
 * Канал, которым Ramka доставляет пакеты контакту. Порядок = приоритет: локальная сеть, Yggdrasil, relay,
 * и только в самом конце — Yggmail (асинхронный store-and-forward через почтовый сервер, необязательный).
 */
enum class TransportChannel { LOCAL_WIFI, YGGDRASIL, RELAY, YGGMAIL }

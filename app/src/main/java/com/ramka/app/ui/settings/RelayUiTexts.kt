package com.ramka.app.ui.settings

import com.ramka.domain.relay.RelayAddressError
import com.ramka.domain.relay.RelayConfigIssue
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayLabelError
import com.ramka.domain.relay.RelayPinError

/** Тексты ошибок и статусов релеёв: общие для экранов списка и редактора. */

internal fun addressErrorText(error: RelayAddressError): String = when (error) {
    RelayAddressError.EMPTY -> "Введите адрес relay"
    RelayAddressError.FORBIDDEN_CHARACTERS -> "Только host или host:port, без https://, пути и пробелов"
    RelayAddressError.BAD_HOST -> "Некорректное имя хоста или IP-адрес"
    RelayAddressError.BAD_PORT -> "Порт должен быть числом от 1 до 65535"
    RelayAddressError.IPV6_NEEDS_BRACKETS -> "IPv6-адрес указывается в квадратных скобках: [2001:db8::1]:48766"
}

internal fun tokenErrorText(issue: RelayConfigIssue): String = when (issue) {
    RelayConfigIssue.NO_TOKEN -> "Введите токен доступа"
    RelayConfigIssue.BAD_TOKEN_LENGTH -> "Токен должен быть длиной от 16 до 64 байт"
    RelayConfigIssue.NO_ADDRESS, RelayConfigIssue.PIN_REQUIRED_FOR_IP -> "Некорректный токен"
}

internal fun pinErrorText(error: RelayPinError): String = when (error) {
    RelayPinError.BAD_FORMAT -> "Пин — ровно 64 hex-символа (допускаются пробелы и двоеточия)"
    RelayPinError.REQUIRED_FOR_IP -> "Для IP-адреса пин обязателен: сертификат проверить иначе нечем"
}

internal fun failureText(failure: RelayFailure): String = when (failure) {
    RelayFailure.UNREACHABLE -> "relay недоступен: проверьте адрес, порт, проброс порта и интернет"
    RelayFailure.TLS -> "ошибка TLS: пин сертификата не совпал или сертификат недействителен"
    RelayFailure.TLS13_UNSUPPORTED -> "это устройство не поддерживает TLS 1.3 (нужен Android 10 или новее)"
    RelayFailure.AUTH -> "relay не принял токен доступа"
    RelayFailure.PROTOCOL -> "неожиданный ответ relay: проверьте версии приложения и сервера"
    RelayFailure.TARGET_OFFLINE -> "собеседник сейчас не подключён к relay"
    RelayFailure.TIMEOUT -> "relay не ответил вовремя"
    RelayFailure.BUSY -> "relay временно ограничил доступ (слишком много запросов или неверных токенов)"
    RelayFailure.REPLACED -> "это устройство вытеснено другим устройством с теми же ключами"
    RelayFailure.INTERNAL -> "внутренняя ошибка relay"
}

/** Новое поле «метка» (в старой форме его не было). */
internal fun labelErrorText(error: RelayLabelError): String = when (error) {
    RelayLabelError.TOO_LONG -> "Метка не длиннее 40 символов"
    RelayLabelError.BAD_CHARACTERS -> "Метка не должна содержать переводов строк и табуляции"
}

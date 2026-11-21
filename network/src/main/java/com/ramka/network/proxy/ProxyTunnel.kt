package com.ramka.network.proxy

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Прокси ответил не так, как требует протокол, или отказал. В тексте нет ни логина, ни пароля, ни адресов. */
class ProxyHandshakeException(message: String) : IOException(message)

/**
 * Ручное рукопожатие с прокси: SOCKS5 (RFC 1928, логин/пароль по RFC 1929) и HTTP CONNECT. Возвращает обычный
 * подключённый [Socket], поверх которого вызывающий делает TLS.
 *
 * Принципы:
 *  - `java.net.Authenticator` не используется: логин и пароль уходят только в этом рукопожатии.
 *  - Адрес назначения НЕ разрешается локально: домен передаётся прокси как есть (SOCKS5 ATYP 0x03, HTTP CONNECT),
 *    поэтому DNS-запрос к релею не покидает устройство. Локально разрешается только адрес самого прокси.
 *  - Один общий срок на подключение к прокси и всё рукопожатие.
 *  - При любой ошибке сокет закрывается; ни пароль, ни ответ прокси в сообщения исключений не попадают.
 */
object ProxyTunnel {

    private const val SOCKS_VERSION = 0x05
    private const val METHOD_NO_AUTH = 0x00
    private const val METHOD_USER_PASS = 0x02
    private const val METHOD_NONE_ACCEPTABLE = 0xFF
    private const val AUTH_VERSION = 0x01
    private const val CMD_CONNECT = 0x01
    private const val ATYP_IPV4 = 0x01
    private const val ATYP_DOMAIN = 0x03
    private const val ATYP_IPV6 = 0x04
    private const val MAX_HTTP_HEADER_BYTES = 16 * 1024

    /**
     * @param settings включённый и верный прокси (см. [ProxyManager.usableSettings])
     * @param targetHost имя, IPv4 или IPv6 (со скобками или без) назначения
     * @param timeoutMillis общий срок на подключение к прокси и рукопожатие
     * @param onSocket получает сокет сразу после создания, ещё до подключения: так вызывающий может оборвать
     *   подключение снаружи (закрыв сокет), не дожидаясь конца рукопожатия
     * @throws ProxyMisconfiguredException настройки неверны
     * @throws ProxyHandshakeException прокси отказал или ответил не по протоколу
     * @throws IOException сеть (в том числе [SocketTimeoutException])
     */
    fun open(
        settings: ProxySettings,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int,
        onSocket: (Socket) -> Unit = {}
    ): Socket {
        settings.validate()?.let { throw ProxyMisconfiguredException(it) }
        if (targetPort !in 1..65535) throw ProxyHandshakeException("Неверный порт назначения")
        val target = classify(ProxySettings.stripBrackets(targetHost.trim()))
        val deadline = System.nanoTime() + timeoutMillis.coerceAtLeast(1) * 1_000_000L

        val socket = Socket() // без аргументов: ничего не подключается и не разрешается до connect
        onSocket(socket)
        try {
            val proxyAddress = InetSocketAddress(ProxySettings.stripBrackets(settings.host.trim()), settings.port)
            if (proxyAddress.isUnresolved) throw ProxyHandshakeException("Не удалось найти прокси по адресу из настроек")
            socket.connect(proxyAddress, remainingMillis(deadline))
            socket.tcpNoDelay = true
            when (settings.type) {
                ProxyType.SOCKS5 -> socks5(socket, settings, target, targetPort, deadline)
                ProxyType.HTTP -> httpConnect(socket, settings, target, targetPort, deadline)
            }
            socket.soTimeout = 0
            return socket
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (_: IOException) {
            }
            throw e
        }
    }

    // ---- SOCKS5 -----------------------------------------------------------------------------

    private fun socks5(socket: Socket, settings: ProxySettings, target: Target, port: Int, deadline: Long) {
        val out = socket.getOutputStream()
        val input = DataInputStream(socket.getInputStream())
        val offerAuth = settings.hasCredentials

        // Приветствие: без логина предлагаем только «без аутентификации», иначе оба способа.
        out.write(if (offerAuth) byteArrayOf(0x05, 0x02, 0x00, 0x02) else byteArrayOf(0x05, 0x01, 0x00))
        out.flush()

        socket.soTimeout = remainingMillis(deadline)
        val version = readUnsigned(input)
        val method = readUnsigned(input)
        if (version != SOCKS_VERSION) throw ProxyHandshakeException("Адрес и порт не похожи на SOCKS5-прокси")
        when (method) {
            METHOD_NO_AUTH -> Unit
            METHOD_USER_PASS -> {
                if (!offerAuth) throw ProxyHandshakeException("Прокси требует логин и пароль")
                socksAuthenticate(socket, out, input, settings, deadline)
            }
            METHOD_NONE_ACCEPTABLE -> throw ProxyHandshakeException("Прокси не принял способы аутентификации")
            else -> throw ProxyHandshakeException("Прокси выбрал неподдерживаемый способ аутентификации")
        }

        // CONNECT: VER CMD RSV ATYP ADDR PORT
        val request = ByteArrayOutputStream()
        request.write(SOCKS_VERSION)
        request.write(CMD_CONNECT)
        request.write(0x00)
        when (target) {
            is Target.V4 -> { request.write(ATYP_IPV4); request.write(target.bytes) }
            is Target.V6 -> { request.write(ATYP_IPV6); request.write(target.bytes) }
            is Target.Domain -> {
                val name = target.name.toByteArray(Charsets.US_ASCII)
                request.write(ATYP_DOMAIN); request.write(name.size); request.write(name)
            }
        }
        request.write(port ushr 8)
        request.write(port and 0xFF)
        out.write(request.toByteArray())
        out.flush()

        socket.soTimeout = remainingMillis(deadline)
        val replyVersion = readUnsigned(input)
        val reply = readUnsigned(input)
        readUnsigned(input) // RSV
        val atyp = readUnsigned(input)
        if (replyVersion != SOCKS_VERSION) throw ProxyHandshakeException("Некорректный ответ SOCKS5-прокси")
        if (reply != 0x00) throw ProxyHandshakeException(socksReplyText(reply))
        // Привязанный адрес нам не нужен, но его надо прочитать целиком: дальше в потоке уже данные релея.
        val skip = when (atyp) {
            ATYP_IPV4 -> 4 + 2
            ATYP_IPV6 -> 16 + 2
            ATYP_DOMAIN -> readUnsigned(input) + 2
            else -> throw ProxyHandshakeException("Некорректный ответ SOCKS5-прокси")
        }
        try {
            input.readFully(ByteArray(skip))
        } catch (e: java.io.EOFException) {
            throw ProxyHandshakeException("Прокси закрыл соединение")
        }
    }

    private fun socksAuthenticate(socket: Socket, out: OutputStream, input: DataInputStream, settings: ProxySettings, deadline: Long) {
        val user = settings.username.toByteArray(Charsets.UTF_8)
        val pass = settings.password.toByteArray(Charsets.UTF_8)
        try {
            if (user.isEmpty() || user.size > 255 || pass.size > 255) {
                throw ProxyHandshakeException("Логин и пароль не длиннее 255 байт")
            }
            val message = ByteArray(3 + user.size + pass.size)
            message[0] = AUTH_VERSION.toByte()
            message[1] = user.size.toByte()
            user.copyInto(message, 2)
            message[2 + user.size] = pass.size.toByte()
            pass.copyInto(message, 3 + user.size)
            try {
                out.write(message)
                out.flush()
            } finally {
                message.fill(0)
            }
        } finally {
            user.fill(0)
            pass.fill(0)
        }
        socket.soTimeout = remainingMillis(deadline)
        val version = readUnsigned(input)
        val status = readUnsigned(input)
        if (version != AUTH_VERSION) throw ProxyHandshakeException("Некорректный ответ прокси на логин и пароль")
        if (status != 0x00) throw ProxyHandshakeException("Прокси отклонил логин или пароль")
    }

    private fun socksReplyText(code: Int): String = when (code) {
        0x01 -> "Сбой SOCKS5-прокси"
        0x02 -> "Прокси запретил это соединение"
        0x03 -> "Прокси: сеть назначения недоступна"
        0x04 -> "Прокси: узел назначения недоступен"
        0x05 -> "Прокси: соединение с узлом назначения отклонено"
        0x06 -> "Прокси: истекло время жизни пакета (TTL)"
        0x07 -> "Прокси не поддерживает команду CONNECT"
        0x08 -> "Прокси не поддерживает этот тип адреса"
        else -> "Прокси отказал в соединении (код $code)"
    }

    // ---- HTTP CONNECT -----------------------------------------------------------------------

    private fun httpConnect(socket: Socket, settings: ProxySettings, target: Target, port: Int, deadline: Long) {
        val host = when (target) {
            is Target.Domain -> target.name
            is Target.V4 -> target.text
            is Target.V6 -> "[" + target.text + "]"
        }
        val authority = "$host:$port"
        val request = StringBuilder()
            .append("CONNECT ").append(authority).append(" HTTP/1.1\r\n")
            .append("Host: ").append(authority).append("\r\n")
        ProxyAuth.headerFor(settings)?.let { request.append("Proxy-Authorization: ").append(it).append("\r\n") }
        request.append("\r\n")

        val out = socket.getOutputStream()
        val bytes = request.toString().toByteArray(Charsets.US_ASCII)
        request.setLength(0)
        out.write(bytes)
        out.flush()
        bytes.fill(0) // в запросе был заголовок с паролем

        val head = readHttpHead(socket, socket.getInputStream(), deadline)
        val statusLine = head.substringBefore("\r\n")
        val parts = statusLine.split(' ', limit = 3)
        val code = parts.getOrNull(1)?.toIntOrNull()
        if (parts.size < 2 || !parts[0].startsWith("HTTP/1.") || code == null) {
            throw ProxyHandshakeException("Адрес и порт не похожи на HTTP-прокси")
        }
        when {
            code in 200..299 -> Unit
            code == 407 -> throw ProxyHandshakeException("Прокси требует верные логин и пароль (407)")
            else -> throw ProxyHandshakeException("Прокси отказал в соединении (код $code)")
        }
    }

    /** Читает заголовки ответа по одному байту: лишнего из потока не берём, дальше идут данные релея. */
    private fun readHttpHead(socket: Socket, input: InputStream, deadline: Long): String {
        val buffer = ByteArrayOutputStream()
        var matched = 0 // сколько байт «\r\n\r\n» уже сошлось подряд
        val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (matched < end.size) {
            socket.soTimeout = remainingMillis(deadline)
            val b = input.read()
            if (b < 0) throw ProxyHandshakeException("Прокси закрыл соединение")
            buffer.write(b)
            if (buffer.size() > MAX_HTTP_HEADER_BYTES) throw ProxyHandshakeException("Слишком длинный ответ прокси")
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
        }
        return buffer.toString(Charsets.ISO_8859_1.name())
    }

    // ---- Адрес назначения -------------------------------------------------------------------

    internal sealed interface Target {
        class V4(val bytes: ByteArray, val text: String) : Target
        class V6(val bytes: ByteArray, val text: String) : Target
        class Domain(val name: String) : Target
    }

    /** IPv4/IPv6-литерал или домен. Никакого DNS: литералы разбираются вручную. */
    internal fun classify(host: String): Target {
        if (host.isEmpty()) throw ProxyHandshakeException("Не задан адрес назначения")
        parseIpv4(host)?.let { return Target.V4(it, host) }
        if (host.contains(':')) {
            if (!host.all { it in "0123456789abcdefABCDEF:." }) throw ProxyHandshakeException("Неверный IPv6-адрес назначения")
            val address = try {
                InetAddress.getByName(host) // для литерала DNS не используется
            } catch (e: UnknownHostException) {
                throw ProxyHandshakeException("Неверный IPv6-адрес назначения")
            }
            return if (address.address.size == 4) Target.V4(address.address, address.hostAddress) else Target.V6(address.address, host)
        }
        if (host.any { it.isWhitespace() || it.isISOControl() }) throw ProxyHandshakeException("Неверное имя узла назначения")
        val ascii = try {
            IDN.toASCII(host)
        } catch (e: IllegalArgumentException) {
            throw ProxyHandshakeException("Неверное имя узла назначения")
        }
        if (ascii.isEmpty() || ascii.length > 255) throw ProxyHandshakeException("Неверное имя узла назначения")
        return Target.Domain(ascii)
    }

    private fun parseIpv4(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val result = ByteArray(4)
        for ((i, p) in parts.withIndex()) {
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            val v = p.toInt()
            if (v > 255) return null
            result[i] = v.toByte()
        }
        return result
    }

    // ---- Служебное --------------------------------------------------------------------------

    private fun readUnsigned(input: DataInputStream): Int = try {
        input.readUnsignedByte()
    } catch (e: java.io.EOFException) {
        throw ProxyHandshakeException("Прокси закрыл соединение")
    }

    private fun remainingMillis(deadline: Long): Int {
        val ms = (deadline - System.nanoTime()) / 1_000_000L
        if (ms <= 0) throw SocketTimeoutException("Прокси не ответил вовремя")
        return ms.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

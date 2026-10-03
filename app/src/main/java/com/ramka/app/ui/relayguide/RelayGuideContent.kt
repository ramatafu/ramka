package com.ramka.app.ui.relayguide

import com.ramka.domain.relay.RelayAddress

/** Блок содержимого шага: пояснение, команда для копирования или предупреждение. */
sealed interface GuideBlock {
    data class Paragraph(val text: String) : GuideBlock

    /** Одна или несколько команд, которые вставляются в терминал целиком (кнопка «Копировать»). */
    data class Command(val text: String) : GuideBlock

    data class Note(val text: String) : GuideBlock
}

data class GuideStep(val title: String, val blocks: List<GuideBlock>)

/**
 * Текст инструкции «Как настроить relay-сервер». Это статические данные без Android-зависимостей:
 * экран ([RelayGuideScreen]) только рисует их, а тест проверяет согласованность (порт, заглушки).
 *
 * Команды соответствуют DEPLOY.md и RELAY_PROTOCOL.md (сервер ramka-relay, порт по умолчанию 48766).
 */
object RelayGuideContent {

    /** Репозиторий проекта. Каталог [RELAY_DIR] с исходниками сервера должен лежать в нём (см. шаг 3). */
    const val REPO_URL = "https://github.com/ramatafu/ramka.git"
    const val REPO_DIR = "ramka"
    const val RELAY_DIR = "relay"

    val PORT: Int = RelayAddress.DEFAULT_PORT

    /** Заглушки в командах и тексте: пользователь заменяет их своими значениями. */
    const val PLACEHOLDER_IP = "IP_СЕРВЕРА"
    const val PLACEHOLDER_USER = "ВАШ_ПОЛЬЗОВАТЕЛЬ"

    val intro: String =
        "Relay — это ваш собственный маленький сервер, через который сообщения доходят до собеседника, " +
            "когда вы не в одной сети. Он не может прочитать сообщения: они шифруются на устройствах, " +
            "сервер пересылает только непрозрачные байты. Настройка занимает 15–20 минут. " +
            "Понадобятся компьютер с терминалом и немного терпения."

    val steps: List<GuideStep> = listOf(
        GuideStep(
            title = "Арендуйте VPS с белым IP-адресом",
            blocks = listOf(
                GuideBlock.Paragraph(
                    "Подойдёт любой хостинг, где можно арендовать виртуальный сервер (VPS) с публичным " +
                        "(«белым») IPv4-адресом. Самой слабой конфигурации достаточно: 1 vCPU и 512 МБ–1 ГБ памяти."
                ),
                GuideBlock.Paragraph(
                    "Операционная система: Ubuntu 24.04 LTS. На более старых версиях в системе слишком " +
                        "старая версия Go, и сервер не соберётся."
                ),
                GuideBlock.Paragraph(
                    "После создания сервера запишите его IP-адрес, имя пользователя и пароль (или SSH-ключ) " +
                        "из письма или панели хостинга."
                ),
                GuideBlock.Note(
                    "Если в панели хостинга есть собственный файрвол, разрешите входящие TCP-соединения на " +
                        "порт $PORT. Вместо VPS можно использовать домашний сервер со статическим IP: " +
                        "тогда на роутере нужно пробросить TCP-порт $PORT на этот сервер."
                )
            )
        ),
        GuideStep(
            title = "Подключитесь к серверу по SSH",
            blocks = listOf(
                GuideBlock.Paragraph(
                    "Откройте терминал: на Windows 10/11 — PowerShell, на macOS и Linux — Терминал. " +
                        "Замените $PLACEHOLDER_USER и $PLACEHOLDER_IP значениями от хостинга " +
                        "(часто пользователь — root) и введите пароль, когда его спросят:"
                ),
                GuideBlock.Command("ssh $PLACEHOLDER_USER@$PLACEHOLDER_IP"),
                GuideBlock.Paragraph("Затем обновите систему и включите файрвол. Порядок важен: сначала разрешаем SSH, потом включаем файрвол."),
                GuideBlock.Command(
                    "sudo apt update && sudo apt upgrade -y\n" +
                        "sudo ufw allow OpenSSH\n" +
                        "sudo ufw allow $PORT/tcp\n" +
                        "sudo ufw enable"
                ),
                GuideBlock.Note("Если вы вошли как root, слово sudo можно не писать, но с ним тоже всё работает.")
            )
        ),
        GuideStep(
            title = "Скачайте и запустите ramka-relay",
            blocks = listOf(
                GuideBlock.Paragraph("Установите нужные программы, скачайте проект и соберите сервер:"),
                GuideBlock.Command(
                    "sudo apt install -y golang-go make git\n" +
                        "git clone $REPO_URL\n" +
                        "cd $REPO_DIR/$RELAY_DIR\n" +
                        "make build"
                ),
                GuideBlock.Note(
                    "Сборка создаёт файл ramka-relay в каталоге $RELAY_DIR. Если команда cd пишет, что " +
                        "каталога нет, значит исходники сервера в репозитории ещё не опубликованы: " +
                        "загрузите папку $RELAY_DIR на сервер вручную (например, командой scp -r) и " +
                        "выполните make build внутри неё."
                ),
                GuideBlock.Paragraph("Создайте токен доступа. Он нужен приложению, чтобы сервер пускал только вас:"),
                GuideBlock.Command("./ramka-relay gentoken"),
                GuideBlock.Note(
                    "Команда напечатает две строки. Первая — token: скопируйте и сохраните, он " +
                        "показывается один раз и нужен в приложении. Вторая — tokens_sha256: он нужен " +
                        "для настройки сервера ниже. Никому не показывайте токен."
                ),
                GuideBlock.Paragraph("Установите сервер и создайте для него отдельного пользователя и каталоги:"),
                GuideBlock.Command(
                    "sudo install -m 0755 ramka-relay /usr/local/bin/ramka-relay\n" +
                        "sudo useradd --system --no-create-home --shell /usr/sbin/nologin ramka\n" +
                        "sudo install -d -o root -g ramka -m 0750 /etc/ramka-relay\n" +
                        "sudo install -d -o ramka -g ramka -m 0700 /var/lib/ramka-relay"
                ),
                GuideBlock.Paragraph("Создайте файл настроек и вставьте в него значение tokens_sha256:"),
                GuideBlock.Command(
                    "sudo cp config.example.json /etc/ramka-relay/config.json\n" +
                        "sudo nano /etc/ramka-relay/config.json"
                ),
                GuideBlock.Note(
                    "В редакторе nano замените строку-заглушку внутри кавычек на значение tokens_sha256 " +
                        "(64 символа). Сохраните: Ctrl+O, затем Enter, выйдите: Ctrl+X."
                ),
                GuideBlock.Command(
                    "sudo chown root:ramka /etc/ramka-relay/config.json\n" +
                        "sudo chmod 0640 /etc/ramka-relay/config.json"
                )
            )
        ),
        GuideStep(
            title = "Настройте автозапуск через systemd",
            blocks = listOf(
                GuideBlock.Paragraph(
                    "Файл службы лежит рядом с исходниками. Подключите его и запустите сервер, чтобы он " +
                        "стартовал сам при каждой загрузке и перезапускался после сбоев:"
                ),
                GuideBlock.Command(
                    "sudo cp ramka-relay.service /etc/systemd/system/\n" +
                        "sudo systemctl daemon-reload\n" +
                        "sudo systemctl enable --now ramka-relay"
                ),
                GuideBlock.Paragraph("Проверьте, что служба работает:"),
                GuideBlock.Command("systemctl status ramka-relay --no-pager"),
                GuideBlock.Note(
                    "В статусе должно быть active (running). Если там failed, посмотрите причину: " +
                        "journalctl -u ramka-relay -n 30 --no-pager"
                )
            )
        ),
        GuideStep(
            title = "Возьмите адрес, токен и пин и введите их в приложении",
            blocks = listOf(
                GuideBlock.Paragraph("Сервер при первом запуске создал сертификат. Его пин (отпечаток) напечатан в журнале:"),
                GuideBlock.Command("journalctl -u ramka-relay --no-pager | grep \"TLS pin\""),
                GuideBlock.Paragraph(
                    "В строке после «TLS pin (SPKI SHA-256):» будет 64 символа из цифр и букв a–f. Это и есть пин."
                ),
                GuideBlock.Paragraph("Вернитесь в Настройки приложения, секция «Домашний relay», и заполните поля:"),
                GuideBlock.Paragraph("• Адрес relay: $PLACEHOLDER_IP:$PORT (например, 203.0.113.7:$PORT)."),
                GuideBlock.Paragraph("• Токен доступа: значение token из шага 3."),
                GuideBlock.Paragraph("• Пин сертификата: 64 символа из журнала."),
                GuideBlock.Paragraph(
                    "Включите ползунок «Домашний relay», нажмите «Сохранить», затем «Проверить подключение». " +
                        "Должно появиться «Подключение работает»."
                ),
                GuideBlock.Note(
                    "Если проверка не прошла: «relay недоступен» — проверьте адрес, порт и файрвол " +
                        "(шаги 1–2); «ошибка TLS» — пин скопирован с ошибкой; «relay не принял токен» — " +
                        "токен введён неверно. Адрес, токен и пин должны быть одинаковыми на устройствах " +
                        "всех собеседников."
                )
            )
        )
    )

    /** Все команды шага (для тестов и копирования). */
    fun commandsOf(step: GuideStep): List<String> = step.blocks.filterIsInstance<GuideBlock.Command>().map { it.text }
}

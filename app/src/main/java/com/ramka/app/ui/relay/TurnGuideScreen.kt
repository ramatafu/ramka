package com.ramka.app.ui.relay

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ramka.app.ui.relayguide.GuideBlock
import com.ramka.app.ui.relayguide.GuideStep
import kotlinx.coroutines.delay

/**
 * Экран «Свой TURN-сервер (Coturn)»: пошаговая инструкция (7 шагов), чтобы видеозвонки работали
 * через мобильный интернет. Без ViewModel и Hilt: содержимое статическое ([TurnGuideContent]),
 * единственное состояние — «скопировано» у кнопок команд. Навигация — только колбэк [onBack].
 *
 * Не путать с [com.ramka.app.ui.relayguide.RelayGuideScreen] — там инструкция по relay-серверу
 * сообщений (ramka-relay); модели [GuideStep]/[GuideBlock] используются общие.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnGuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Свой TURN-сервер (Coturn)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(TurnGuideContent.intro, style = MaterialTheme.typography.bodyLarge)
            TurnGuideContent.steps.forEachIndexed { index, step ->
                TurnStepCard(number = index + 1, step = step)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TurnStepCard(number: Int, step: GuideStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            number.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(step.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            step.blocks.forEach { block ->
                when (block) {
                    is GuideBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
                    is GuideBlock.Command -> TurnCodeBlock(block.text)
                    is GuideBlock.Note -> Text(
                        block.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Код/команды моноширинным шрифтом (можно выделить) и кнопка «Копировать» в буфер обмена. */
@Composable
private fun TurnCodeBlock(code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            SelectionContainer {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }) { Text(if (copied) "Скопировано" else "Копировать") }
            }
        }
    }
}

/** Текст инструкции по Coturn: статические данные без Android-зависимостей. */
internal object TurnGuideContent {

    const val PLACEHOLDER_IP = "YOUR_SERVER_IP"
    const val PLACEHOLDER_LOGIN = "ВАШ_ЛОГИН"
    const val PLACEHOLDER_PASSWORD = "ВАШ_ПАРОЛЬ"
    const val TURN_PORT = 3478
    const val MIN_PORT = 49152
    const val MAX_PORT = 65535

    val intro: String =
        "Видеозвонок Ramka идёт напрямую между телефонами. У мобильных операторов обычно «симметричный» " +
            "NAT: прямое соединение не пробивается, и звонок с мобильного интернета не соединяется. " +
            "Выход — собственный TURN-сервер: он ретранслирует уже зашифрованный медиапоток (WebRTC шифрует " +
            "его на устройствах, сервер не может ни услышать, ни увидеть разговор). " +
            "Настройка занимает 10–15 минут."

    private val serverConfig: String = """
        listening-port=$TURN_PORT
        listening-ip=0.0.0.0
        external-ip=$PLACEHOLDER_IP
        min-port=$MIN_PORT
        max-port=$MAX_PORT
        fingerprint
        lt-cred-mech
        realm=ramka
        user=$PLACEHOLDER_LOGIN:$PLACEHOLDER_PASSWORD
        no-multicast-peers
    """.trimIndent()

    private val appFields: String = """
        Адрес сервера: turn:$PLACEHOLDER_IP:$TURN_PORT
        Логин: $PLACEHOLDER_LOGIN
        Пароль: $PLACEHOLDER_PASSWORD
    """.trimIndent()

    val steps: List<GuideStep> = listOf(
        GuideStep(
            title = "Зачем нужен TURN-сервер",
            blocks = listOf(
                GuideBlock.Paragraph(
                    "В одной Wi-Fi сети звонки работают без сервера. Когда один из собеседников на мобильном " +
                        "интернете, операторский NAT не даёт телефонам найти друг друга. Тогда трафик " +
                        "идёт через TURN-сервер, и звонок соединяется."
                ),
                GuideBlock.Note(
                    "STUN-сервер (он уже есть в приложении) только подсказывает телефону его публичный адрес " +
                        "и симметричный NAT не пробивает. Для этого и нужен TURN."
                )
            )
        ),
        GuideStep(
            title = "Что понадобится",
            blocks = listOf(
                GuideBlock.Paragraph("VPS (виртуальный сервер) с публичным («белым») IPv4-адресом."),
                GuideBlock.Paragraph("Операционная система: Ubuntu 20.04 или 22.04. Достаточно 1 vCPU и 512 МБ–1 ГБ памяти."),
                GuideBlock.Paragraph("Доступ по SSH с правами sudo и компьютер с терминалом."),
                GuideBlock.Note("Запишите публичный IP сервера: он понадобится в шагах 4 и 7.")
            )
        ),
        GuideStep(
            title = "Установите Coturn",
            blocks = listOf(
                GuideBlock.Paragraph("Подключитесь к серверу по SSH и выполните:"),
                GuideBlock.Command("sudo apt update && sudo apt install -y coturn"),
                GuideBlock.Paragraph("На Ubuntu 20.04 службу нужно дополнительно включить в системных настройках:"),
                GuideBlock.Command("echo 'TURNSERVER_ENABLED=1' | sudo tee -a /etc/default/coturn"),
                GuideBlock.Note("На 22.04 эта команда безвредна, её можно выполнить в любом случае.")
            )
        ),
        GuideStep(
            title = "Настройте /etc/turnserver.conf",
            blocks = listOf(
                GuideBlock.Paragraph("Сначала сохраните старый конфиг и очистите файл — удалить нужно всё старое:"),
                GuideBlock.Command(
                    "sudo cp /etc/turnserver.conf /etc/turnserver.conf.bak && " +
                        "sudo truncate -s 0 /etc/turnserver.conf"
                ),
                GuideBlock.Paragraph("Откройте файл в редакторе:"),
                GuideBlock.Command("sudo nano /etc/turnserver.conf"),
                GuideBlock.Paragraph("Вставьте базовый конфиг:"),
                GuideBlock.Command(serverConfig),
                GuideBlock.Paragraph(
                    "Замените $PLACEHOLDER_IP на публичный IP вашего сервера, а $PLACEHOLDER_LOGIN и " +
                        "$PLACEHOLDER_PASSWORD — на придуманные вами логин и пароль. " +
                        "Сохраните файл: Ctrl+O, Enter, затем Ctrl+X."
                ),
                GuideBlock.Note(
                    "Пароль придумайте длинный и случайный: любой, кто знает адрес, логин и пароль, " +
                        "сможет гонять через ваш сервер свой трафик. " +
                        "Если у VPS публичный адрес не на самом интерфейсе (бывает в облаках с внутренней " +
                        "сетью), в external-ip указывается пара «публичный/внутренний»: 1.2.3.4/10.0.0.5."
                )
            )
        ),
        GuideStep(
            title = "Откройте порты",
            blocks = listOf(
                GuideBlock.Paragraph("Если на сервере используется ufw (по умолчанию в Ubuntu):"),
                GuideBlock.Command(
                    "sudo ufw allow $TURN_PORT/udp\n" +
                        "sudo ufw allow $TURN_PORT/tcp\n" +
                        "sudo ufw allow $MIN_PORT:$MAX_PORT/udp"
                ),
                GuideBlock.Paragraph("Если вы управляете файрволом через iptables:"),
                GuideBlock.Command(
                    "sudo iptables -A INPUT -p udp --dport $TURN_PORT -j ACCEPT\n" +
                        "sudo iptables -A INPUT -p tcp --dport $TURN_PORT -j ACCEPT\n" +
                        "sudo iptables -A INPUT -p udp --dport $MIN_PORT:$MAX_PORT -j ACCEPT"
                ),
                GuideBlock.Paragraph("Чтобы правила iptables пережили перезагрузку:"),
                GuideBlock.Command("sudo apt install -y iptables-persistent && sudo netfilter-persistent save"),
                GuideBlock.Note(
                    "Нужен один из двух способов, не оба сразу. Если ufw ещё выключен, перед " +
                        "sudo ufw enable обязательно разрешите SSH (sudo ufw allow OpenSSH), иначе потеряете доступ " +
                        "к серверу. Если в панели хостинга есть свой файрвол, откройте те же порты и там."
                )
            )
        ),
        GuideStep(
            title = "Запустите и проверьте",
            blocks = listOf(
                GuideBlock.Paragraph("Перезапустите службу и включите автозапуск:"),
                GuideBlock.Command("sudo systemctl restart coturn && sudo systemctl enable coturn"),
                GuideBlock.Paragraph("Статус должен быть active (running):"),
                GuideBlock.Command("sudo systemctl status coturn"),
                GuideBlock.Paragraph("Порт $TURN_PORT должен слушаться (в выводе строки с :$TURN_PORT для udp и tcp):"),
                GuideBlock.Command("sudo ss -tulpn | grep $TURN_PORT"),
                GuideBlock.Note("Если службы не видно, посмотрите журнал: sudo journalctl -u coturn -n 50")
            )
        ),
        GuideStep(
            title = "Укажите сервер в приложении",
            blocks = listOf(
                GuideBlock.Paragraph(
                    "Откройте Настройки → «Настройки TURN-сервера» и заполните поля:"
                ),
                GuideBlock.Command(appFields),
                GuideBlock.Paragraph(
                    "IP — адрес вашего сервера, порт $TURN_PORT, логин и пароль — те, что указаны в строке user= " +
                        "конфига. Включите переключатель «Использовать TURN-сервер» и нажмите «Сохранить». " +
                        "Новые настройки применятся при следующем звонке, пересобирать приложение не нужно."
                ),
                GuideBlock.Note(
                    "Проверка: позвоните, когда один телефон на Wi-Fi, а другой на мобильном интернете. " +
                        "Надёжнее ввести настройки на обоих телефонах. Приложение само добавляет и UDP-, и " +
                        "резервный TCP-вариант адреса для операторов, блокирующих UDP."
                )
            )
        )
    )
}

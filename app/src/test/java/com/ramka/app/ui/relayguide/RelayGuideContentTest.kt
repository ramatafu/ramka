package com.ramka.app.ui.relayguide

import com.ramka.domain.relay.RelayAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayGuideContentTest {

    private val steps = RelayGuideContent.steps

    private fun GuideStep.allText(): String = blocks.joinToString("\n") {
        when (it) {
            is GuideBlock.Paragraph -> it.text
            is GuideBlock.Command -> it.text
            is GuideBlock.Note -> it.text
        }
    }

    @Test
    fun `пять шагов в заданном порядке`() {
        assertEquals(5, steps.size)
        assertTrue(steps[0].title.contains("VPS"))
        assertTrue(steps[1].title.contains("SSH"))
        assertTrue(steps[2].title.contains("ramka-relay"))
        assertTrue(steps[3].title.contains("systemd"))
        assertTrue(steps[4].title.contains("пин", ignoreCase = true))
    }

    @Test
    fun `в каждом шаге есть содержимое, а команды не пустые`() {
        for (step in steps) {
            assertTrue(step.title, step.blocks.isNotEmpty())
            for (b in step.blocks) {
                val text = when (b) {
                    is GuideBlock.Paragraph -> b.text
                    is GuideBlock.Command -> b.text
                    is GuideBlock.Note -> b.text
                }
                assertTrue("пустой блок в «${step.title}»", text.isNotBlank())
            }
        }
        assertTrue(RelayGuideContent.intro.isNotBlank())
    }

    @Test
    fun `порт в инструкции совпадает с портом по умолчанию клиента и сервера`() {
        assertEquals(RelayAddress.DEFAULT_PORT, RelayGuideContent.PORT)
        assertEquals(48766, RelayGuideContent.PORT)
        assertTrue(RelayGuideContent.commandsOf(steps[1]).any { it.contains("ufw allow ${RelayGuideContent.PORT}/tcp") })
        assertTrue(steps[4].allText().contains("${RelayGuideContent.PLACEHOLDER_IP}:${RelayGuideContent.PORT}"))
    }

    @Test
    fun `порядок команд файрвола не оставляет без SSH`() {
        val firewall = RelayGuideContent.commandsOf(steps[1]).first { it.contains("ufw") }
        assertTrue(firewall.indexOf("ufw allow OpenSSH") in 0 until firewall.indexOf("ufw enable"))
    }

    @Test
    fun `каждая заглушка объясняется в тексте того же шага`() {
        for (step in steps) {
            val commands = RelayGuideContent.commandsOf(step).joinToString("\n")
            for (placeholder in listOf(RelayGuideContent.PLACEHOLDER_IP, RelayGuideContent.PLACEHOLDER_USER)) {
                if (commands.contains(placeholder)) {
                    val prose = step.blocks.filter { it !is GuideBlock.Command }.joinToString("\n") {
                        (it as? GuideBlock.Paragraph)?.text ?: (it as GuideBlock.Note).text
                    }
                    assertTrue("заглушка $placeholder не объяснена в «${step.title}»", prose.contains(placeholder))
                }
            }
        }
    }

    @Test
    fun `шаг сборки использует адрес репозитория и каталог сервера`() {
        val build = RelayGuideContent.commandsOf(steps[2]).joinToString("\n")
        assertTrue(build.contains("git clone ${RelayGuideContent.REPO_URL}"))
        assertTrue(build.contains("cd ${RelayGuideContent.REPO_DIR}/${RelayGuideContent.RELAY_DIR}"))
        assertTrue(build.contains("make build"))
        assertTrue(build.contains("./ramka-relay gentoken"))
    }

    @Test
    fun `команды совпадают с DEPLOY - служба, пользователь, каталоги, пин`() {
        val all = steps.flatMap { RelayGuideContent.commandsOf(it) }.joinToString("\n")
        for (expected in listOf(
            "useradd --system --no-create-home --shell /usr/sbin/nologin ramka",
            "install -d -o root -g ramka -m 0750 /etc/ramka-relay",
            "install -d -o ramka -g ramka -m 0700 /var/lib/ramka-relay",
            "chmod 0640 /etc/ramka-relay/config.json",
            "systemctl enable --now ramka-relay",
            "journalctl -u ramka-relay --no-pager | grep \"TLS pin\""
        )) {
            assertTrue("нет команды: $expected", all.contains(expected))
        }
    }

    @Test
    fun `в командах нет кириллицы кроме заглушек`() {
        for (step in steps) {
            for (cmd in RelayGuideContent.commandsOf(step)) {
                val cleaned = cmd.replace(RelayGuideContent.PLACEHOLDER_IP, "").replace(RelayGuideContent.PLACEHOLDER_USER, "")
                assertFalse("кириллица в команде: $cmd", cleaned.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' })
            }
        }
    }
}

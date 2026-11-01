package com.ramka.app.yggmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailHostTest {
    @Test fun ipv6LiteralGetsBrackets() {
        assertEquals("[200:db8::1]", MailHost.formatIPv6ForJavaMail("200:db8::1"))
        assertEquals("[324:71e:281a:9ed3::53]", MailHost.formatIPv6ForJavaMail("  324:71e:281a:9ed3::53 "))
        assertEquals("[200:1:2:3:4:5:6:7]", MailHost.formatIPv6ForJavaMail("200:1:2:3:4:5:6:7"))
    }

    @Test fun alreadyBracketedIpv6IsUnchanged() {
        assertEquals("[200:db8::1]", MailHost.formatIPv6ForJavaMail("[200:db8::1]"))
    }

    @Test fun hostnamesAndIpv4AreUnchanged() {
        assertEquals("mail.example.org", MailHost.formatIPv6ForJavaMail("mail.example.org"))
        assertEquals("192.168.1.5", MailHost.formatIPv6ForJavaMail("192.168.1.5"))
        assertEquals("localhost", MailHost.formatIPv6ForJavaMail("localhost"))
    }

    @Test fun portLikeSuffixIsNotTreatedAsIpv6() {
        assertEquals("host:1025", MailHost.formatIPv6ForJavaMail("host:1025"))
    }

    @Test fun stripBracketsRoundTrip() {
        assertEquals("200:db8::1", MailHost.stripBrackets("[200:db8::1]"))
        assertEquals("200:db8::1", MailHost.stripBrackets("200:db8::1"))
        assertEquals("[]", MailHost.stripBrackets("[]"))
    }

    @Test fun validHosts() {
        assertTrue(MailHost.isValid("mail.example.org"))
        assertTrue(MailHost.isValid("192.168.1.5"))
        assertTrue(MailHost.isValid("200:db8::1"))
        assertTrue(MailHost.isValid("[200:db8::1]"))
        assertTrue(MailHost.isValid("localhost"))
    }

    @Test fun invalidHosts() {
        assertFalse(MailHost.isValid(""))
        assertFalse(MailHost.isValid("   "))
        assertFalse(MailHost.isValid("bad host"))
        assertFalse(MailHost.isValid("host_name"))
        assertFalse(MailHost.isValid("-bad.example.org"))
        assertFalse(MailHost.isValid("[200:db8::1"))
        assertFalse(MailHost.isValid("200:db8::1%eth0"))
    }

    @Test fun securityDependsOnPort() {
        assertEquals(MailSecurity.SSL, MailSecurityPolicy.forSmtp(465))
        assertEquals(MailSecurity.STARTTLS, MailSecurityPolicy.forSmtp(587))
        assertEquals(MailSecurity.NONE, MailSecurityPolicy.forSmtp(1025))
        assertEquals(MailSecurity.SSL, MailSecurityPolicy.forImap(993))
        assertEquals(MailSecurity.STARTTLS, MailSecurityPolicy.forImap(143))
        assertEquals(MailSecurity.NONE, MailSecurityPolicy.forImap(1143))
    }
}

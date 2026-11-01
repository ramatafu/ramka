package com.ramka.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YggmailMailboxTest {
    private val key = "a".repeat(64)

    @Test fun parsesYggmailMailbox() {
        assertEquals("$key@yggmail", YggmailMailbox.parse("$key@yggmail"))
        assertEquals("$key@yggmail", YggmailMailbox.parse("  ${key.uppercase()}@YggMail  "))
        assertEquals("user.name-1@yggmail", YggmailMailbox.parse("user.name-1@yggmail"))
    }

    @Test fun otherDomainsAreRejected() {
        assertNull(YggmailMailbox.parse("user@example.com"))
        assertNull(YggmailMailbox.parse("user@gmail.com"))
        assertNull(YggmailMailbox.parse("user@yggmail.evil.com"))
    }

    @Test fun malformedAddressesAreRejected() {
        assertNull(YggmailMailbox.parse(""))
        assertNull(YggmailMailbox.parse("@yggmail"))
        assertNull(YggmailMailbox.parse("user@"))
        assertNull(YggmailMailbox.parse("user"))
        assertNull(YggmailMailbox.parse("a@b@yggmail"))
        assertNull(YggmailMailbox.parse("us er@yggmail"))
        assertNull(YggmailMailbox.parse("user@[200::1]"))
        assertNull(YggmailMailbox.parse("${"a".repeat(129)}@yggmail"))
    }

    @Test fun contactFieldAcceptsIpv6OrMailbox() {
        assertTrue(ContactNetworkAddress.isValid("user@[324:71e::53]"))
        assertTrue(ContactNetworkAddress.isValid("324:71e::53"))
        assertTrue(ContactNetworkAddress.isValid("$key@yggmail"))
        assertFalse(ContactNetworkAddress.isValid("user@example.com"))
        assertFalse(ContactNetworkAddress.isValid("2001:db8::1"))
        assertFalse(ContactNetworkAddress.isValid("example.com"))
    }

    @Test fun mailboxIsNotAnIpv6Address() {
        assertFalse(YggdrasilAddress.isValid("$key@yggmail"))
        assertFalse(YggmailMailbox.isValid("user@[324:71e::53]"))
    }
}

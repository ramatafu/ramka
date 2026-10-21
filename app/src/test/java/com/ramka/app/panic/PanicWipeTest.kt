package com.ramka.app.panic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class PanicWipeTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `deleteChildren removes nested content but keeps root and kept names`() {
        val root = tmp.newFolder("data")
        File(root, "files/datastore").mkdirs()
        File(root, "files/datastore/a.preferences_pb").writeText("x")
        File(root, "databases").mkdirs()
        File(root, "databases/ramka.db").writeText("x")
        File(root, ".sync").writeText("m")
        File(root, "lib").mkdirs()

        PanicWipe.deleteChildren(root, setOf(".sync", "lib"))

        assertTrue(root.isDirectory)
        assertEquals(setOf(".sync", "lib"), root.list()!!.toSet())
    }

    @Test
    fun `deleteChildren does not follow symlinks`() {
        val outside = tmp.newFolder("outside")
        val precious = File(outside, "keep.txt").apply { writeText("keep") }
        val root = tmp.newFolder("data")
        try {
            Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        } catch (e: Exception) {
            assumeTrue("symlinks unavailable", false)
        }

        PanicWipe.deleteChildren(root, emptySet())

        assertTrue(precious.exists())
        assertFalse(File(root, "link").exists())
    }

    @Test
    fun `deleteChildren on missing dir does not throw`() {
        PanicWipe.deleteChildren(File(tmp.root, "nope"), emptySet())
    }
}

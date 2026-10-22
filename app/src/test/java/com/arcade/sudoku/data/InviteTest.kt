package com.arcade.sudoku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteTest {
    private val url = "https://script.google.com/macros/s/AKfycbx123-abc_XYZ/exec"

    @Test
    fun shareTextParsesBack() {
        val parsed = Invite.parse(Invite(url, "K7PQ2M").toShareText())
        assertEquals(url, parsed.scriptUrl)
        assertEquals("K7PQ2M", parsed.code)
    }

    @Test
    fun parsesLooseMessages() {
        val parsed = Invite.parse("join me at $url. secret code = ab12cd")
        assertEquals(url, parsed.scriptUrl)
        assertEquals("AB12CD", parsed.code)
    }

    @Test
    fun missingPartsAreNull() {
        val parsed = Invite.parse("hello there")
        assertNull(parsed.scriptUrl)
        assertNull(parsed.code)
    }

    @Test
    fun randomCodesAreUnambiguous() {
        repeat(100) {
            val code = Invite.randomCode()
            assertEquals(6, code.length)
            assertTrue(code.none { it in "01IO" })
        }
    }
}

class InvitePasswordTest {
    private val url = "https://arcade-sudoku.jahangir.workers.dev"

    @Test
    fun cloudflareInviteWithPasswordParsesBack() {
        val parsed = Invite.parse(Invite(url, "K7PQ2M", "s3cret!").toShareText())
        assertEquals(url, parsed.scriptUrl)
        assertEquals("K7PQ2M", parsed.code)
        assertEquals("s3cret!", parsed.password)
    }

    @Test
    fun noPasswordLineWhenThereIsNone() {
        val text = Invite(url, "K7PQ2M", "").toShareText()
        assertTrue("password" !in text.lowercase())
        assertNull(Invite.parse(text).password)
    }

    @Test
    fun recognisesServerTypes() {
        assertTrue(BattleBackend.isAppsScript("https://script.google.com/macros/s/x/exec"))
        assertTrue(!BattleBackend.isAppsScript(url))
    }
}

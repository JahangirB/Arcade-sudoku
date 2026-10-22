package com.arcade.sudoku.data

import kotlin.random.Random

/** The script link and secret code the host shares with the opponent. */
data class Invite(val scriptUrl: String?, val code: String?) {
    fun toShareText() =
        "Let's play a Sudoku Battle in Arcade Sudoku!\nScript link: $scriptUrl\nSecret code: $code"

    companion object {
        private val URL_REGEX = Regex("""https://script\.google(?:usercontent)?\.com/\S+""")
        private val CODE_REGEX = Regex("""(?i)secret\s*code\s*[:=]?\s*([A-Z0-9]{4,12})""")

        /** Pulls the link and code out of a shared invite message; either may be missing. */
        fun parse(text: String): Invite {
            val url = URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ';')
            val code = CODE_REGEX.find(text)?.groupValues?.get(1)?.uppercase()
            return Invite(url, code)
        }

        // No 0/O or 1/I so a code read aloud is unambiguous.
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun randomCode(length: Int = 6) =
            (1..length).map { CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    }
}

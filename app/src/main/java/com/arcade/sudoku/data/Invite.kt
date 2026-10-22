package com.arcade.sudoku.data

import kotlin.random.Random

/** The server link, optional server password and secret code the host shares with the opponent. */
data class Invite(val scriptUrl: String?, val code: String?, val password: String? = null) {
    fun toShareText() = buildString {
        append("Let's play in Game Suite!\n")
        append("Server link: $scriptUrl\n")
        if (!password.isNullOrBlank()) append("Server password: $password\n")
        append("Secret code: $code")
    }

    companion object {
        private val URL_REGEX = Regex("""https://\S+""")
        private val CODE_REGEX = Regex("""(?i)secret\s*code\s*[:=]?\s*([A-Z0-9]{4,12})""")
        private val PASSWORD_REGEX = Regex("""(?i)server\s*password\s*[:=]\s*(\S+)""")

        /** Pulls the link, password and code out of a shared invite message; any may be missing. */
        fun parse(text: String): Invite {
            val url = URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ';')
            val code = CODE_REGEX.find(text)?.groupValues?.get(1)?.uppercase()
            val password = PASSWORD_REGEX.find(text)?.groupValues?.get(1)
            return Invite(url, code, password)
        }

        // No 0/O or 1/I so a code read aloud is unambiguous.
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun randomCode(length: Int = 6) =
            (1..length).map { CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    }
}

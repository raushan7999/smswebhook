package com.bestcallerapp.smswebhook.core.utils

import java.security.MessageDigest

object Hashing {
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte -> append(HEX[(byte.toInt() shr 4) and 0x0F]).append(HEX[byte.toInt() and 0x0F]) }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

package com.bestcallerapp.smswebhook.core.security

/** Encrypts small secrets (e.g. header values) before they are persisted. */
interface SecretCipher {
    fun encrypt(plaintext: String): String

    /** @throws SecretUnavailableException if the value cannot be decrypted (e.g. key lost). */
    fun decrypt(ciphertext: String): String
}

class SecretUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

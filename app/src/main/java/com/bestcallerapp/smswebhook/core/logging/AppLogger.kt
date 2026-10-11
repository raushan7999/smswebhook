package com.bestcallerapp.smswebhook.core.logging

/**
 * Application logger. Implementations must never receive SMS bodies, header values or
 * full webhook URLs — callers log identifiers only.
 */
interface AppLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

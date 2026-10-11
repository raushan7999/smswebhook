package com.bestcallerapp.smswebhook.core.logging

import android.util.Log
import com.bestcallerapp.smswebhook.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logcat-backed logger. Debug output is suppressed in release builds; R8 additionally strips
 * [Log.d]/[Log.v] calls (see keep rules). Throwable messages are reduced to their class name in
 * release so exception text that might echo request data never reaches logcat.
 */
@Singleton
class AndroidAppLogger @Inject constructor() : AppLogger {

    override fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(prefixed(tag), message)
    }

    override fun i(tag: String, message: String) {
        Log.i(prefixed(tag), message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(prefixed(tag), withThrowable(message, throwable))
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG && throwable != null) {
            Log.e(prefixed(tag), message, throwable)
        } else {
            Log.e(prefixed(tag), withThrowable(message, throwable))
        }
    }

    private fun withThrowable(message: String, throwable: Throwable?): String =
        if (throwable == null) message else "$message [${throwable.javaClass.simpleName}]"

    private fun prefixed(tag: String) = "SmsWebhook/$tag".take(MAX_TAG_LENGTH)

    private companion object {
        const val MAX_TAG_LENGTH = 23
    }
}

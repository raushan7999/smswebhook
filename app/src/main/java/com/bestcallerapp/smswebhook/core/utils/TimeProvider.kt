package com.bestcallerapp.smswebhook.core.utils

import java.time.ZoneId
import javax.inject.Inject

/** Abstraction over the wall clock so time-dependent logic is testable. */
interface TimeProvider {
    fun nowMillis(): Long
    fun zone(): ZoneId
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

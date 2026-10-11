package com.bestcallerapp.smswebhook.core.utils

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher


/** Process-wide scope for work that must outlive a single component (e.g. receiver goAsync). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

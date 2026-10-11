package com.bestcallerapp.smswebhook.core.extensions

fun String.truncate(maxChars: Int, ellipsis: String = "…"): String =
    if (length <= maxChars) this else take(maxChars) + ellipsis

/** Escapes LIKE wildcards so user search input is matched literally (used with ESCAPE '\'). */
fun String.escapeForLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

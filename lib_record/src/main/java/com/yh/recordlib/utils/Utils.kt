package com.yh.recordlib.utils

import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

fun logLevel(value: Int): String {
    return when(value) {
        Log.VERBOSE -> "V"
        Log.DEBUG   -> "D"
        Log.INFO    -> "I"
        Log.WARN    -> "W"
        Log.ERROR   -> "E"
        Log.ASSERT  -> "A"
        else        -> "UNKNOWN"
    }
}

internal val isMIUI by lazy { Build.MANUFACTURER.contains("xiaomi", true) }

private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy.M.d HH:mm:ss", Locale.CHINESE) }

internal val Long.toDate
    get() =
        if(this > 0) {
            DATE_FORMATTER.format(this)
        } else {
            "0"
        }


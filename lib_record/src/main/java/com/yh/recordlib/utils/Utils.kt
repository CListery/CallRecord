package com.yh.recordlib.utils

import android.os.Build
import android.util.Log

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

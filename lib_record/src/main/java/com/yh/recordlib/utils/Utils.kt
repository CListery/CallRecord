package com.yh.recordlib.utils

import android.util.Log

fun logLevel(value: Int): String? {
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

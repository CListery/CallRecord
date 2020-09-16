package com.yh.recordlib.utils

import android.util.Log

fun logLevel(value: Int): String? {
    return when(value) {
        Log.VERBOSE -> "VERBOSE"
        Log.DEBUG   -> "DEBUG"
        Log.INFO    -> "INFO"
        Log.WARN    -> "WARN"
        Log.ERROR   -> "ERROR"
        Log.ASSERT  -> "ASSERT"
        else        -> "UNKNOWN"
    }
}

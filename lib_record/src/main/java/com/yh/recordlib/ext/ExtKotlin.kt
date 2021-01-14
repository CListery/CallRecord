package com.yh.recordlib.ext

import android.os.Build
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.math.ln
import kotlin.math.pow

fun Float.round1(): Float = Math.round(this * 10.0) / 10.0f

fun Double.round1(): Double = Math.round(this * 10.0) / 10.0

fun Float.round2(): Float = Math.round(this * 100.0) / 100.0f

fun Double.round2(): Double = Math.round(this * 100.0) / 100.0

inline fun runOnApi(api: Int, f: () -> Unit, otherwise: () -> Unit = {}) {
    if(Build.VERSION.SDK_INT == api) {
        f()
    } else {
        otherwise()
    }
}

inline fun runOnApiBelow(api: Int, f: () -> Unit) {
    if(Build.VERSION.SDK_INT < api) {
        f()
    }
}

inline fun runOnApiAbove(api: Int, f: () -> Unit) {
    if(Build.VERSION.SDK_INT > api) {
        f()
    }
}

inline fun runOnApiBelow(api: Int, f: () -> Unit, otherwise: () -> Unit = {}) {
    if(Build.VERSION.SDK_INT < api) {
        f()
    } else {
        otherwise()
    }
}

inline fun runOnApiAbove(api: Int, f: () -> Unit, otherwise: () -> Unit = {}) {
    if(Build.VERSION.SDK_INT > api) {
        f()
    } else {
        otherwise()
    }
}

/**
 * Convert bytes into normalized unit string
 */
fun humanReadableByteCount(bytes: Long): String {
    val unit = 1024
    if(bytes < unit) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.2f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
}

/**
 * Format passed bytes into megabytes string
 */
fun convertBytesToMega(bytes: Long): String {
    val megaBytes = bytes.toDouble() / (1024.0 * 1024.0)
    val df = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
    
    return "${df.format(megaBytes)} MB"
}

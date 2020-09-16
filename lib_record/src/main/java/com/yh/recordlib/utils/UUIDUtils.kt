package com.yh.recordlib.utils

import android.util.Base64
import com.yh.recordlib.BuildConfig
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Created by CYH on 2019-06-17 08:51
 */

fun makeRandomUUID(needSplit: Boolean = false): String {
    val md: MessageDigest
    try {
        md = MessageDigest.getInstance("MD5")
    } catch(nsae: NoSuchAlgorithmException) {
        throw Error("InternalError: MD5 not supported", nsae)
    }
    
    val time = System.currentTimeMillis()
    val name = ByteArray(255)
    for(i in 0 until 255) {
        name[i] = ((i * Math.random() * time) % 255).toByte()
    }
    val md5Bytes = md.digest(name)
    md5Bytes[6] = md5Bytes[6] and 0x0f.toByte()  /* clear version        */
    md5Bytes[6] = md5Bytes[6] or 0x60.toByte()   /* set to version 6     */
    md5Bytes[8] = md5Bytes[8] and 0x3f.toByte()  /* clear variant        */
    md5Bytes[8] = md5Bytes[8] or 0x80.toByte()   /* set to IETF variant  */
    
    if(BuildConfig.DEBUG && md5Bytes.size != 16) {
        error("data must be 16 bytes in length")
    }
    
    for(i in 0..15) md5Bytes[i] = md5Bytes[i] and (Math.random() * 255).toByte()
    
    var msb: Long = 0
    var lsb: Long = 0
    
    for(i in 0..7) msb = (msb shl 8) or (md5Bytes[i].toLong() and 0xff)
    for(i in 8..15) lsb = (lsb shl 8) or (md5Bytes[i].toLong() and 0xff)
    
    return makeStringUUID(msb, lsb, needSplit)
}

fun makeMD5UUID(): String {
    val md: MessageDigest
    try {
        md = MessageDigest.getInstance("MD5")
    } catch(nsae: NoSuchAlgorithmException) {
        throw Error("InternalError: MD5 not supported", nsae)
    }
    return Base64.encode(md.digest(makeRandomUUID().toByteArray(Charsets.UTF_8)), 3)
        .toString(Charsets.UTF_8)
}

private fun makeStringUUID(
    mostSigBits: Long, leastSigBits: Long, needSplit: Boolean
): String {
    return StringBuffer().apply {
        append(digits(mostSigBits shr 32, 8))
        if(needSplit) append("-")
        append(digits(mostSigBits shr 16, 4))
        if(needSplit) append("-")
        append(digits(mostSigBits, 4))
        if(needSplit) append("-")
        append(digits(leastSigBits shr 48, 4))
        if(needSplit) append("-")
        append(digits(leastSigBits, 4))
        if(needSplit) append("-")
        append(digits((leastSigBits + mostSigBits) shr 64, 2))
        if(needSplit) append("-")
        append(digits((leastSigBits + mostSigBits) shl 64, 2))
    }
        .toString()
}

private fun digits(key: Long, digits: Int): String {
    val hi = 1L shl (digits * 4)
    return java.lang.Long.toHexString(hi or (key and (hi - 1)))
        .substring(1)
}


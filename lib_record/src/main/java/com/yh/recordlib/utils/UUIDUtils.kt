package com.yh.recordlib.utils

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.random.Random

/**
 * Created by CYH on 2019-06-17 08:51
 */

fun makeRandomUUID(phoneNumber: String): String {
    val md: MessageDigest
    try {
        md = MessageDigest.getInstance("MD5")
    } catch(nsae: NoSuchAlgorithmException) {
        throw Error("InternalError: MD5 not supported", nsae)
    }
    
    val time = System.currentTimeMillis()
    val origin = "$phoneNumber$time${Random(time).nextLong()}${UUID.randomUUID()}"
    val md5Bytes = md.digest(origin.toByteArray())
    
    val sb = StringBuffer()
    for(byte in md5Bytes) {
        val hex = Integer.toHexString(0xFF and byte.toInt())
        if(hex.length == 1) {
            sb.append("0").append(hex)
        } else {
            sb.append(hex)
        }
    }
    return sb.toString().uppercase()
}

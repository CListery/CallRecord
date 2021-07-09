package com.yh.recordlib.entity

import com.yh.recordlib.utils.toDate
import io.realm.RealmObject
import io.realm.annotations.RealmClass

@RealmClass
open class SystemCallRecord : RealmObject, Comparable<SystemCallRecord> {
    
    var callId: Long = -1L
    var date: Long = -1L
    var duration: Long = -1L
    var type: Int = -1
    var phoneAccountId: Int = -1
    var phoneNumber: String = ""
    
    constructor() : super()
    
    constructor(
        callId: Long,
        date: Long,
        duration: Long,
        type: Int,
        phoneAccountId: Int,
        phoneNumber: String
    ) : super() {
        this.callId = callId
        this.date = date
        this.duration = duration
        this.type = type
        this.phoneAccountId = phoneAccountId
        this.phoneNumber = phoneNumber
    }
    
    fun getDurationMillis(): Long {
        return duration * 1000
    }
    
    override fun toString(): String {
        return """SystemCallRecord(
            |callId=$callId,
            |date[${date.toDate}]=$date,
            |duration=$duration,
            |type=$type,
            |phoneAccountId=$phoneAccountId,
            |phoneNumber="$phoneNumber"
            |)""".trimMargin().lines().joinToString("")
    }
    
    override fun compareTo(other: SystemCallRecord): Int {
        return date.compareTo(other.date)
    }
}
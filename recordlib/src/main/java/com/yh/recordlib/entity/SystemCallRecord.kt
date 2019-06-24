package com.yh.recordlib.entity

import io.realm.RealmObject
import io.realm.annotations.RealmClass

@RealmClass
open class SystemCallRecord : RealmObject() {
    
    var callId: Long = -1L
    var date: Long = -1L
    var duration: Long = -1L
    var type: Int = -1
    var phoneAccountId: Int = -1
    var phoneNumber: String = ""
    
    fun getDurationMillis(): Long {
        if(duration in 1..999) {
            return duration * 1000
        }
        return 0
    }
    
    override fun toString(): String {
        return "SystemCallRecord(callId=$callId, date=$date, duration=$duration, type=$type, phoneAccountId=$phoneAccountId, phoneNumber='$phoneNumber')"
    }
}
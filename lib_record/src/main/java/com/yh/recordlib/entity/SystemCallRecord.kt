package com.yh.recordlib.entity

import io.realm.RealmObject
import io.realm.annotations.RealmClass

@RealmClass
open class SystemCallRecord : RealmObject() {
    
    var callId: Long = -1L
    var date: Long = -1L
    var lastModify: Long = -1L
    var duration: Long = -1L
    var type: Int = -1
    var phoneAccountId: Int = -1
    var phoneNumber: String = ""
    
    fun getDurationMillis(): Long {
        return duration * 1000
    }
    
    override fun toString(): String {
        return "SystemCallRecord(callId=$callId, date=$date, lastModify=$lastModify, duration=$duration, type=$type, phoneAccountId=$phoneAccountId, phoneNumber='$phoneNumber')"
    }
}
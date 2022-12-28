package com.yh.recordlib.ext

import android.database.Cursor
import android.provider.CallLog
import com.yh.appbasic.logger.logW
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.entity.SystemCallRecord

/**
 * Created by CYH on 2019-06-17 11:17
 */
@Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
fun <T : Any> Cursor.get(columnIndex: Int, defaultVal: T): T {
    if(-1 == columnIndex || isNull(columnIndex)) {
        return defaultVal
    }
    return when(defaultVal) {
        is Long      -> getLong(columnIndex)
        is Int       -> getInt(columnIndex)
        is String    -> getString(columnIndex)
        is Double    -> getDouble(columnIndex)
        is Float     -> getFloat(columnIndex)
        is ByteArray -> getBlob(columnIndex)
        is Boolean   -> getInt(columnIndex) == 1
        else         -> defaultVal
    } as T
}

fun Cursor.parseSystemRecord(
    columnIndexId: Int,
    columnIndexDate: Int,
    columnIndexDuration: Int,
    columnIndexType: Int,
    subscriptionIdColumnIndexes: Array<Int>,
    numberColumnIndexes: Array<Int>
): SystemCallRecord {
    return SystemCallRecord().apply {
        callId = get(columnIndexId, -1L)
        date = get(columnIndexDate, -1L)
        duration = get(columnIndexDuration, -1L)
        type = get(columnIndexType, -1)
        subscriptionIdColumnIndexes.forEach {
            val subscriptionId = get(it, -1)
            if (subscriptionId in 0..4) {
                phoneAccountId = subscriptionId
                return@forEach
            }
        }
        numberColumnIndexes.forEach {
            val tmpNumber = get(it, "")
            if (tmpNumber.isNotEmpty()) {
                phoneNumber = TelephonyCenter.get().filterGarbageInPhoneNumber(tmpNumber)
                return@forEach
            }
        }
    }
}

fun Cursor?.parseSystemCallRecords(
    successAction: ((ArrayList<SystemCallRecord>) -> Unit)? = null, failAction: (() -> Unit)? = null
) {
    if(null == this) {
        logW("parseSystemCallRecords: cursor -> NULL!", loggable = TelephonyCenter.get())
        failAction?.invoke()
        return
    }
    val records = arrayListOf<SystemCallRecord>()
    this.use callLogResult@{ result ->
        if(result.count <= 0 || !result.moveToFirst()) {
            logW("parseSystemCallRecords: count -> ${result.count}", loggable = TelephonyCenter.get())
            failAction?.invoke()
            return
        }
        
        val columnIndexId = result.getColumnIndex(CallLog.Calls._ID)
        val columnIndexDate = result.getColumnIndex(CallLog.Calls.DATE)
        val columnIndexDuration = result.getColumnIndex(CallLog.Calls.DURATION)
        val columnIndexType = result.getColumnIndex(CallLog.Calls.TYPE)
        
        val columnIndexSubscriptionId = result.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
        val columnIndexSimId = result.getColumnIndex("simid")
        val columnIndexSubId = result.getColumnIndex("sub_id")
        
        val columnIndexNumber = result.getColumnIndex(CallLog.Calls.NUMBER)
        val columnIndexMatchedNumber = result.getColumnIndex("matched_number")
        
        do {
            val sr = result.parseSystemRecord(
                columnIndexId,
                columnIndexDate,
                columnIndexDuration,
                columnIndexType,
                arrayOf(columnIndexSubscriptionId, columnIndexSimId, columnIndexSubId),
                arrayOf(columnIndexNumber, columnIndexMatchedNumber)
            )
            if(sr.phoneNumber.isNotEmpty()) {
                records.add(sr)
            }
        } while(result.moveToNext())
    }
    successAction?.invoke(records)
}
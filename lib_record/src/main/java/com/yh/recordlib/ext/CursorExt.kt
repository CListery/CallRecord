package com.yh.recordlib.ext

import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import com.yh.appinject.logger.ext.libW
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
        is Long -> getLong(columnIndex)
        is Int -> getInt(columnIndex)
        is String -> getString(columnIndex)
        is Double -> getDouble(columnIndex)
        is Float -> getFloat(columnIndex)
        is ByteArray -> getBlob(columnIndex)
        is Boolean -> getInt(columnIndex) == 1
        else -> defaultVal
    } as T
}

fun Cursor.parseSystemRecord(
    columnIndexId: Int,
    columnIndexDate: Int,
    columnIndexDuration: Int,
    columnIndexType: Int,
    columnIndexSubscriptionId: Int,
    columnIndexNumber: Int,
    columnIndexLastModified: Int
): SystemCallRecord {
    return SystemCallRecord().apply {
        callId = get(columnIndexId, -1L)
        date = get(columnIndexDate, -1L)
        duration = get(columnIndexDuration, -1L)
        type = get(columnIndexType, -1)
        //这个获取不到也不影响，recalculateDuration 会处理通话时长
        phoneAccountId = get(columnIndexSubscriptionId, -1)
        phoneNumber = TelephonyCenter.get().filterGarbageInPhoneNumber(get(columnIndexNumber, ""))
        lastModify = get(columnIndexLastModified, -1L)
    }
}

fun Cursor?.parseSystemCallRecords(
    successAction: ((ArrayList<SystemCallRecord>) -> Unit)? = null, failAction: (() -> Unit)? = null
) {
    if(null == this) {
        TelephonyCenter.get().libW("parseSystemCallRecords: cursor -> NULL!")
        failAction?.invoke()
        return
    }
    val records = arrayListOf<SystemCallRecord>()
    this.use callLogResult@{ result ->
        if(result.count <= 0 || !result.moveToFirst()) {
            TelephonyCenter.get().libW("parseSystemCallRecords: count -> ${result.count}")
            failAction?.invoke()
            return
        }
        
        val columnIndexId = result.getColumnIndex(CallLog.Calls._ID)
        val columnIndexDate = result.getColumnIndex(CallLog.Calls.DATE)
        val columnIndexDuration = result.getColumnIndex(CallLog.Calls.DURATION)
        val columnIndexType = result.getColumnIndex(CallLog.Calls.TYPE)
        
        val columnIndexSubscriptionId = if(Build.VERSION.SDK_INT >= 21) {
            result.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
        } else {
            var index = result.getColumnIndex("sub_id")
            if(-1 == index) {
                index = result.getColumnIndex("simid")
            }
            index
        }
        val columnIndexNumber = result.getColumnIndex(CallLog.Calls.NUMBER)
        val columnIndexLastModified = result.getColumnIndex("last_modified")
        
        do {
            records.add(
                result.parseSystemRecord(
                    columnIndexId,
                    columnIndexDate,
                    columnIndexDuration,
                    columnIndexType,
                    columnIndexSubscriptionId,
                    columnIndexNumber,
                    columnIndexLastModified
                )
            )
        } while(result.moveToNext())
    }
    successAction?.invoke(records)
}
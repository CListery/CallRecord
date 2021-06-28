package com.yh.recordlib.ext

import com.yh.appinject.logger.ext.libD
import com.yh.krealmextensions.querySortedAsync
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.service.RecordCallService
import io.realm.Sort

/**
 * Created by CYH on 2019-05-30 14:20
 */

fun findRecordById(recordId: String, callback: (CallRecord?) -> Unit) {
    querySortedAsync<CallRecord>({
        TelephonyCenter.get().libD("findRecordById: ${it.size}")
        callback.invoke(it.firstOrNull())
    }, "callStartTime", Sort.DESCENDING, { equalTo("recordId", recordId) })
}

fun findRecordByIdNotNull(recordId: String, callback: (CallRecord) -> Unit) {
    querySortedAsync<CallRecord>({
        TelephonyCenter.get().libD("findRecordByIdNotNull: ${it.size}")
        if(it.isNotEmpty()) {
            callback.invoke(it.first())
        }
    }, "callStartTime", Sort.DESCENDING, { equalTo("recordId", recordId) })
}

fun findAllUnSyncRecords(callback: (List<CallRecord>) -> Unit) {
    querySortedAsync<CallRecord>({
        TelephonyCenter.get().libD("findAllUnSyncRecords: ${it.size}")
        callback.invoke(it)
    }, "callStartTime", Sort.DESCENDING, { equalTo("synced", false).and().equalTo("isDeleted", false) })
}

fun queryLastRecord(callback: (CallRecord?) -> Unit) {
    querySortedAsync<CallRecord>({
        TelephonyCenter.get().libD("queryLastRecord: ${it.size}")
        callback.invoke(it.firstOrNull())
    }, "callStartTime", Sort.DESCENDING)
}

package com.yh.recordlib.ext

import com.vicpin.krealmextensions.delete
import com.vicpin.krealmextensions.querySorted
import com.yh.appinject.logger.ext.libD
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.entity.CallRecord
import io.realm.Sort

/**
 * Created by CYH on 2019-05-30 14:20
 */
fun findRecordById(recordId: String): CallRecord? {
    val records = querySorted<CallRecord>("callStartTime", Sort.DESCENDING) {
        equalTo("recordId", recordId)
    }
    if(records.isNotEmpty()) {
        return records.first()
    }
    return null
}

fun findAllUnSyncRecords(): List<CallRecord>? {
    return querySorted("callStartTime", Sort.DESCENDING) {
        equalTo("synced", false).and().equalTo("isDeleted", false)
    }
}

fun queryLastRecord(): CallRecord? {
    val recordList = querySorted<CallRecord>("callStartTime", Sort.DESCENDING)
    TelephonyCenter.get().libD("queryLastRecord: $recordList")
    if(recordList.isNotEmpty()) {
        return recordList.first()
    }
    return null
}

fun deleteRecordById(recordId: String) {
    delete<CallRecord> { equalTo("recordId", recordId) }
}
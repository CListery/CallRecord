package com.yh.recordlib.ext

import com.vicpin.krealmextensions.querySorted
import com.yh.recordlib.entity.CallRecord
import io.realm.Sort
import timber.log.Timber

/**
 * Created by CYH on 2019-05-30 14:20
 */

fun findRecordById(recordId: String): CallRecord? {
    return querySorted<CallRecord>("callStartTime", Sort.DESCENDING) {
        equalTo("recordId", recordId)
    }.first()
}

fun findAllUnSyncRecords(): List<CallRecord>? {
    return querySorted("callStartTime", Sort.DESCENDING) {
        equalTo("synced", false).and()
            .equalTo("isDeleted", false)
    }
}

fun queryLastRecord(): CallRecord? {
    val recordList = querySorted<CallRecord>("callStartTime", Sort.DESCENDING)
    Timber.d("queryLastRecord: $recordList")
    if(recordList.isNotEmpty()) {
        return recordList.first()
    }
    return null
}


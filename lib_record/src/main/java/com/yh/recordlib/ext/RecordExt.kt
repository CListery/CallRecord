package com.yh.recordlib.ext

import com.yh.krealmextensions.delete
import com.yh.krealmextensions.querySorted
import com.yh.krealmextensions.querySortedAsync
import com.yh.recordlib.entity.CallRecord
import io.realm.Sort

/**
 * Created by CYH on 2019-05-30 14:20
 */
fun findRecordById(recordId: String): CallRecord? {
    return querySorted<CallRecord>("callStartTime", Sort.DESCENDING) {
        equalTo("recordId", recordId)
    }.firstOrNull()
}

fun findAllUnSyncRecords(): List<CallRecord> {
    return querySorted("callStartTime", Sort.DESCENDING) {
        equalTo("synced", false).and().equalTo("isDeleted", false)
    }
}

fun queryLastRecord(): CallRecord? {
    return querySorted<CallRecord>("callStartTime", Sort.DESCENDING).firstOrNull()
}

fun deleteRecordById(recordId: String) {
    delete<CallRecord> { equalTo("recordId", recordId) }
}
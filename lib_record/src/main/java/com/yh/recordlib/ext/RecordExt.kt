package com.yh.recordlib.ext

import com.yh.krealmextensions.querySortedAsync
import com.yh.recordlib.entity.CallRecord
import io.realm.Sort

/**
 * Created by CYH on 2019-05-30 14:20
 */

fun findRecordById(recordId: String, callback: (CallRecord?) -> Unit) {
    querySortedAsync<CallRecord>({
        callback.invoke(it.firstOrNull())
    }, "callStartTime", Sort.DESCENDING, { equalTo("recordId", recordId) })
}

fun findAllUnSyncRecords(callback: (List<CallRecord>) -> Unit) {
    querySortedAsync<CallRecord>({
        callback.invoke(it)
    }, "callStartTime", Sort.DESCENDING, { equalTo("synced", false).and().equalTo("isDeleted", false) })
}

fun queryLastRecord(callback: (CallRecord?) -> Unit) {
    querySortedAsync<CallRecord>({
        callback.invoke(it.firstOrNull())
    }, "callStartTime", Sort.DESCENDING)
}

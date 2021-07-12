package com.yh.recordlib.ext

import com.yh.appinject.logger.ext.libD
import com.yh.krealmextensions.querySorted
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.entity.CallRecord
import io.realm.Sort

/**
 * Created by CYH on 2019-05-30 14:20
 */

fun findRecordById(recordId: String): CallRecord? {
    val results = querySorted<CallRecord>("callStartTime", Sort.DESCENDING) { equalTo("recordId", recordId) }
    TelephonyCenter.get().libD("findRecordById: ${results.size}")
    return results.firstOrNull()
}

fun findAllUnSyncRecords(syncTimeOffset: Long, maxSyncCount: Int = Int.MAX_VALUE): List<CallRecord> {
    val results = querySorted<CallRecord>("callStartTime", Sort.DESCENDING) {
        if(syncTimeOffset != Long.MAX_VALUE) {
            val date = System.currentTimeMillis()
            if(date > syncTimeOffset) {
                between("callStartTime", date - syncTimeOffset, date)
            }
        }
        equalTo("synced", false)
        equalTo("isDeleted", false)
        if(Int.MAX_VALUE != maxSyncCount) {
            lessThan("syncCount", maxSyncCount)
        }
    }
    TelephonyCenter.get().libD("findAllUnSyncRecords: ${results.size}")
    return results
}

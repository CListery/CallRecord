package com.yh.recordlib

import androidx.annotation.MainThread
import com.yh.recordlib.entity.CallRecord

/**
 * Created by CYH on 2019-06-24 16:16
 */
interface ISyncCallback {
    
    @MainThread
    fun onSyncSuccess(record: CallRecord) {}
    @MainThread
    fun onSyncFail(record: CallRecord) {}
}
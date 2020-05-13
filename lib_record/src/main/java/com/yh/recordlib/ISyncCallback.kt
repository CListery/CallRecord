package com.yh.recordlib

import androidx.annotation.MainThread

/**
 * Created by CYH on 2019-06-24 16:16
 */
interface ISyncCallback {
    
    @MainThread
    fun onSyncSuccess(recordId: String) {}
    @MainThread
    fun onSyncFail(recordId: String) {}
}
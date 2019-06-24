package com.yh.recordlib

/**
 * Created by CYH on 2019-06-24 16:16
 */
interface ISyncCallback {
    
    fun onSyncSuccess(recordId: String) {}
    fun onSuncFail(recordId: String) {}
}
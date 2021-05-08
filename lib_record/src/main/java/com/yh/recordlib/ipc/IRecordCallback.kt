package com.yh.recordlib.ipc

import com.codezjx.andlinker.annotation.RemoteInterface

/**
 * Created by CYH on 2019-05-30 11:11
 */
@RemoteInterface
interface IRecordCallback {
    
    fun onRecordIdCreated(recordId: String)
    fun onCallIn(recordId: String)
    fun onCallOut(recordId: String)
    fun onCallEnd(recordId: String)
    fun onCallOffHook(recordId: String)
    
}
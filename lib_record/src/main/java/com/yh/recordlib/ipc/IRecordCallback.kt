package com.yh.recordlib.ipc

import com.codezjx.andlinker.annotation.RemoteInterface
import com.yh.recordlib.entity.CallRecord

/**
 * Created by CYH on 2019-05-30 11:11
 */
@RemoteInterface
interface IRecordCallback {
    
    fun onRecordIdCreated(callRecord: CallRecord)
    fun onCallIn(recordId: String)
    fun onCallOut(recordId: String)
    fun onCallEnd(recordId: String)
    fun onCallOffHook(recordId: String)
    
}
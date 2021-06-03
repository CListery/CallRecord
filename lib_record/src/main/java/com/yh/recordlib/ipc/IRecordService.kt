package com.yh.recordlib.ipc

import com.codezjx.andlinker.annotation.In
import com.codezjx.andlinker.annotation.RemoteInterface

/**
 * Created by CYH on 2019-05-30 11:35
 */
@RemoteInterface
interface IRecordService {
    
    fun startListen(callNumber: String)
    fun stopListen()
    
    fun resumeLastRecord(recordId: String)
    fun registerRecordCallback(
        @In
        recordCallback: IRecordCallback
    )
    fun unRegisterRecordCallback(
        @In
        recordCallback: IRecordCallback
    )
}
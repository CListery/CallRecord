package com.yh.recordlib.ipc

import com.codezjx.andlinker.annotation.RemoteInterface

/**
 * Created by CYH on 2019-05-30 11:11
 */
@RemoteInterface
interface IRecordCallback {
    
    fun onRecordIdCreated(recordId: String)
}
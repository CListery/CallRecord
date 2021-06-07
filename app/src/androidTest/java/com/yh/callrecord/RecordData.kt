package com.yh.callrecord

import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.RecordMappingInfo
import com.yh.recordlib.entity.SystemCallRecord

class RecordData(
    val callRecords: List<CallRecord>,
    val systemRecords: List<SystemCallRecord>,
    val checker: (recordMappingInfo: RecordMappingInfo) -> Unit
)
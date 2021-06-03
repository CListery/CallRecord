package com.yh.recordlib.entity

class RecordMappingInfo {
    
    var noMappingRecords: List<CallRecord> = emptyList()
    var unUseSystemCallRecords: List<SystemCallRecord> = emptyList()
    var mappingRecords: Map<SystemCallRecord, CallRecord> = emptyMap()
    
}
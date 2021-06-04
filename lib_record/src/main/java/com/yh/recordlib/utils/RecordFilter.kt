package com.yh.recordlib.utils

import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.entity.RecordMappingInfo
import com.yh.recordlib.entity.SystemCallRecord
import kotlin.math.max

object RecordFilter {
    
    fun findMappingRecords(systemRecords: List<SystemCallRecord>, callRecords: List<CallRecord>): RecordMappingInfo {
        val tmpCallRecords = callRecords.sortedByDescending { it.callStartTime }.toMutableList()
        val tmpSystemCallRecords = systemRecords.sortedByDescending { it.date }.toMutableList()
        val result: HashMap<SystemCallRecord, CallRecord> = hashMapOf()
        findDateMapping(tmpCallRecords, tmpSystemCallRecords, result)
        val recordMappingInfo = RecordMappingInfo()
        recordMappingInfo.noMappingRecords = tmpCallRecords.toList()
        recordMappingInfo.mappingRecords = result.toMap()
        return recordMappingInfo
    }
    
    private fun findDateMapping(tmpCallRecords: MutableList<CallRecord>, tmpSystemCallRecords: MutableList<SystemCallRecord>, result: HashMap<SystemCallRecord, CallRecord>) {
        if(tmpCallRecords.isNotEmpty()) {
            tmpCallRecords.forEach { cr ->
                var minStartTimeOffset: Long = Long.MAX_VALUE
                var minEndTimeOffset: Long = Long.MAX_VALUE
                var targetSR: SystemCallRecord? = null
                tmpSystemCallRecords.forEach sys@{ sr ->
                    if(!result.containsKey(sr)) {
                        if(sr.phoneNumber != cr.phoneNumber) {
                            return@sys
                        }
                        val callStartTime = max(cr.callStartTime, cr.callOffHookTime)
                        val startTimeOffset = sr.date - callStartTime
                        if(startTimeOffset >= 0 && startTimeOffset < TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset) {
                            if(startTimeOffset < minStartTimeOffset) {
                                if(cr.callEndTime <= 0) {
                                    minStartTimeOffset = startTimeOffset
                                    targetSR = sr
                                } else {
                                    if(sr.date < cr.callEndTime) {
                                        val endTimeOffset = cr.callEndTime - sr.date
                                        if(endTimeOffset < minEndTimeOffset) {
                                            minEndTimeOffset = endTimeOffset
                                            targetSR = sr
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if(null != targetSR) {
                    result[targetSR!!] = cr
                    tmpSystemCallRecords.remove(targetSR!!)
                }
            }
            if(result.isNotEmpty()) {
                tmpCallRecords.removeAll(result.values)
            }
        }
    }
    
}
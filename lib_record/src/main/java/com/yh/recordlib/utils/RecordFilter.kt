package com.yh.recordlib.utils

import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.entity.RecordMappingInfo
import com.yh.recordlib.entity.SystemCallRecord
import kotlin.math.max

object RecordFilter {
    
    fun reloadMaxOffset(sr: SystemCallRecord, cr: CallRecord): Long {
        var maxOffset = TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset
        if(sr.date - cr.callStartTime > TelephonyCenter.get()
                .getRecordConfigure().maxCallTimeOffset
        ) {
            maxOffset = TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset * 3
        }
        return maxOffset
    }
    
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
                        val phoneNumber = sr.phoneNumber
                        if(phoneNumber.isNotEmpty()) {
                            if(sr.phoneNumber != cr.phoneNumber) {
                                return@sys
                            }
                        }
                        val startTimeOffset = sr.date - cr.callStartTime
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
    
    fun precisionFilter(
        sr: SystemCallRecord,
        crs: List<CallRecord>,
        precisionCount: Int,
        maxOffset: Long
    ): List<CallRecord> {
        if(precisionCount > maxOffset / TelephonyCenter.get().getRecordConfigure().minCallTimeOffset) {
            return crs
        }
        val tmp: List<CallRecord> = crs.filter { cr ->
            precisionFilter(sr, cr, precisionCount, maxOffset)
        }
        if(tmp.size > 1) {
            return precisionFilter(sr, tmp, precisionCount.inc(), maxOffset)
        }
        return tmp
    }
    
    /**
     * 精确过滤
     */
    fun precisionFilter(sr: SystemCallRecord, cr: CallRecord, precisionCount: Int, maxOffset: Long): Boolean {
        //重新计算时间偏移量
        val timeOffset = maxOffset - (TelephonyCenter.get().getRecordConfigure().minCallTimeOffset * precisionCount)
        val crStartTime = max(cr.callStartTime, cr.callOffHookTime)
        if(crStartTime > 0 && cr.callEndTime <= 0) { // 未能监听到结束时间的意外情况
            if(sr.duration > 0) {
                return sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
            }
        }
        val endTimeFilter = if(sr.duration > 0) { //（结束时长过滤器）内部数据库记录的通话结束时间 处于
            // 1.系统数据库通话记录 [开始时间+通话持续时长 +- 一分钟误差范围内]
            cr.callEndTime in (sr.date + sr.getDurationMillis() - timeOffset)..(sr.date + sr.getDurationMillis() + timeOffset)
        } else {
            //系统记录未接通,只判断开始时间
            return sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
        }
        val startTimeFilter = // 开始时长过滤器
            if(CallType.CallIn.ordinal == cr.callType) { // 呼入
                if(cr.callOffHookTime > 0) {
                    //当属于呼入类型且接通时开始时间以接通时间为准
                    var filter =
                        sr.date in (cr.callOffHookTime - timeOffset)..(cr.callOffHookTime + timeOffset)
                    if(!filter && endTimeFilter) {
                        //如果某些机型系统数据库中开始时间不是以接通时间,则使用开始时间为准
                        filter = sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
                    }
                    filter
                } else {
                    false
                }
            } else { // 呼出
                // 系统数据库通话记录 [开始时间] == 内部数据库通话记录 [开始时间 +- 一分钟误差范围内]
                sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
            }
        return startTimeFilter && endTimeFilter
    }
    
    /**
     * 粗略过滤
     * 1. 未能获取到号码的情况使用时间范围过滤
     * 2. 号码相同的情况直接过滤
     */
    fun coarseFilter(sr: SystemCallRecord, cr: CallRecord, timeOffset: Long = TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset): Boolean {
        return if(sr.phoneNumber.isEmpty() || cr.isFake) { // 号码未能正常获取，使用时间范围进行模糊过滤
            return when(cr.realCallType) {
                CallType.Unknown -> false
                CallType.CallIn  -> {
                    // 获取通话开始时间
                    val crStartTime = max(cr.callStartTime, cr.callOffHookTime)
                    //（结束时长过滤器）内部数据库记录的通话结束时间 处于系统数据库通话记录 [开始时间+通话持续时长 +- 偏移时间] 范围内
                    val endTimeFilter =
                        if(cr.callEndTime > 0) cr.callEndTime in (sr.date + sr.getDurationMillis() - timeOffset)..(sr.date + sr.getDurationMillis() + timeOffset)
                        else true
                    // 开始时长过滤器
                    val startTimeFilter =
                        if(cr.callOffHookTime > 0) {
                            var filter = sr.date in (cr.callOffHookTime - timeOffset)..(cr.callOffHookTime + timeOffset)
                            if(!filter && endTimeFilter) {
                                filter = sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
                            }
                            filter
                        } else {
                            false
                        }
                    startTimeFilter && endTimeFilter
                }
                CallType.CallOut -> {
                    // 获取通话开始时间
                    val crStartTime = cr.callStartTime
                    // 系统数据库通话记录 [开始时间] == 内部数据库通话记录 [开始时间 +- 偏移时间] 范围内
                    val startTimeFilter = sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
                    //（结束时长过滤器）内部数据库记录的通话结束时间 处于系统数据库通话记录 [开始时间+通话持续时长 +- 偏移时间] 范围内
                    val endTimeFilter =
                        if(cr.callEndTime > 0) cr.callEndTime in (sr.date + sr.getDurationMillis() - timeOffset)..(sr.date + sr.getDurationMillis() + timeOffset)
                        else true
                    startTimeFilter && endTimeFilter
                }
            }
        } else {
            //号码过滤
            cr.phoneNumber == sr.phoneNumber
        }
    }
    
}
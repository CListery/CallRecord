package com.yh.callrecord

import android.app.Application
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.yh.recordlib.RecordConfigure
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.utils.RecordFilter.findMappingRecords
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncFilterTest {
    
    @Before
    fun setup() {
        val application =
            InstrumentationRegistry.getTargetContext().applicationContext as Application
        val recordConfigure = RecordConfigure(
            application,
            startTimeOffset = 20000
        )
        TelephonyCenter.get().setupRecordConfigure(recordConfigure)
    }
    
    @Test
    fun testFilter() {
        dataRecords.forEach { rd ->
            rd.apply {
                if(callRecords.size == 1) {
                    testSyncSingle()
                } else {
                    testSyncAll()
                }
            }
        }
    }
    
    private fun RecordData.testSyncAll() {
        val recordMappingInfo = findMappingRecords(systemRecords, callRecords)
        checker.invoke(recordMappingInfo)
        
        // var firstCallStartTime: Long = System.currentTimeMillis()
        // var lastCallEndTime = 0L
        // callRecords.forEach { record ->
        //     if(record.callStartTime in 1 until firstCallStartTime) {
        //         firstCallStartTime = record.callStartTime
        //     }
        //     if(record.callEndTime > lastCallEndTime) {
        //         lastCallEndTime = record.callEndTime
        //     }
        // }
        // // 在监听过程中 APP 崩溃或被系统强制杀死时将监听不到通话结束时间
        // // 如异常通话记录的通话开始时间大于所有正常通话记录的结束时间，则以最后一条异常记录的开始时间+30分钟作为最后一条通话记录的结束时间
        // val allExceptionRecord = callRecords.filter { it.callEndTime <= 0 }
        // if(allExceptionRecord.isNotEmpty()) {
        //     val lastStartRecord = allExceptionRecord.maxBy { it.callStartTime }
        //     if(null != lastStartRecord) {
        //         if(lastStartRecord.callStartTime > lastCallEndTime){
        //             lastCallEndTime = lastStartRecord.callStartTime + 1800000
        //         }
        //     }
        // }
        // systemRecords.forEach sys@{ sr ->
        //     var targetRecords = ArrayList(callRecords).filter { cr ->
        //         coarseFilter(sr, cr)
        //     }
        //     if(targetRecords.isNotEmpty()) {
        //         // val maxOffset = reloadMaxOffset(sr, targetRecords.first())
        //         // val maxOffset = reloadMaxOffset(listOf(sr), targetRecords)
        //         val maxOffset = lastCallEndTime - firstCallStartTime
        //         targetRecords = precisionFilter(sr, targetRecords, 0, maxOffset)
        //
        //         when {
        //             targetRecords.size == 1 -> {
        //                 val target = targetRecords[0]
        //                 println("testSyncAll: recordCall -> $target")
        //                 checker.invoke(target, sr)
        //             }
        //
        //             targetRecords.isEmpty() -> {
        //                 //该条系统记录不能找到匹配的为同步记录,正常情况,因为没有一直监听
        //                 throw Exception("testSyncAll: Ignored Sys record: $sr")
        //             }
        //
        //             else                    -> {
        //                 //找到太多相似的记录，尝试同步时间偏移最小的一条
        //                 var target: CallRecord? = null
        //                 var timeOffset = Long.MAX_VALUE
        //                 targetRecords.forEach { cr ->
        //                     if(!cr.isFake) {
        //                         if(cr.phoneNumber != sr.phoneNumber) {
        //                             return@forEach
        //                         }
        //                     }
        //                     val offset = abs(sr.date - cr.callStartTime)
        //                     if(offset < timeOffset) {
        //                         timeOffset = offset
        //                         target = cr
        //                     }
        //                 }
        //                 if(timeOffset < 20000) { // 最大容许20s以内误差
        //                     println("testSyncAll: Use min time-offset: $timeOffset record: $target")
        //                     target?.apply {
        //                         checker.invoke(this, sr)
        //                     }
        //                         ?: throw Exception("testSyncAll: Not found target record")
        //                 } else {
        //                     throw Exception("testSyncAll: Find too many similar records: $sr")
        //                 }
        //             }
        //         }
        //     } else {
        //         throw Exception("testSyncAll: Not found target record: $sr")
        //     }
        // }
    }
    
    private fun RecordData.testSyncSingle() {
        val recordMappingInfo = findMappingRecords(systemRecords, callRecords)
        checker.invoke(recordMappingInfo)
        
        // val recordCall = callRecords.first()
        //
        // val callStartTime = recordCall.callStartTime.minus(
        //     TelephonyCenter.get().getRecordConfigure().startTimeOffset
        // )
        // val callEndTime = if(recordCall.callEndTime <= 0L) {
        //     callStartTime + 1800000
        // } else {
        //     recordCall.callEndTime.plus(
        //         TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset
        //     )
        // }
        //
        // val maxOffset = callEndTime - callStartTime
        // var synced = false
        // var failMsg = ""
        // systemRecords.forEach sys@{ sr ->
        //     if(coarseFilter(sr, recordCall)) {
        //         val targetRecords = precisionFilter(sr, arrayListOf(recordCall), 0, maxOffset)
        //         when {
        //             targetRecords.size == 1 -> {
        //                 val target = targetRecords[0]
        //                 println("syncTargetRecord: recordCall -> $target")
        //                 checker.invoke(target, sr)
        //                 synced = true
        //                 return
        //             }
        //
        //             targetRecords.isEmpty() -> {
        //                 //该条系统记录不能找到匹配的为同步记录,正常情况,因为没有一直监听
        //                 failMsg = "syncTargetRecord: Ignored Sys record: $sr"
        //                 return@sys
        //             }
        //         }
        //     } else {
        //         failMsg = "syncTargetRecord: Not found target record: $sr"
        //     }
        // }
        // if(!synced){
        //     throw Exception(failMsg)
        // }
    }
    
}
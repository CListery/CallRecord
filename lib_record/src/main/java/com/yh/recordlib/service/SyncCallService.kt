package com.yh.recordlib.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CallLog
import android.text.TextUtils
import androidx.core.app.JobIntentService
import com.vicpin.krealmextensions.delete
import com.vicpin.krealmextensions.save
import com.yh.appinject.logger.ext.libCursor
import com.yh.appinject.logger.ext.libD
import com.yh.appinject.logger.ext.libE
import com.yh.appinject.logger.ext.libW
import com.yh.recordlib.CallRecordController
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.cons.Constants
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.entity.FakeCallRecord
import com.yh.recordlib.entity.SystemCallRecord
import com.yh.recordlib.ext.findAllUnSyncRecords
import com.yh.recordlib.ext.findRecordById
import com.yh.recordlib.ext.parseSystemCallRecords
import com.yh.recordlib.notifier.RecordSyncNotifier
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by CYH on 2019-05-30 15:50
 */
class SyncCallService : JobIntentService() {
    
    companion object {
        /**
         * Unique job ID for this service.
         */
        private const val JOB_ID = 10001

        @JvmStatic
        fun enqueueWork(context: Context, recordId: String?) {
            TelephonyCenter.get().libW("enqueueWork1: $context - $recordId")
            if(null != recordId) {
                enqueueWork(context, Intent().putExtra(Constants.EXTRA_LAST_RECORD_ID, recordId))
            } else {
                enqueueWork(context)
            }
        }

        @JvmStatic
        fun enqueueWork(context: Context, work: Intent = Intent()) {
            TelephonyCenter.get().libW("enqueueWork2: $context - $work")
            enqueueWork(context, SyncCallService::class.java, JOB_ID, work)
        }
    }
    
    override fun onHandleWork(work: Intent) {
        if(work.hasExtra(Constants.EXTRA_RETRY)) {
            TelephonyCenter.get().libW("onHandleWork >>RETRY<< : $work")
        }
        TelephonyCenter.get().libW("onHandleWork 1: $work")
        val recordId = work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)
        TelephonyCenter.get().libD("onHandleWork 2: $recordId")
        
        if(null == recordId || TextUtils.isEmpty(recordId)) {
            syncAllRecord(work)
        } else {
            syncTargetRecord(work, recordId)
        }
    }
    
    private fun syncAllRecord(work: Intent) {
        val callLogClient = contentResolver.acquireUnstableContentProviderClient(CallLog.Calls.CONTENT_URI)
        if(null == callLogClient) {
            TelephonyCenter.get().libE("Can not load ${CallLog.Calls.CONTENT_URI} ContentProvider obj!!!")
            return
        }
        val tmpUnSyncRecords = findAllUnSyncRecords()
        TelephonyCenter.get().libD("syncAllRecord: tmpUnSyncRecords -> ${tmpUnSyncRecords?.toString()}")
        if(null == tmpUnSyncRecords || tmpUnSyncRecords.isEmpty()) {
            return
        }
        
        val allUnSyncRecords = ArrayList(tmpUnSyncRecords)
        
        var firstCallStartTime: Long = System.currentTimeMillis()
        var lastCallEndTime = 0L
        allUnSyncRecords.forEach { record ->
            if(record.callStartTime in 1 until firstCallStartTime) {
                firstCallStartTime = record.callStartTime
            }
            if(record.callEndTime > lastCallEndTime) {
                lastCallEndTime = record.callEndTime
            }
        }
        // 在监听过程中 APP 崩溃或被系统强制杀死时将监听不到通话结束时间
        // 如异常通话记录的通话开始时间大于所有正常通话记录的结束时间，则以最后一条异常记录的开始时间+30分钟作为最后一条通话记录的结束时间
        val allExceptionRecord = allUnSyncRecords.filter { it.callEndTime <= 0 }
        if(allExceptionRecord.isNotEmpty()) {
            val lastStartRecord = allExceptionRecord.maxBy { it.callStartTime }
            if(null != lastStartRecord) {
                if(lastStartRecord.callStartTime > lastCallEndTime){
                    lastCallEndTime = lastStartRecord.callStartTime + 1800000
                }
            }
        }
        try {
            val selection = StringBuilder()
            selection.append(CallLog.Calls.DATE)
            selection.append(" ")
            selection.append("BETWEEN")
            selection.append(" ")
            selection.append("?")
            selection.append(" ")
            selection.append("AND")
            selection.append(" ")
            selection.append("?")
            
            val args: ArrayList<String> = arrayListOf(
                firstCallStartTime.minus(TelephonyCenter.get().getRecordConfigure().minCallTimeOffset).toString(),
                lastCallEndTime.plus(TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset).toString()
            )
            val sort = CallLog.Calls.DEFAULT_SORT_ORDER
            
            TelephonyCenter.get().libD("syncAllRecord: selection -> $selection ; args -> $args")
    
            @SuppressLint("Recycle")
            // close by #parseSystemCallRecords
            val callLogResult = callLogClient.query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection.toString(),
                args.toArray(arrayOf<String>()),
                sort
            )
            TelephonyCenter.get().libCursor(callLogResult)
    
            callLogResult.parseSystemCallRecords(successAction = { systemRecords ->
                if(systemRecords.isNotEmpty()) {
                    var syncedCount = 0
                    systemRecords.forEach sys@{ sr ->
                        var targetRecords = ArrayList(allUnSyncRecords).filter { cr ->
                            coarseFilter(sr, cr)
                        }
                        if(targetRecords.isNotEmpty()) {
            
                            targetRecords = precisionFilter(sr, targetRecords)
            
                            when {
                                targetRecords.size == 1 -> {
                                    val target = targetRecords[0]
                                    syncRecordBySys(target, sr)
                                    TelephonyCenter.get().libD("syncAllRecord: recordCall -> $target")
                                    allUnSyncRecords.remove(target)
                                    syncedCount++
                                }
                
                                targetRecords.isEmpty() -> {
                                    //该条系统记录不能找到匹配的为同步记录,正常情况,因为没有一直监听
                                    TelephonyCenter.get().libW("syncAllRecord: Ignored Sys record: $sr")
                                    return@sys
                                }
                
                                else                    -> {
                                    //找到太多相似的记录，尝试同步时间偏移最小的一条
                                    var target: CallRecord? = null
                                    var timeOffset = Long.MAX_VALUE
                                    targetRecords.forEach { cr ->
                                        if(!cr.isFake){
                                            if(cr.phoneNumber != sr.phoneNumber){
                                                return@forEach
                                            }
                                        }
                                        val offset = abs(sr.date - cr.callStartTime)
                                        if(offset < timeOffset) {
                                            timeOffset = offset
                                            target = cr
                                        }
                                    }
                                    if(timeOffset < 5000) { // 最大容许5s以内误差
                                        TelephonyCenter.get().libW("syncAllRecord: Use min time-offset: $timeOffset record: $target")
                                        target?.apply {
                                            syncRecordBySys(this, sr)
                                            allUnSyncRecords.remove(this)
                                            syncedCount++
                                        }
                                    } else {
                                        TelephonyCenter.get().libE("syncAllRecord: Find too many similar records: $sr")
                                    }
                                    return@sys
                                }
                            }
                        } else {
                            TelephonyCenter.get().libE("syncAllRecord: Not found target record: $sr")
                        }
                    }
                    TelephonyCenter.get().libW("syncAllRecord: $syncedCount records have been synchronized done!!")
                    if(allUnSyncRecords.isNotEmpty()) {
                        TelephonyCenter.get().libE("syncAllRecord: Failed to sync successfully: $allUnSyncRecords")
                        markNoMappingRecord(allUnSyncRecords)
                        CallRecordController.get().retry(work)
                    }
                } else {
                    if(allUnSyncRecords.isNotEmpty()) {
                        TelephonyCenter.get().libE("syncAllRecord: Not found sys mapping record!!")
                        markNoMappingRecord(allUnSyncRecords)
                        CallRecordController.get().retry(work)
                    }
                }
            }, failAction = {
                if(allUnSyncRecords.isNotEmpty()) {
                    TelephonyCenter.get().libE("syncAllRecord: Not found any CallRecord by ${CallLog.Calls.DATE} between $firstCallStartTime to $lastCallEndTime from system!!")
                    markNoMappingRecord(allUnSyncRecords)
                    CallRecordController.get().retry(work)
                }
            })
        } catch(e: Exception) {
            if(allUnSyncRecords.isNotEmpty()) {
                TelephonyCenter.get().libE("syncAllRecord", throwable = e)
                markNoMappingRecord(allUnSyncRecords)
                CallRecordController.get().retry(work)
            }
        } finally {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                callLogClient.close()
            } else {
                @Suppress("DEPRECATION") callLogClient.release()
            }
        }
    }
    
    private fun markNoMappingRecord(allUnSyncRecords: ArrayList<CallRecord>) {
        allUnSyncRecords.forEach {
            if(System.currentTimeMillis() - it.callStartTime > 7200000) {
                //2小时未能成功同步,标记为删除状态
                it.isDeleted = true
            }
            it.isNoMapping = true
            it.save()
        }
        
        RecordSyncNotifier.get().notifyRecordSyncStatus(allUnSyncRecords)
    }
    
    private fun precisionFilter(sr: SystemCallRecord, crs: List<CallRecord>, precisionCount: Int = 0): List<CallRecord> {
        if(precisionCount > TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset / TelephonyCenter.get().getRecordConfigure().minCallTimeOffset) {
            return crs
        }
        val tmp: List<CallRecord> = crs.filter { cr ->
            precisionFilter(sr, cr, precisionCount)
        }
        if(tmp.size > 1) {
            return precisionFilter(sr, tmp, precisionCount.inc())
        }
        return tmp
    }
    
    /**
     * 精确过滤
     */
    private fun precisionFilter(sr: SystemCallRecord, cr: CallRecord, precisionCount: Int): Boolean {
        //重新计算时间偏移量
        val timeOffset = TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset - (TelephonyCenter.get().getRecordConfigure().minCallTimeOffset * precisionCount)
        val crStartTime = max(cr.callStartTime, cr.callOffHookTime)
        if(crStartTime > 0 && cr.callEndTime <= 0) { // 未能监听到结束时间的意外情况
            if(sr.duration > 0) {
                return sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
            }
        }
        val endTimeFilter = if(sr.duration > 0) { //（结束时长过滤器）内部数据库记录的通话结束时间 处于
            // 1.系统数据库通话记录 [开始时间+通话持续时长 +- 一分钟误差范围内]
            cr.callEndTime in (sr.date + sr.getDurationMillis() - timeOffset)..(sr.date + sr.getDurationMillis() + timeOffset)
                    // 2.系统数据库通话记录 [最后修改时间 +- 一分钟误差范围内]（部分机型有该值）
                    || (sr.lastModify > 0 && cr.callEndTime in (sr.lastModify - timeOffset)..(sr.lastModify + timeOffset))
        } else {
            //系统记录未接通,只判断开始时间
            return sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
        }
        val startTimeFilter = // 开始时长过滤器
            if(CallType.CallIn.ordinal == cr.callType) { // 呼入
                if(cr.callOffHookTime > 0) {
                    //当属于呼入类型且接通时开始时间以接通时间为准
                    var filter = sr.date in (cr.callOffHookTime - timeOffset)..(cr.callOffHookTime + timeOffset)
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
    private fun coarseFilter(
        sr: SystemCallRecord, cr: CallRecord, timeOffset: Long = TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset
    ): Boolean {
        return if(sr.phoneNumber.isEmpty() || cr.isFake) { // 号码未能正常获取，使用时间范围进行模糊过滤
            val crStartTime = max(cr.callStartTime, cr.callOffHookTime) // 获取通话开始时间
            val endTimeFilter = //（结束时长过滤器）内部数据库记录的通话结束时间 处于
                // 1.系统数据库通话记录 [开始时间+通话持续时长 +- 一分钟误差范围内]
                cr.callEndTime in (sr.date + sr.getDurationMillis() - timeOffset)..(sr.date + sr.getDurationMillis() + timeOffset)
                        // 2.系统数据库通话记录 [最后修改时间 +- 一分钟误差范围内]（部分机型有该值）
                        || (sr.lastModify > 0 && cr.callEndTime in (sr.lastModify - timeOffset)..(sr.lastModify + timeOffset))
            val startTimeFilter = // 开始时长过滤器
                if(CallType.CallIn.ordinal == cr.callType) { // 呼入
                    if(cr.callOffHookTime > 0) {
                        var filter = sr.date in (cr.callOffHookTime - timeOffset)..(cr.callOffHookTime + timeOffset)
                        if(!filter && endTimeFilter) {
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
            startTimeFilter && endTimeFilter
        } else {
            //号码过滤
            cr.phoneNumber == sr.phoneNumber
        }
    }
    
    /**
     * 同步指定的记录
     */
    private fun syncTargetRecord(work: Intent, recordId: String) {
        val recordCall = findRecordById(recordId) ?: return
        TelephonyCenter.get().libD("syncTargetRecord: $recordCall")
        
        val callLogClient = contentResolver.acquireUnstableContentProviderClient(CallLog.Calls.CONTENT_URI)
        if(null == callLogClient) {
            TelephonyCenter.get().libE("Can not load ${CallLog.Calls.CONTENT_URI} ContentProvider obj!!!")
            return
        }
        
        try {
            val selection = StringBuilder()
            selection.append(CallLog.Calls.DATE)
            selection.append(" ")
            selection.append("BETWEEN")
            selection.append(" ")
            selection.append("?")
            selection.append(" ")
            selection.append("AND")
            selection.append(" ")
            selection.append("?")
    
            val callStartTime = recordCall.callStartTime.minus(TelephonyCenter.get().getRecordConfigure().minCallTimeOffset)
            val callEndTime = if(recordCall.callEndTime <= 0L) {
                callStartTime + 1800000
            } else {
                recordCall.callEndTime.plus(TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset)
            }
            
            val args: ArrayList<String> = arrayListOf(
                callStartTime.toString(),
                callEndTime.toString()
            )

            if(!recordCall.isFake) {
                selection.append(" ")
                selection.append("and")
                selection.append(" ")
                selection.append(CallLog.Calls.NUMBER)
                selection.append("=?")
                
                args.add(recordCall.phoneNumber)
            }
            
            val sort = CallLog.Calls.DEFAULT_SORT_ORDER
    
            @SuppressLint("Recycle")
            // close by #parseSystemCallRecords
            val callLogResult = callLogClient.query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection.toString(),
                args.toArray(arrayOf<String>()),
                sort
            )
            TelephonyCenter.get().libCursor(callLogResult)
            
            callLogResult.parseSystemCallRecords(successAction = { systemRecords ->
                if(systemRecords.isNotEmpty()) {
                    //                    TelephonyCenter.get().libD("syncTargetRecord: recordCall -> $recordCall")
                    //                    syncRecordBySys(recordCall, systemRecords[0])
                    systemRecords.forEach sys@{ sr ->
                        if(coarseFilter(sr, recordCall)) {
                            val targetRecords = precisionFilter(sr, arrayListOf(recordCall))
                            when {
                                targetRecords.size == 1 -> {
                                    val target = targetRecords[0]
                                    syncRecordBySys(target, sr)
                                    TelephonyCenter.get().libD("syncTargetRecord: recordCall -> $target")
                                    return@parseSystemCallRecords
                                }
                
                                targetRecords.isEmpty() -> {
                                    //该条系统记录不能找到匹配的为同步记录,正常情况,因为没有一直监听
                                    TelephonyCenter.get().libW("syncTargetRecord: Ignored Sys record: $sr")
                                }
                            }
                        } else {
                            TelephonyCenter.get().libE("syncTargetRecord: Not found target record: $sr")
                        }
                    }
                }
                TelephonyCenter.get().libW("syncTargetRecord: Not found sys mapping record!! -> $recordCall")
                markNoMappingRecord(arrayListOf(recordCall))
                CallRecordController.get().retry(work)
            }, failAction = {
                TelephonyCenter.get().libW("syncTargetRecord: Not found any record with $selection from system db! -> $recordCall")
                markNoMappingRecord(arrayListOf(recordCall))
                CallRecordController.get().retry(work)
            })
        } catch(e: Exception) {
            TelephonyCenter.get().libE("syncTargetRecord", throwable = e)
            markNoMappingRecord(arrayListOf(recordCall))
            CallRecordController.get().retry(work)
        } finally {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                callLogClient.close()
            } else {
                @Suppress("DEPRECATION") callLogClient.release()
            }
        }
    }
    
    private fun syncRecordBySys(callRecord: CallRecord, systemCallRecord: SystemCallRecord) {
        TelephonyCenter.get().libD("syncRecordBySys: ${callRecord.isFake} -> $systemCallRecord")
        callRecord.callLogId = systemCallRecord.callId
        callRecord.callStartTime = systemCallRecord.date
        callRecord.duration = systemCallRecord.duration
        callRecord.callState = systemCallRecord.type
        callRecord.phoneAccountId = systemCallRecord.phoneAccountId
        if(callRecord.callEndTime <= 0) {
            callRecord.callEndTime = systemCallRecord.lastModify
            if(callRecord.callEndTime < callRecord.callStartTime) {
                callRecord.callEndTime = -1
            }
        }
        if(callRecord.isFake && systemCallRecord.phoneNumber.isNotEmpty()) {
            delete<FakeCallRecord> { equalTo("recordId", callRecord.recordId) }
            callRecord.isFake = false
            callRecord.phoneNumber = systemCallRecord.phoneNumber
        }
        callRecord.synced = true
        
        callRecord.recalculateDuration()
        
        callRecord.isDeleted = false
        callRecord.isNoMapping = false
        
        callRecord.save()
        
        RecordSyncNotifier.get().notifyRecordSyncStatus(arrayListOf(callRecord))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        TelephonyCenter.get().libW("All sync job is complete!")
    }
}
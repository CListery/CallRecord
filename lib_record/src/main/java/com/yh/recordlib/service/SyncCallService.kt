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
import com.yh.recordlib.BuildConfig
import com.yh.recordlib.CallRecordController
import com.yh.recordlib.cons.Constants
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.entity.FakeCallRecord
import com.yh.recordlib.entity.SystemCallRecord
import com.yh.recordlib.ext.findAllUnSyncRecords
import com.yh.recordlib.ext.findRecordById
import com.yh.recordlib.ext.parseSystemCallRecords
import com.yh.recordlib.notifier.RecordSyncNotifier
import timber.log.Timber
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
            Timber.w("enqueueWork1: $context - $recordId")
            if(null != recordId) {
                enqueueWork(context, Intent().putExtra(Constants.EXTRA_LAST_RECORD_ID, recordId))
            } else {
                enqueueWork(context)
            }
        }

        @JvmStatic
        fun enqueueWork(context: Context, work: Intent = Intent()) {
            Timber.w("enqueueWork2: $context - $work")
            enqueueWork(context, SyncCallService::class.java, JOB_ID, work)
        }
    }
    
    override fun onHandleWork(work: Intent) {
        if(work.hasExtra(Constants.EXTRA_RETRY)) {
            Timber.w("onHandleWork >>RETRY<< : $work")
        }
        Timber.w("onHandleWork 1: $work")
        val recordId = work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)
        Timber.d("onHandleWork 2: $recordId")
        
        if(null == recordId || TextUtils.isEmpty(recordId)) {
            syncAllRecord()
        } else {
            syncTargetRecord(work, recordId)
        }
    }
    
    private fun syncAllRecord() {
        val callLogClient = contentResolver.acquireUnstableContentProviderClient(CallLog.Calls.CONTENT_URI)
        if(null == callLogClient) {
            Timber.e("Can not load ${CallLog.Calls.CONTENT_URI} ContentProvider obj!!!")
            return
        }
        val tmpUnSyncRecords = findAllUnSyncRecords()
        Timber.d("syncAllRecord: tmpUnSyncRecords -> ${tmpUnSyncRecords?.toString()}")
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
        // 则取出异常记录的最大通话开始时间并将半小时之内的通话记录取出
        val allExceptionRecord = allUnSyncRecords.filter { it.callEndTime <= 0 }
        if(allExceptionRecord.isNotEmpty()) {
            val lastStartRecord = allExceptionRecord.maxBy { it.callStartTime }
            if(null != lastStartRecord) {
                lastCallEndTime = max(lastCallEndTime, lastStartRecord.callStartTime + 1800000)
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
                firstCallStartTime.minus(BuildConfig.MAX_CALL_TIME_OFFSET).toString(),
                lastCallEndTime.plus(BuildConfig.MAX_CALL_TIME_OFFSET).toString()
            )
            val sort = CallLog.Calls.DEFAULT_SORT_ORDER
            
            Timber.d("syncAllRecord: selection -> $selection ; args -> $args")
    
            @SuppressLint("Recycle")
            // close by #parseSystemCallRecords
            val callLogResult = callLogClient.query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection.toString(),
                args.toArray(arrayOf<String>()),
                sort
            )
            Timber.d("syncAllRecord: callLogResult -> ${callLogResult?.count}")
            
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
                                    Timber.d("syncAllRecord: recordCall -> $target")
                                    allUnSyncRecords.remove(target)
                                    syncedCount++
                                }
                                
                                targetRecords.isEmpty() -> {
                                    //该条系统记录不能找到匹配的为同步记录,正常情况,因为没有一直监听
                                    Timber.w("syncAllRecord: Ignored Sys record: $sr")
                                    return@sys
                                }
                                
                                else -> {
                                    //找到太多相似的记录
                                    Timber.e("syncAllRecord: Find too many similar records: $sr")
                                    return@sys
                                }
                            }
                        } else {
                            Timber.e("syncAllRecord: Not found target record: $sr")
                        }
                    }
                    if(allUnSyncRecords.isNotEmpty()) {
                        Timber.e("syncAllRecord: Failed to sync successfully: $allUnSyncRecords")
                        markNoMappingRecord(allUnSyncRecords)
                    }
                    Timber.w("syncAllRecord: $syncedCount records have been synchronized done!!")
                } else {
                    Timber.e("syncAllRecord: Not found sys mapping record!!")
                    markNoMappingRecord(allUnSyncRecords)
                }
            }, failAction = {
                Timber.e("syncAllRecord: Not found any CallRecord by ${CallLog.Calls.DATE} between $firstCallStartTime to $lastCallEndTime from system!!")
                markNoMappingRecord(allUnSyncRecords)
            })
        } catch(e: Exception) {
            Timber.e(e)
            markNoMappingRecord(allUnSyncRecords)
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
            if(System.currentTimeMillis() - it.callStartTime > 3600000) {
                //1小时未能成功同步,标记为删除状态
                it.isDeleted = true
            }
            it.isNoMapping = true
            it.save()
        }
        
        RecordSyncNotifier.get().notifyRecordSyncStatus(allUnSyncRecords)
    }
    
    private fun precisionFilter(
        sr: SystemCallRecord, crs: List<CallRecord>, precisionCount: Int = 0
    ): List<CallRecord> {
        if(precisionCount > BuildConfig.MAX_CALL_TIME_OFFSET / BuildConfig.MIN_CALL_TIME_OFFSET) {
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
    private fun precisionFilter(
        sr: SystemCallRecord, cr: CallRecord, precisionCount: Int
    ): Boolean {
        //重新计算时间偏移量
        val timeOffset = BuildConfig.MAX_CALL_TIME_OFFSET - (BuildConfig.MIN_CALL_TIME_OFFSET * precisionCount)
        val crStartTime = max(cr.callStartTime, cr.callOffHookTime)
        if(crStartTime > 0 && cr.callEndTime <= 0) {
            //意外情况
            if(sr.duration > 0) {
                return sr.date == crStartTime || sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
            }
        }
        val endTimeFilter = if(sr.duration > 0) {
            //系统库中有接通时长时,系统开始时间+接通时长=数据库中结束时间
            cr.callEndTime in (sr.date + sr.getDurationMillis() - timeOffset)..(sr.date + sr.getDurationMillis() + timeOffset) || (sr.lastModify > 0 && cr.callEndTime == sr.lastModify)
        } else {
            //系统记录未接通,只判断开始时间
            return sr.date == crStartTime || sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
        }
        val startTimeFilter = if(CallType.CallIn.ordinal == cr.callType && cr.callOffHookTime > 0) {
            //当属于呼入类型且接通时开始时间以接通时间为准
            var filter = sr.date in (cr.callOffHookTime - timeOffset)..(cr.callOffHookTime + timeOffset)
            if(!filter && endTimeFilter) {
                //如果某些机型系统数据库中开始时间不是以接通时间,则使用开始时间为准
                filter = sr.date == crStartTime || sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
            }
            filter
        } else {
            //系统开始时间=数据库中开始时间
            sr.date == crStartTime || sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
        }
        return startTimeFilter && endTimeFilter
    }
    
    /**
     * 粗略过滤
     * 1. 未能获取到号码的情况使用时间范围过滤
     * 2. 号码相同的情况直接过滤
     */
    private fun coarseFilter(
        sr: SystemCallRecord, cr: CallRecord, timeOffset: Long = BuildConfig.MAX_CALL_TIME_OFFSET
    ): Boolean {
        return if(sr.phoneNumber.isEmpty() || cr.isFake) {
            //时间过滤
            val crStartTime = max(cr.callStartTime, cr.callOffHookTime)
            val endTimeFilter = cr.callEndTime in (sr.date + sr.getDurationMillis() - timeOffset)..(sr.date + sr.getDurationMillis() + timeOffset) || (sr.lastModify > 0 && cr.callEndTime == sr.lastModify)
            val startTimeFilter = if(CallType.CallIn.ordinal == cr.callType && cr.callOffHookTime > 0) {
                var filter = sr.date in (cr.callOffHookTime - timeOffset)..(cr.callOffHookTime + timeOffset)
                if(!filter && endTimeFilter) {
                    filter = sr.date == crStartTime || sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
                }
                filter
            } else {
                sr.date == crStartTime || sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
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
        Timber.d("syncTargetRecord: $recordCall")
        
        val callLogClient = contentResolver.acquireUnstableContentProviderClient(CallLog.Calls.CONTENT_URI)
        if(null == callLogClient) {
            Timber.e("Can not load ${CallLog.Calls.CONTENT_URI} ContentProvider obj!!!")
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

            val args: ArrayList<String> = arrayListOf(
                recordCall.callStartTime.minus(BuildConfig.MAX_CALL_TIME_OFFSET).toString(),
                recordCall.callEndTime.plus(BuildConfig.MAX_CALL_TIME_OFFSET).toString()
            )

            if(!recordCall.isFake) {
                selection.append(" ")
                selection.append("and")
                selection.append(" ")
                selection.append(CallLog.Calls.NUMBER)
                selection.append("=?")
                
                args.add(recordCall.phoneNumber)
            }
            
            val sort = "${CallLog.Calls.DEFAULT_SORT_ORDER} LIMIT 1"
    
            @SuppressLint("Recycle")
            // close by #parseSystemCallRecords
            val callLogResult = callLogClient.query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection.toString(),
                args.toArray(arrayOf<String>()),
                sort
            )
            Timber.d("syncTargetRecord: callLogResult -> $callLogResult")
            
            callLogResult.parseSystemCallRecords(successAction = { records ->
                if(records.isNotEmpty()) {
                    Timber.d("syncTargetRecord: recordCall -> $recordCall")
                    syncRecordBySys(recordCall, records[0])
                } else {
                    Timber.w("syncTargetRecord: Not found sys mapping record!! -> $recordCall")
                    markNoMappingRecord(arrayListOf(recordCall))
                    CallRecordController.get()
                        .retry(work)
                }
            }, failAction = {
                Timber.w("syncTargetRecord: Not found any record with $selection from system db! -> $recordCall")
                markNoMappingRecord(arrayListOf(recordCall))
                CallRecordController.get()
                    .retry(work)
            })
        } catch(e: Exception) {
            Timber.e(e)
            markNoMappingRecord(arrayListOf(recordCall))
        } finally {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                callLogClient.close()
            } else {
                @Suppress("DEPRECATION") callLogClient.release()
            }
        }
    }
    
    private fun syncRecordBySys(callRecord: CallRecord, systemCallRecord: SystemCallRecord) {
        Timber.d("syncRecordBySys: ${callRecord.isFake} -> $systemCallRecord")
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
        Timber.w("All sync job is complete!")
    }
}
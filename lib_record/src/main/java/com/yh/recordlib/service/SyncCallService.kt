package com.yh.recordlib.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import android.text.TextUtils
import android.util.Log
import androidx.core.app.SafeJobIntentService
import androidx.core.content.PermissionChecker
import com.yh.appinject.logger.LibLogs
import com.yh.appinject.logger.ext.libCursor
import com.yh.appinject.logger.ext.libD
import com.yh.appinject.logger.ext.libE
import com.yh.appinject.logger.ext.libP
import com.yh.appinject.logger.ext.libW
import com.yh.appinject.logger.impl.TheLogAdapter
import com.yh.krealmextensions.saveAll
import com.yh.recordlib.BuildConfig
import com.yh.recordlib.CallRecordController
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.cons.Constants
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.SystemCallRecord
import com.yh.recordlib.ext.findAllUnSyncRecords
import com.yh.recordlib.ext.findRecordById
import com.yh.recordlib.ext.parseSystemCallRecords
import com.yh.recordlib.log.ManualSyncLogFormatStrategy
import com.yh.recordlib.notifier.RecordSyncNotifier
import com.yh.recordlib.utils.DeviceUtils
import com.yh.recordlib.utils.RecordFilter.findMappingRecords
import kotlin.math.max

/**
 * Created by CYH on 2019-05-30 15:50
 */
class SyncCallService : SafeJobIntentService() {
    
    companion object {
        
        /**
         * Unique job ID for this service.
         */
        private const val JOB_ID = 10001
        
        const val SYNC_ALL_RECORD_ID = "sync_all_record_id"
        
        @JvmStatic
        fun makeSyncWork(recordId: String): Intent {
            val work = Intent()
            work.putExtra(Constants.EXTRA_LAST_RECORD_ID, recordId)
            return work
        }
        
        @JvmStatic
        fun enqueueWorkById(context: Context, recordId: String) {
            TelephonyCenter.get().libW("enqueueWorkById: $context - $recordId")
            if(recordId.isEmpty()) {
                throw IllegalArgumentException("recordId can not be EMPTY!!!")
            }
            enqueueWork(context, makeSyncWork(recordId))
        }
        
        @JvmStatic
        fun enqueueWork(context: Context, work: Intent) {
            TelephonyCenter.get().libW("enqueueWork: $context - $work")
            val recordId = work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)
            if(recordId.isNullOrEmpty()) {
                throw IllegalArgumentException("recordId can not be EMPTY!!!")
            }
            enqueueWork(context, SyncCallService::class.java, JOB_ID, work)
        }
    }
    
    private var isManualSync: Boolean = false
    private var manualSyncLogAdapter: TheLogAdapter? = null
    private var strategy: ManualSyncLogFormatStrategy? = null
    
    override fun onHandleWork(work: Intent) {
        isManualSync = work.getBooleanExtra(Constants.EXTRA_IS_MANUAL_SYNC, false)
        if(isManualSync) {
            val logDir = work.getStringExtra(Constants.EXTRA_LOG_DIR)
                ?: cacheDir.absolutePath
            val logFileName = work.getStringExtra(Constants.EXTRA_LOG_FILE_NAME)
                ?: "msr"
            val logFormatStrategy = ManualSyncLogFormatStrategy.Builder()
                .logDir(logDir)
                .logFileName(logFileName)
                .build()
            strategy = logFormatStrategy
            manualSyncLogAdapter =
                TheLogAdapter(logFormatStrategy).apply { setConfig(true to Log.VERBOSE) }
        }
        val recordId = work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)
        printLog(Log.WARN, "onHandleWork: id:$recordId, manual:$isManualSync")
        if(!hasCallLogPermission()) {
            printLog(Log.ERROR, "No permission operator call_log!")
            return
        }
        if(recordId.isNullOrEmpty() || SYNC_ALL_RECORD_ID == recordId) {
            syncAllRecord()
        } else {
            syncTargetRecord(work, recordId)
        }
        printLog(Log.WARN, "All sync job is complete!")
        printEnd()
    }
    
    private fun hasCallLogPermission(): Boolean {
        return PermissionChecker.PERMISSION_GRANTED == PermissionChecker.checkSelfPermission(
            applicationContext,
            Manifest.permission.READ_CALL_LOG
        )
    }
    
    private fun syncAllRecord() {
        val callLogClient = contentResolver.acquireUnstableContentProviderClient(CallLog.Calls.CONTENT_URI)
        if(null == callLogClient) {
            TelephonyCenter.get().libE("Can not load ${CallLog.Calls.CONTENT_URI} ContentProvider obj!!!")
            return
        }
        
        val allUnSyncRecords = findAllUnSyncRecords(TelephonyCenter.get().getRecordConfigure().syncTimeOffset)
        TelephonyCenter.get().libD("syncAllRecord: allUnSyncRecords -> $allUnSyncRecords")
        if(allUnSyncRecords.isEmpty()) {
            return
        }
        
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
            val lastStartRecord = allExceptionRecord.maxByOrNull { it.callStartTime }
            if(null != lastStartRecord) {
                if(lastStartRecord.callStartTime > lastCallEndTime) {
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
                firstCallStartTime.minus(TelephonyCenter.get().getRecordConfigure().startTimeOffset).toString(),
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
            printCursor(callLogResult, true)
            
            callLogResult.parseSystemCallRecords(successAction = { systemRecords ->
                printLog(Log.INFO, systemRecords.joinToString("\n") { "$it" })
                if(systemRecords.isNotEmpty()) {
                    val recordMappingInfo = findMappingRecords(systemRecords, allUnSyncRecords)
                    
                    val finalAllUnSyncRecords = recordMappingInfo.noMappingRecords
                    
                    if(recordMappingInfo.unUseSystemCallRecords.isNotEmpty()) {
                        TelephonyCenter.get().libW("syncAllRecord: Ignored Sys records: ${recordMappingInfo.unUseSystemCallRecords}")
                    }
                    
                    if(recordMappingInfo.mappingRecords.isNotEmpty()) {
                        TelephonyCenter.get().libW("syncAllRecord: synced records: ${recordMappingInfo.mappingRecords}")
                        syncRecordBySys(recordMappingInfo.mappingRecords)
                    }
                    
                    if(finalAllUnSyncRecords.isNotEmpty()) {
                        TelephonyCenter.get().libE("syncAllRecord: Failed to sync successfully: $finalAllUnSyncRecords")
                        markUnSyncAndRetry(finalAllUnSyncRecords)
                    }
                } else {
                    if(allUnSyncRecords.isNotEmpty()) {
                        TelephonyCenter.get().libE("syncAllRecord: Not found sys mapping record!!")
                        markUnSyncAndRetry(allUnSyncRecords)
                    }
                }
            }, failAction = {
                if(allUnSyncRecords.isNotEmpty()) {
                    TelephonyCenter.get().libE("syncAllRecord: Not found any CallRecord by ${CallLog.Calls.DATE} between $firstCallStartTime to $lastCallEndTime from system!!")
                    markUnSyncAndRetry(allUnSyncRecords)
                }
            })
        } catch(e: Exception) {
            if(allUnSyncRecords.isNotEmpty()) {
                TelephonyCenter.get().libE("syncAllRecord", throwable = e)
                markUnSyncAndRetry(allUnSyncRecords)
            }
        } finally {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                callLogClient.close()
            } else {
                @Suppress("DEPRECATION") callLogClient.release()
            }
        }
    }
    
    /**
     * 同步指定的记录
     */
    private fun syncTargetRecord(work: Intent, recordId: String) {
        val recordCall = findRecordById(recordId)
        printLog(Log.DEBUG, "syncTargetRecord: $recordCall")
        if(null == recordCall) {
            return
        }
        if(recordCall.synced || (!isManualSync && recordCall.isDeleted)) {
            printLog(
                Log.WARN, "syncTargetRecord: $recordId has been"
                    .plus(if(recordCall.synced) " synced " else "")
                    .plus(if(isManualSync) " manual " else "")
                    .plus(if(recordCall.isDeleted) " deleted " else "")
                    .plus(", sync count: ${recordCall.syncCount}")
            )
            return
        }
        
        val callLogClient = contentResolver.acquireUnstableContentProviderClient(CallLog.Calls.CONTENT_URI)
        if(null == callLogClient) {
            printLog(Log.ERROR, "Can not load ${CallLog.Calls.CONTENT_URI} ContentProvider obj!!!")
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
            
            val callStartTime = recordCall.callStartTime.minus(
                TelephonyCenter.get().getRecordConfigure().startTimeOffset
            )
            val callEndTime = if(recordCall.callEndTime <= 0L) {
                callStartTime + 1800000
            } else {
                recordCall.callEndTime.plus(
                    TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset
                )
            }
            
            val args: ArrayList<String> = arrayListOf(
                callStartTime.toString(),
                callEndTime.toString()
            )
            
            selection.append(" ")
            selection.append("and")
            selection.append(" ")
            selection.append(CallLog.Calls.NUMBER)
            selection.append("=?")
            
            args.add(recordCall.phoneNumber)
            
            val sort = CallLog.Calls.DEFAULT_SORT_ORDER
            
            try {
                printLog(
                    Log.DEBUG,
                    "selection: ${selection.toString().replace("?", "%s").format(*args.toArray())} $sort"
                )
            } catch(e: Exception) {
            }
            
            @SuppressLint("Recycle")
            // close by #parseSystemCallRecords
            val callLogResult = callLogClient.query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection.toString(),
                args.toArray(arrayOf<String>()),
                sort
            )
            
            callLogResult.parseSystemCallRecords(successAction = { systemRecords ->
                printLog(Log.INFO, systemRecords.mapIndexed { index, systemCallRecord -> "| $index $systemCallRecord" }.joinToString("\n"))
                if(systemRecords.isNotEmpty()) {
                    if(systemRecords.size == 1) {
                        // 只有一条记录的情况直接进行同步
                        syncRecordBySys(mapOf(systemRecords.first() to recordCall))
                        printLog(Log.DEBUG, "syncTargetRecord: just find single Sys record, sync this!")
                        return@parseSystemCallRecords
                    }
                    val recordMappingInfo = findMappingRecords(systemRecords, listOf(recordCall))
                    
                    if(recordMappingInfo.unUseSystemCallRecords.isNotEmpty()) {
                        printLog(Log.WARN, "syncTargetRecord: Ignored Sys records: ${recordMappingInfo.unUseSystemCallRecords}")
                    }
                    
                    if(recordMappingInfo.mappingRecords.isNotEmpty()) {
                        printLog(Log.DEBUG, "syncTargetRecord: synced records: ${recordMappingInfo.mappingRecords}")
                        syncRecordBySys(recordMappingInfo.mappingRecords)
                    }
                    
                    if(recordMappingInfo.noMappingRecords.isNotEmpty()) {
                        printLog(Log.ERROR, "syncAllRecord: not found mapping records: ${recordMappingInfo.noMappingRecords}")
                        markNoMappingRecord(recordMappingInfo.noMappingRecords)
                        if(isManualSync) {
                            submitSysRecords(callLogClient, recordCall)
                        } else {
                            CallRecordController.get().retry(work)
                        }
                    }
                } else {
                    printLog(Log.WARN, "syncTargetRecord: Not found sys mapping record!! -> $recordCall")
                    markNoMappingRecord(listOf(recordCall))
                    if(isManualSync) {
                        submitSysRecords(callLogClient, recordCall)
                    } else {
                        CallRecordController.get().retry(work)
                    }
                }
            }, failAction = {
                printLog(Log.WARN, "syncTargetRecord: Not found any record with $selection from system db! -> $recordCall")
                markNoMappingRecord(arrayListOf(recordCall))
                if(isManualSync) {
                    submitSysRecords(callLogClient, recordCall)
                } else {
                    CallRecordController.get().retry(work)
                }
            })
        } catch(e: Exception) {
            printLog(Log.ERROR, "syncTargetRecord", throwable = e)
            markNoMappingRecord(arrayListOf(recordCall))
            if(isManualSync) {
                submitSysRecords(callLogClient, recordCall)
            } else {
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
    
    private fun submitSysRecords(callLogClient: ContentProviderClient, recordCall: CallRecord) {
        printLog(Log.INFO, "==============[BEGIN SYS RECORDS]==============")
        
        printLog(Log.INFO, "|| [CALL RECORD]")
        printLog(Log.INFO, recordCall)
        
        if(!TextUtils.isEmpty(recordCall.phoneNumber)) {
            printLog(Log.INFO, "|| callRecord phone: ${recordCall.phoneNumber}")
        }
        
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
    
        val callStartTime = recordCall.callStartTime.minus(600000)
        val callEndTime = if(recordCall.callEndTime <= 0L) {
            callStartTime + 1800000
        } else {
            recordCall.callEndTime.plus(600000)
        }
        
        val args: ArrayList<String> = arrayListOf(
            callStartTime.toString(),
            callEndTime.toString()
        )
        
        val sort = CallLog.Calls.DEFAULT_SORT_ORDER
        
        try {
            printLog(Log.INFO, "|| [SELECTION]")
            printLog(
                Log.DEBUG,
                "selection: ${selection.toString().replace("?", "%s").format(*args.toArray())} $sort"
            )
        } catch(e: Exception) {
            printLog(Log.ERROR, "submitSysRecords", e)
        }
        
        try {
            printLog(Log.INFO, "|| [SYS RECORDS]")
            val systemRecords = callLogClient.query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection.toString(),
                args.toArray(arrayOf<String>()),
                sort
            )
            printCursor(systemRecords, true)
            printLog(Log.DEBUG, "----------------------------------------")
            systemRecords.parseSystemCallRecords({
                printLog(Log.INFO, it.mapIndexed { index, systemCallRecord -> "| $index $systemCallRecord" }.joinToString("\n"))
            }, {
                printCursor(systemRecords)
            })
        } catch(e: Exception) {
            printLog(Log.ERROR, "submitSysRecords", e)
        }
        
        printLog(Log.INFO, "===============[END SYS RECORDS]===============")
    }
    
    private fun printOtherInfo() {
        printConfigInfo()
        printMobileInfo()
        printDeviceInfo()
    }
    
    private fun printConfigInfo() {
        printLog(Log.INFO, "==============[START CONFIGURE INFO]==============")
        try {
            val configure = TelephonyCenter.get().getRecordConfigure()
            printLog(Log.INFO, "|| CTX: ${configure.ctx}")
            printLog(Log.INFO, "|| INIT_SYNC: ${configure.needInitSync}")
            printLog(Log.INFO, "|| DB_DIR: ${configure.dbFileDirName.invoke()}")
            printLog(
                Log.INFO,
                "|| DB_VER_LIB: ${BuildConfig.RECORD_DB_VERSION}"
            )
            printLog(
                Log.INFO,
                "|| DB_VER_APP: ${configure.dbVersion}"
            )
            printLog(Log.INFO, "|| SYNC_RETRY_TIME: ${(configure.syncRetryTime / 1000)}s")
            printLog(Log.INFO, "|| MAX_RETRY_COUNT: ${configure.maxRetryCount}")
            printLog(Log.INFO, "|| START_OFFSET: ${(configure.startTimeOffset / 1000)}s")
            printLog(Log.INFO, "|| MAX_OFFSET: ${(configure.maxCallTimeOffset / 1000)}s")
            printLog(Log.INFO, "|| MIN_OFFSET: ${(configure.minCallTimeOffset / 1000)}s")
        } catch(e: Exception) {
            printLog(Log.ERROR, "printConfigInfo", throwable = e)
        }
        printLog(Log.INFO, "===============[END CONFIGURE INFO]===============")
    }
    
    private fun printMobileInfo() {
        printLog(Log.INFO, "==============[START MOBILE INFO]==============")
        try {
            printLog(Log.INFO, "|| ALL_OPERATOR: ${TelephonyCenter.get().getAllSimOperator()}")
            printLog(Log.INFO, "|| MULTI_CONFIG: ${TelephonyCenter.get().getMultiSimConfiguration().name}")
            printLog(Log.INFO, "|| PN0: ${TelephonyCenter.get().getPhoneNumber()}")
            printLog(Log.INFO, "|| PN1: ${TelephonyCenter.get().getPhoneNumber(1)}")
            printLog(Log.INFO, "|| PN2: ${TelephonyCenter.get().getPhoneNumber(2)}")
            printLog(Log.INFO, "|| ISN0: ${TelephonyCenter.get().getIccSerialNumber()}")
            printLog(Log.INFO, "|| ISN1: ${TelephonyCenter.get().getIccSerialNumber(1)}")
            printLog(Log.INFO, "|| ISN2: ${TelephonyCenter.get().getIccSerialNumber(2)}")
        } catch(e: Exception) {
            printLog(Log.ERROR, "printMobileInfo", throwable = e)
        }
        printLog(Log.INFO, "===============[END MOBILE INFO]===============")
    }
    
    private fun printDeviceInfo() {
        printLog(Log.INFO, "==============[START DEVICE INFO]==============")
        try {
            printLog(Log.INFO, "|| MANUFACTURER: ${Build.MANUFACTURER}")
            printLog(Log.INFO, "|| DEVICE: ${Build.DEVICE}")
            printLog(Log.INFO, "|| PRODUCT: ${Build.PRODUCT}")
            printLog(Log.INFO, "|| MODEL: ${Build.MODEL}")
            printLog(Log.INFO, "|| HARDWARE: ${Build.HARDWARE}")
            printLog(Log.INFO, "|| RELEASE: ${Build.VERSION.RELEASE}")
            printLog(Log.INFO, "|| ROOTED: ${DeviceUtils.isDeviceRooted()}")
            DeviceUtils.getMemoryInfo().forEach {
                printLog(Log.INFO, "|| $it")
            }
            DeviceUtils.getExternalAndInternalStorageInfo().forEach {
                printLog(Log.INFO, "|| $it")
            }
        } catch(e: Exception) {
            printLog(Log.ERROR, "printDeviceInfo", throwable = e)
        }
        printLog(Log.INFO, "===============[END DEVICE INFO]===============")
    }
    
    private fun printEnd() {
        if(isManualSync) {
            printOtherInfo()
        }
        manualSyncLogAdapter?.release()
        RecordSyncNotifier.get().notifyManualSyncDone(strategy?.getRealLogFile())
    }
    
    private fun printCursor(cursor: Cursor?, justCurRow: Boolean = false) {
        if(isManualSync && null != manualSyncLogAdapter) {
            LibLogs.logCursor(cursor, justCurRow, logAdapter = manualSyncLogAdapter)
        } else {
            TelephonyCenter.get().libCursor(cursor, justCurRow)
        }
    }
    
    private fun printLog(priority: Int = Log.DEBUG, obj: Any?, throwable: Throwable? = null) {
        if(obj is Cursor?) {
            printCursor(obj)
            return
        }
        if(isManualSync && null != manualSyncLogAdapter) {
            LibLogs.logP(priority, obj, logAdapter = manualSyncLogAdapter, throwable = throwable)
        } else {
            TelephonyCenter.get().libP(priority, obj, throwable = throwable)
        }
    }
    
    private fun markUnSyncAndRetry(finalAllUnSyncRecords: List<CallRecord>) {
        markNoMappingRecord(finalAllUnSyncRecords)
        finalAllUnSyncRecords.map { makeSyncWork(it.recordId) }.forEachIndexed { index, work ->
            CallRecordController.get().retry(work, CallRecordController.get().syncRetryTime + (index * 200))
        }
    }
    
    private fun markNoMappingRecord(allUnSyncRecords: List<CallRecord>) {
        allUnSyncRecords.forEach {
            it.syncCount++
            if(isManualSync) {
                it.isManualSynced = true
            }
            if(it.isManualSynced && System.currentTimeMillis() - it.callStartTime > 7200000) {
                //已经手动同步过，且2小时未能成功同步,标记为删除状态
                it.isDeleted = true
            }
            it.isNoMapping = true
        }
        val callRecords = allUnSyncRecords.toList()
        callRecords.saveAll()
        RecordSyncNotifier.get().notifyRecordSyncStatus(callRecords)
    }
    
    private fun syncRecordBySys(mappingRecords: Map<SystemCallRecord, CallRecord>) {
        if(mappingRecords.isEmpty()) {
            printLog(Log.ERROR, "syncRecordBySys: mappingRecords is empty!")
            return
        }
        mappingRecords.forEach { mr ->
            val systemCallRecord = mr.key
            val callRecord = mr.value
            printLog(Log.DEBUG, "syncRecordBySys: $systemCallRecord")
            val originStartTime = max(callRecord.callStartTime, callRecord.callOffHookTime)
            callRecord.syncCount++
            callRecord.callLogId = systemCallRecord.callId
            callRecord.callStartTime = systemCallRecord.date
            callRecord.duration = systemCallRecord.duration
            callRecord.callState = systemCallRecord.type
            callRecord.phoneAccountId = systemCallRecord.phoneAccountId
            callRecord.recalculateTime()
            callRecord.synced = true
            if(isManualSync) {
                callRecord.isManualSynced = true
            }
            
            callRecord.recalculateDuration(originStartTime, systemCallRecord)
            
            callRecord.isDeleted = false
            callRecord.isNoMapping = false
        }
        val callRecords = mappingRecords.values.toList()
        callRecords.saveAll()
        RecordSyncNotifier.get().notifyRecordSyncStatus(callRecords)
    }
    
}
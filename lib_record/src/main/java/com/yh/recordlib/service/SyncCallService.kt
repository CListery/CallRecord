package com.yh.recordlib.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CallLog
import android.text.TextUtils
import androidx.core.app.SafeJobIntentService
import androidx.core.content.PermissionChecker
import com.kotlin.runCatchingSafety
import com.yh.appbasic.logger.*
import com.yh.appbasic.logger.impl.DiskLogFormatStrategy
import com.yh.appbasic.share.AppBasicShare
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
        
        /**
         * 用于优化查询结果的项，优先级从低到高
         */
        private val optimizationProjections = arrayOf(
            "formatted_number",
            "matched_number",
            "sub_id",
            "geocoded_location",
            "last_modified",
            "simid",
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        
        @JvmStatic
        fun makeSyncWork(recordId: String): Intent {
            val work = Intent()
            work.putExtra(Constants.EXTRA_LAST_RECORD_ID, recordId)
            return work
        }
        
        @JvmStatic
        fun enqueueWorkById(context: Context, recordId: String) {
            logW("enqueueWorkById: $context - $recordId", loggable = TelephonyCenter.get())
            if (recordId.isEmpty()) {
                throw IllegalArgumentException("recordId can not be EMPTY!!!")
            }
            enqueueWork(context, makeSyncWork(recordId))
        }
        
        @JvmStatic
        fun enqueueWork(context: Context, work: Intent) {
            logW("enqueueWork: $context - $work", loggable = TelephonyCenter.get())
            val recordId = work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)
            if (recordId.isNullOrEmpty()) {
                throw IllegalArgumentException("recordId can not be EMPTY!!!")
            }
            enqueueWork(context, SyncCallService::class.java, JOB_ID, work)
        }
    }
    
    private var isManualSync: Boolean = false
    private var notLogout: Boolean = false
    
    private val isManualSyncLoggable get() = isManualSync && !notLogout
    
    private val manualSyncLogOwner by lazy { LogOwner { "" } }
    private val loggable: Any get() = if (isManualSyncLoggable) manualSyncLogOwner else TelephonyCenter.get()
    
    override fun onHandleWork(work: Intent) {
        isManualSync = work.getBooleanExtra(Constants.EXTRA_IS_MANUAL_SYNC, false)
        notLogout = work.getBooleanExtra(Constants.EXTRA_NOT_LOGFILE, false)
        val recordId = work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID) ?: SYNC_ALL_RECORD_ID
        if (isManualSyncLoggable) {
            manualSyncLogOwner.onCreateFormatStrategy {
                DiskLogFormatStrategy.Builder(AppBasicShare.context, "MSL")
                    .build()
            }
        }
        logW("onHandleWork: id:$recordId, manual:$isManualSync", loggable = loggable)
        if (!hasCallLogPermission()) {
            logE("No permission operator call_log!", loggable = loggable)
            printEnd()
            return
        }
        if (null == TelephonyCenter.get().callsProjections) {
            initProjections()
        }
        if (recordId.isEmpty() || SYNC_ALL_RECORD_ID == recordId) {
            syncAllRecord()
        } else {
            syncTargetRecord(work, recordId)
        }
        logW("All sync job is complete!", loggable = loggable)
        printEnd()
    }
    
    private fun initProjections() {
        AppBasicShare.context.runCatchingSafety {
            contentResolver.acquireUnstableContentProviderClient(CallLog.Calls.CONTENT_URI)
                ?.use { callLogClient ->
                    val sort = CallLog.Calls.DEFAULT_SORT_ORDER
                    
                    val tmpProjections: ArrayList<String> = arrayListOf(
                        CallLog.Calls._ID,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.NUMBER,
                    )
                    tmpProjections.addAll(optimizationProjections)
    
                    fun removeProjection(columnName: String): Boolean {
                        return tmpProjections.removeAll { it.equals(columnName, true) }
                    }
    
                    fun ContentProviderClient.checkerProjections(projections: Array<String>? = null): Array<String>? {
                        try {
                            query(
                                CallLog.Calls.CONTENT_URI.buildUpon()
                                    .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1")
                                    .build(),
                                projections,
                                null,
                                null,
                                sort
                            ).use {
                                return projections
                            }
                        } catch (e: Exception) {
                            logE("initProjections", throwable = e, loggable = loggable)
                            if (null == projections) {
                                return checkerProjections(tmpProjections.toTypedArray())
                            } else {
                                val message = e.message
                                if (!message.isNullOrEmpty()) {
                                    val columnName = if (message.startsWith("Invalid column ")) {
                                        message.replace("Invalid column ", "")
                                    } else if (message.startsWith("no such column: ")) {
                                        message.replace("no such column: ", "")
                                            .split(" ")
                                            .firstOrNull()
                                    } else {
                                        null
                                    }
                                    if (!columnName.isNullOrEmpty()) {
                                        if (removeProjection(columnName)) {
                                            return checkerProjections(tmpProjections.toTypedArray())
                                        }
                                    }
                                }
                                for (op in optimizationProjections) {
                                    if (removeProjection(op)) {
                                        return checkerProjections(tmpProjections.toTypedArray())
                                    }
                                }
                                return null
                            }
                        }
                    }
    
                    TelephonyCenter.get().callsProjections =
                        callLogClient.checkerProjections() ?: emptyArray()
                }
        }
    }
    
    private fun hasCallLogPermission(): Boolean {
        return PermissionChecker.PERMISSION_GRANTED == PermissionChecker.checkSelfPermission(
            applicationContext,
            Manifest.permission.READ_CALL_LOG
        )
    }
    
    private fun syncAllRecord() {
        val callLogClient =
            contentResolver.acquireUnstableContentProviderClient(CallLog.Calls.CONTENT_URI)
        if (null == callLogClient) {
            logE("Can not load ${CallLog.Calls.CONTENT_URI} ContentProvider obj!!!",
                loggable = loggable)
            return
        }
        
        val allUnSyncRecords =
            findAllUnSyncRecords(TelephonyCenter.get().getRecordConfigure().syncTimeOffset,
                TelephonyCenter.get().getRecordConfigure().maxRetryCount)
        logD("syncAllRecord: allUnSyncRecords -> $allUnSyncRecords", loggable = loggable)
        if (allUnSyncRecords.isEmpty()) {
            return
        }
        
        var firstCallStartTime: Long = System.currentTimeMillis()
        var lastCallEndTime = 0L
        allUnSyncRecords.forEach { record ->
            if (record.callStartTime in 1 until firstCallStartTime) {
                firstCallStartTime = record.callStartTime
            }
            if (record.callEndTime > lastCallEndTime) {
                lastCallEndTime = record.callEndTime
            }
        }
        // 在监听过程中 APP 崩溃或被系统强制杀死时将监听不到通话结束时间
        // 如异常通话记录的通话开始时间大于所有正常通话记录的结束时间，则以最后一条异常记录的开始时间+30分钟作为最后一条通话记录的结束时间
        val allExceptionRecord = allUnSyncRecords.filter { it.callEndTime <= 0 }
        if (allExceptionRecord.isNotEmpty()) {
            val lastStartRecord = allExceptionRecord.maxByOrNull { it.callStartTime }
            if (null != lastStartRecord) {
                if (lastStartRecord.callStartTime > lastCallEndTime) {
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
                firstCallStartTime.minus(TelephonyCenter.get().getRecordConfigure().startTimeOffset)
                    .toString(),
                lastCallEndTime.plus(TelephonyCenter.get().getRecordConfigure().maxCallTimeOffset)
                    .toString()
            )
            val sort = CallLog.Calls.DEFAULT_SORT_ORDER
            
            logD("syncAllRecord: selection -> $selection ; args -> $args", loggable = loggable)
            
            @SuppressLint("Recycle")
            // close by #parseSystemCallRecords
            val callLogResult = callLogClient.query(
                CallLog.Calls.CONTENT_URI,
                TelephonyCenter.get().safeProjections(),
                selection.toString(),
                args.toArray(arrayOf<String>()),
                sort
            )
            logCursor(callLogResult, true, loggable = loggable)
            
            callLogResult.parseSystemCallRecords(successAction = { systemRecords ->
                logI(systemRecords.joinToString("\n") { "$it" }, loggable = loggable)
                if (systemRecords.isNotEmpty()) {
                    val recordMappingInfo = findMappingRecords(systemRecords, allUnSyncRecords)
                    
                    val finalAllUnSyncRecords = recordMappingInfo.noMappingRecords
                    
                    if (recordMappingInfo.unUseSystemCallRecords.isNotEmpty()) {
                        logW("syncAllRecord: Ignored Sys records: ${recordMappingInfo.unUseSystemCallRecords}",
                            loggable = loggable)
                    }
                    
                    if (recordMappingInfo.mappingRecords.isNotEmpty()) {
                        logW("syncAllRecord: synced records: ${recordMappingInfo.mappingRecords}",
                            loggable = loggable)
                        syncRecordBySys(recordMappingInfo.mappingRecords)
                    }
                    
                    if (finalAllUnSyncRecords.isNotEmpty()) {
                        logE("syncAllRecord: Failed to sync successfully: $finalAllUnSyncRecords",
                            loggable = loggable)
                        markUnSyncAndRetry(finalAllUnSyncRecords)
                    }
                } else {
                    if (allUnSyncRecords.isNotEmpty()) {
                        logE("syncAllRecord: Not found sys mapping record!!", loggable = loggable)
                        markUnSyncAndRetry(allUnSyncRecords)
                    }
                }
            }, failAction = {
                if (allUnSyncRecords.isNotEmpty()) {
                    logE("syncAllRecord: Not found any CallRecord by ${CallLog.Calls.DATE} between $firstCallStartTime to $lastCallEndTime from system!!",
                        loggable = loggable)
                    markUnSyncAndRetry(allUnSyncRecords)
                }
            })
        } catch (e: Exception) {
            if (allUnSyncRecords.isNotEmpty()) {
                logE("syncAllRecord", throwable = e, loggable = loggable)
                markUnSyncAndRetry(allUnSyncRecords)
            }
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
        logD("syncTargetRecord: $recordCall", loggable = loggable)
        if (null == recordCall) {
            return
        }
        if (recordCall.synced || (!isManualSync && recordCall.isDeleted)) {
            logW("syncTargetRecord: $recordId has been"
                .plus(if (recordCall.synced) " synced " else "")
                .plus(if (isManualSync) " manual " else "")
                .plus(if (recordCall.isDeleted) " deleted " else "")
                .plus(", sync count: ${recordCall.syncCount}"), loggable = loggable)
            return
        }
        
        val callLogClient =
            contentResolver.acquireUnstableContentProviderClient(CallLog.Calls.CONTENT_URI)
        if (null == callLogClient) {
            logE("Can not load ${CallLog.Calls.CONTENT_URI} ContentProvider obj!!!",
                loggable = loggable)
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
            val callEndTime = if (recordCall.callEndTime <= 0L) {
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
                logD(
                    "selection: ${
                        selection.toString().replace("?", "%s").format(*args.toArray())
                    } $sort", loggable = loggable
                )
            } catch (e: Exception) {
            }
            
            @SuppressLint("Recycle")
            // close by #parseSystemCallRecords
            val callLogResult = callLogClient.query(
                CallLog.Calls.CONTENT_URI,
                TelephonyCenter.get().safeProjections(),
                selection.toString(),
                args.toArray(arrayOf<String>()),
                sort
            )
            
            callLogResult.parseSystemCallRecords(successAction = { systemRecords ->
                logI(systemRecords.mapIndexed { index, systemCallRecord -> "| $index $systemCallRecord" }
                    .joinToString("\n"), loggable = loggable)
                if (systemRecords.isNotEmpty()) {
                    if (systemRecords.size == 1) {
                        // 只有一条记录的情况直接进行同步
                        syncRecordBySys(mapOf(systemRecords.first() to recordCall))
                        logD("syncTargetRecord: just find single Sys record, sync this!",
                            loggable = loggable)
                        return@parseSystemCallRecords
                    }
                    val recordMappingInfo = findMappingRecords(systemRecords, listOf(recordCall))
                    
                    if (recordMappingInfo.unUseSystemCallRecords.isNotEmpty()) {
                        logW("syncTargetRecord: Ignored Sys records: ${recordMappingInfo.unUseSystemCallRecords}",
                            loggable = loggable)
                    }
                    
                    if (recordMappingInfo.mappingRecords.isNotEmpty()) {
                        logD("syncTargetRecord: synced records: ${recordMappingInfo.mappingRecords}",
                            loggable = loggable)
                        syncRecordBySys(recordMappingInfo.mappingRecords)
                    }
                    
                    if (recordMappingInfo.noMappingRecords.isNotEmpty()) {
                        logE("syncAllRecord: not found mapping records: ${recordMappingInfo.noMappingRecords}",
                            loggable = loggable)
                        markNoMappingRecord(recordMappingInfo.noMappingRecords)
                        if (isManualSync) {
                            submitSysRecords(callLogClient, recordCall)
                        } else {
                            CallRecordController.get().retry(work)
                        }
                    }
                } else {
                    logW("syncTargetRecord: Not found sys mapping record!! -> $recordCall",
                        loggable = loggable)
                    markNoMappingRecord(listOf(recordCall))
                    if (isManualSync) {
                        submitSysRecords(callLogClient, recordCall)
                    } else {
                        CallRecordController.get().retry(work)
                    }
                }
            }, failAction = {
                logW("syncTargetRecord: Not found any record with $selection from system db! -> $recordCall",
                    loggable = loggable)
                markNoMappingRecord(arrayListOf(recordCall))
                if (isManualSync) {
                    submitSysRecords(callLogClient, recordCall)
                } else {
                    CallRecordController.get().retry(work)
                }
            })
        } catch (e: Exception) {
            logE("syncTargetRecord", throwable = e, loggable = loggable)
            markNoMappingRecord(arrayListOf(recordCall))
            if (isManualSync) {
                submitSysRecords(callLogClient, recordCall)
            } else {
                CallRecordController.get().retry(work)
            }
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                callLogClient.close()
            } else {
                @Suppress("DEPRECATION") callLogClient.release()
            }
        }
    }
    
    private fun submitSysRecords(callLogClient: ContentProviderClient, recordCall: CallRecord) {
        logI("==============[BEGIN SYS RECORDS]==============", loggable = loggable)
        
        logI("|| [CALL RECORD]", loggable = loggable)
        logI(recordCall, loggable = loggable)
        
        if (!TextUtils.isEmpty(recordCall.phoneNumber)) {
            logI("|| callRecord phone: ${recordCall.phoneNumber}", loggable = loggable)
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
        val callEndTime = if (recordCall.callEndTime <= 0L) {
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
            logI("|| [SELECTION]", loggable = loggable)
            logD(
                "selection: ${
                    selection.toString().replace("?", "%s").format(*args.toArray())
                } $sort", loggable = loggable)
        } catch (e: Exception) {
            logE("submitSysRecords", e, loggable = loggable)
        }
        
        try {
            logI("|| [SYS RECORDS]", loggable = loggable)
            val systemRecords = callLogClient.query(
                CallLog.Calls.CONTENT_URI,
                TelephonyCenter.get().safeProjections(),
                selection.toString(),
                args.toArray(arrayOf<String>()),
                sort
            )
            logCursor(systemRecords, true, loggable = loggable)
            logD("----------------------------------------", loggable = loggable)
            systemRecords.parseSystemCallRecords({
                logI(it.mapIndexed { index, systemCallRecord -> "| $index $systemCallRecord" }
                    .joinToString("\n"), loggable = loggable)
            }, {
                logCursor(systemRecords, loggable = loggable)
            })
        } catch (e: Exception) {
            logE("submitSysRecords", e, loggable = loggable)
        }
        
        logI("===============[END SYS RECORDS]===============", loggable = loggable)
    }
    
    private fun printOtherInfo() {
        printConfigInfo()
        printMobileInfo()
        printDeviceInfo()
        printCustomizeInfo()
    }
    
    private fun printConfigInfo() {
        logI("==============[START CONFIGURE INFO]==============", loggable = loggable)
        try {
            val configure = TelephonyCenter.get().getRecordConfigure()
            logI("|| CTX: ${configure.ctx}", loggable = loggable)
            logI("|| INIT_SYNC: ${configure.needInitSync}", loggable = loggable)
            logI("|| DB_DIR: ${configure.dbFileDirName.invoke()}", loggable = loggable)
            logI("|| DB_VER_LIB: ${BuildConfig.RECORD_DB_VERSION}", loggable = loggable)
            logI("|| DB_VER_APP: ${configure.dbVersion}", loggable = loggable)
            logI("|| SYNC_RETRY_TIME: ${(configure.syncRetryTime / 1000)}s", loggable = loggable)
            logI("|| MAX_RETRY_COUNT: ${configure.maxRetryCount}", loggable = loggable)
            logI("|| START_OFFSET: ${(configure.startTimeOffset / 1000)}s", loggable = loggable)
            logI("|| MAX_OFFSET: ${(configure.maxCallTimeOffset / 1000)}s", loggable = loggable)
            logI("|| MIN_OFFSET: ${(configure.minCallTimeOffset / 1000)}s", loggable = loggable)
            logI("|| DB_PROJECTIONS: ${
                TelephonyCenter.get().safeProjections()?.joinToString(", ")
            }", loggable = loggable)
        } catch (e: Exception) {
            logE("printConfigInfo", throwable = e, loggable = loggable)
        }
        logI("===============[END CONFIGURE INFO]===============", loggable = loggable)
    }
    
    private fun printMobileInfo() {
        logI("==============[START MOBILE INFO]==============", loggable = loggable)
        try {
            logI("|| ALL_OPERATOR: ${TelephonyCenter.get().getAllSimOperator()}",
                loggable = loggable)
            logI("|| MULTI_CONFIG: ${TelephonyCenter.get().getMultiSimConfiguration().name}",
                loggable = loggable)
            logI("|| PN0: ${TelephonyCenter.get().getPhoneNumber()}", loggable = loggable)
            logI("|| PN1: ${TelephonyCenter.get().getPhoneNumber(1)}", loggable = loggable)
            logI("|| PN2: ${TelephonyCenter.get().getPhoneNumber(2)}", loggable = loggable)
            logI("|| ISN0: ${TelephonyCenter.get().getIccSerialNumber()}", loggable = loggable)
            logI("|| ISN1: ${TelephonyCenter.get().getIccSerialNumber(1)}", loggable = loggable)
            logI("|| ISN2: ${TelephonyCenter.get().getIccSerialNumber(2)}", loggable = loggable)
        } catch (e: Exception) {
            logE("printMobileInfo", throwable = e, loggable = loggable)
        }
        logI("===============[END MOBILE INFO]===============", loggable = loggable)
    }
    
    private fun printDeviceInfo() {
        logI("==============[START DEVICE INFO]==============", loggable = loggable)
        try {
            logI("|| MANUFACTURER: ${Build.MANUFACTURER}", loggable = loggable)
            logI("|| DEVICE: ${Build.DEVICE}", loggable = loggable)
            logI("|| PRODUCT: ${Build.PRODUCT}", loggable = loggable)
            logI("|| MODEL: ${Build.MODEL}", loggable = loggable)
            logI("|| HARDWARE: ${Build.HARDWARE}", loggable = loggable)
            logI("|| RELEASE: ".plus(
                arrayOf(
                    Build.VERSION.RELEASE,
                    "(${Build.VERSION.CODENAME})",
                    Build.VERSION.SDK_INT,
                ).joinToString(" ")
            ), loggable = loggable)
            logI("|| ROOTED: ${DeviceUtils.isDeviceRooted()}", loggable = loggable)
            DeviceUtils.getMemoryInfo().forEach {
                logI("|| $it", loggable = loggable)
            }
            DeviceUtils.getExternalAndInternalStorageInfo().forEach {
                logI("|| $it", loggable = loggable)
            }
        } catch (e: Exception) {
            logE("printDeviceInfo", throwable = e, loggable = loggable)
        }
        logI("===============[END DEVICE INFO]===============", loggable = loggable)
    }
    
    private fun printCustomizeInfo() {
        val customizeLogs =
            TelephonyCenter.get().getRecordConfigure().manualSyncCustomizeLogs?.invoke()
        if (!customizeLogs.isNullOrEmpty()) {
            logI("==============[START CUSTOMIZE INFO]==============", loggable = loggable)
            customizeLogs.forEach { (k, v) ->
                logI("|| $k: $v", loggable = loggable)
            }
            logI("===============[END CUSTOMIZE INFO]===============", loggable = loggable)
        }
    }
    
    private fun printEnd() {
        if (isManualSync) {
            printOtherInfo()
        }
        if (isManualSyncLoggable) {
            manualSyncLogOwner.release()
            val diskLogFormatStrategy =
                manualSyncLogOwner.logAdapter.formatStrategy as? DiskLogFormatStrategy
            RecordSyncNotifier.get().notifyManualSyncDone(diskLogFormatStrategy?.getRealLogFile())
        }
    }
    
    private fun markUnSyncAndRetry(finalAllUnSyncRecords: List<CallRecord>) {
        markNoMappingRecord(finalAllUnSyncRecords)
        finalAllUnSyncRecords.map { makeSyncWork(it.recordId) }.forEachIndexed { index, work ->
            CallRecordController.get()
                .retry(work, CallRecordController.get().syncRetryTime + (index * 200))
        }
    }
    
    private fun markNoMappingRecord(allUnSyncRecords: List<CallRecord>) {
        allUnSyncRecords.forEach {
            it.syncCount++
            if (isManualSync) {
                it.isManualSynced = true
            }
            if (it.isManualSynced && System.currentTimeMillis() - it.callStartTime > 7200000) {
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
        if (mappingRecords.isEmpty()) {
            logE("syncRecordBySys: mappingRecords is empty!", loggable = loggable)
            return
        }
        mappingRecords.forEach { mr ->
            val systemCallRecord = mr.key
            val callRecord = mr.value
            logD("syncRecordBySys: $systemCallRecord", loggable = loggable)
            val originStartTime = max(callRecord.callStartTime, callRecord.callOffHookTime)
            callRecord.syncCount++
            callRecord.callLogId = systemCallRecord.callId
            callRecord.callStartTime = systemCallRecord.date
            callRecord.duration = systemCallRecord.duration
            callRecord.callState = systemCallRecord.type
            callRecord.phoneAccountId = systemCallRecord.phoneAccountId
            callRecord.recalculateTime()
            callRecord.synced = true
            if (isManualSync) {
                callRecord.isManualSynced = true
            }
            
            callRecord.recalculateDuration(originStartTime, systemCallRecord)
            
            callRecord.isDeleted = false
            callRecord.isNoMapping = false
            callRecord.syncedTime = System.currentTimeMillis()
        }
        val callRecords = mappingRecords.values.toList()
        callRecords.saveAll()
        RecordSyncNotifier.get().notifyRecordSyncStatus(callRecords)
    }
    
}
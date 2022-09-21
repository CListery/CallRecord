package com.yh.recordlib

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import com.yh.appbasic.logger.logE
import com.yh.appbasic.logger.logW
import com.yh.appbasic.share.AppBasicShare
import com.yh.krealmextensions.RealmConfigManager
import com.yh.recordlib.cons.Constants
import com.yh.recordlib.db.DefCallRecordDBMigration
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.RecordRealmModule
import com.yh.recordlib.ext.findRecordById
import com.yh.recordlib.notifier.RecordSyncNotifier
import com.yh.recordlib.service.SyncCallService
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmMigration
import java.io.File

/**
 * Created by CYH on 2019-06-21 16:39
 */
class CallRecordController private constructor(
    val application: Application,
    var needInitSync: Boolean,
    var dbFileDirName: String,
    var dbVersion: Long,
    var migration: RealmMigration?,
    var syncRetryTime: Long,
    var maxRetryCount: Int,
    var modules: Array<*>?
) {
    
    companion object {
        
        @JvmStatic
        private var mInstances: CallRecordController? = null
        
        @Synchronized
        @JvmStatic
        fun get(): CallRecordController {
            if(null == mInstances) {
                initialization(TelephonyCenter.get().getRecordConfigure())
            }
            return mInstances!!
        }
        
        @Synchronized
        @JvmStatic
        fun initialization(configure: RecordConfigure) {
            var instances = mInstances
            var reInitialization = false
            if(null == instances) {
                instances = CallRecordController(
                    configure.ctx,
                    configure.needInitSync,
                    configure.dbFileDirName.invoke(),
                    configure.dbVersion,
                    configure.migration?.invoke(),
                    configure.syncRetryTime,
                    configure.maxRetryCount,
                    configure.modules?.invoke()
                )
                mInstances = instances
            } else {
                logW("reInitialization", loggable = TelephonyCenter.get())
                reInitialization = true
                instances.needInitSync = configure.needInitSync
                instances.dbFileDirName = configure.dbFileDirName.invoke()
                instances.dbVersion = configure.dbVersion
                instances.migration = configure.migration?.invoke()
                instances.syncRetryTime = configure.syncRetryTime
                instances.maxRetryCount = configure.maxRetryCount
                instances.modules = configure.modules?.invoke()
            }
            instances.setupConfig(reInitialization)
        }
    }
    
    private val mHandlerThread: HandlerThread = HandlerThread("Thread-CallRecordController")
    private val mHandler: Handler
    
    private val mRecordSyncNotifier: RecordSyncNotifier by lazy { RecordSyncNotifier.get() }
    
    init {
        logW("init", loggable = TelephonyCenter.get())
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
        
        Realm.init(application)
        
        RealmConfigManager.isEnableUiThreadOption = true
        
        logW("init done!", loggable = TelephonyCenter.get())
    }
    
    private fun setupConfig(reInitialization: Boolean) {
        logW("setupConfig", loggable = TelephonyCenter.get())
        val builder = RealmConfiguration.Builder()
        builder.directory(File(dbFileDirName))
        builder.name(BuildConfig.CALL_RECORD_DB)
        builder.schemaVersion(DefCallRecordDBMigration.getFinalVersion(dbVersion))
        builder.migration(DefCallRecordDBMigration(migration))
        
        RealmConfigManager.initModule(RecordRealmModule::class.java, builder)
        modules?.filterNotNull()?.forEach { module ->
            RealmConfigManager.initModule(module::class.java, builder)
        }
        
        if(!reInitialization && needInitSync) {
            mHandler.postDelayed({ SyncCallService.enqueueWorkById(application, SyncCallService.SYNC_ALL_RECORD_ID) }, 2000)
        }
        logW("setupConfig done!", TelephonyCenter.get())
    }
    
    fun retry(work: Intent, delayMillis: Long = syncRetryTime) {
        val recordId = work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)
        if(recordId.isNullOrEmpty() || SyncCallService.SYNC_ALL_RECORD_ID == recordId) {
            logE("Can not retry sync this record $recordId, record id is invalid", loggable = TelephonyCenter.get())
            return
        } else {
            val callRecord = findRecordById(recordId)
            if(null == callRecord) {
                logE("Can not retry sync this record $recordId, not found record", loggable = TelephonyCenter.get())
                return
            }
            if(callRecord.isDeleted) {
                logE("Can not retry sync this record $recordId, has been deleted", loggable = TelephonyCenter.get())
                return
            }
            if(callRecord.synced) {
                logE("Can not retry sync this record $recordId, has been synced", loggable = TelephonyCenter.get())
                return
            }
            if(callRecord.syncCount > maxRetryCount) {
                logE("Can not retry sync this record $recordId, re-sync count has exceeded the maximum $maxRetryCount", loggable = TelephonyCenter.get())
                return
            }
            logW("Retry TARGET:${recordId} -> ${callRecord.syncCount}", loggable = TelephonyCenter.get())
        }
        mHandler.postDelayed({ SyncCallService.enqueueWork(application, work) }, delayMillis)
    }
    
    fun registerRecordSyncListener(iSyncCallback: ISyncCallback) {
        mRecordSyncNotifier.register(iSyncCallback)
    }
    
    fun unRegisterRecordSyncListener(iSyncCallback: ISyncCallback) {
        mRecordSyncNotifier.unRegister(iSyncCallback)
    }
    
    fun clearManualSyncListener() {
        mRecordSyncNotifier.clearManualSyncListener()
    }
    
    fun manualSyncRecord(
        recordCall: CallRecord,
        notLogOut: Boolean = false,
    ) {
        val intent = Intent()
        intent.putExtra(Constants.EXTRA_LAST_RECORD_ID, recordCall.recordId)
        intent.putExtra(Constants.EXTRA_IS_MANUAL_SYNC, true)
        intent.putExtra(Constants.EXTRA_NOT_LOGFILE, notLogOut)
        SyncCallService.enqueueWork(AppBasicShare.context, intent)
    }
}
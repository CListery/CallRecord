package com.yh.recordlib

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import com.yh.recordlib.cons.Constants
import com.yh.recordlib.entity.RecordRealmModule
import com.yh.recordlib.notifier.RecordSyncNotifier
import com.yh.recordlib.service.SyncCallService
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmMigration
import timber.log.Timber
import java.io.File

/**
 * Created by CYH on 2019-06-21 16:39
 *
 * @param needInitSync Whether to perform full synchronization during initialization. Default: false
 * @param syncRetryTime Retry interval when the synchronous system's CallRecord data fails. Default: com.yh.recordlib.BuildConfig#CALL_RECORD_RETRY_TIME
 * @param maxRetryCount Max retry count. Default: 2
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
                Timber.w("reInitialization")
                instances.needInitSync = configure.needInitSync
                instances.dbFileDirName = configure.dbFileDirName.invoke()
                instances.dbVersion = configure.dbVersion
                instances.migration = configure.migration?.invoke()
                instances.syncRetryTime = configure.syncRetryTime
                instances.maxRetryCount = configure.maxRetryCount
                instances.modules = configure.modules?.invoke()
            }
            instances.setupConfig()
        }
    }
    
    private val mHandlerThread: HandlerThread = HandlerThread("Thread-CallRecordController")
    private val mHandler: Handler
    
    private val mRecordSyncNotifier: RecordSyncNotifier by lazy { RecordSyncNotifier.get() }
    
    init {
        Timber.w("init")
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
        
        if(BuildConfig.ENABLE_DEBUG && 0 == Timber.treeCount()) {
            Timber.plant(Timber.DebugTree())
        }
        
        Realm.init(application)
        Timber.w("init done!")
    }
    
    private fun setupConfig() {
        Timber.w("setupConfig")
        val builder = RealmConfiguration.Builder()
        builder.directory(File(dbFileDirName))
        builder.name(BuildConfig.CALL_RECORD_DB)
        builder.schemaVersion(dbVersion)
        
        val defaultModule = Realm.getDefaultModule()
        if(null != defaultModule) {
            builder.modules(defaultModule)
        }
        builder.addModule(RecordRealmModule())
        modules?.filterNotNull()?.forEach { module ->
            builder.addModule(module)
        }
        
        migration?.let {
            builder.migration(it) // Migration to run instead of throwing an exception
        } ?: builder.deleteRealmIfMigrationNeeded()
        
        Realm.setDefaultConfiguration(builder.build())
        
        if(needInitSync) {
            SyncCallService.enqueueWork(application)
        }
        Timber.w("setupConfig done!")
    }
    
    fun retry(work: Intent) {
        val retryCount = work.getIntExtra(Constants.EXTRA_RETRY, 0)
        Timber.e("retry ${work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)} -> $retryCount")
        if(retryCount >= maxRetryCount) {
            Timber.e("Can not retry sync this record ${work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)}")
            return
        }
        work.putExtra(Constants.EXTRA_RETRY, retryCount.inc())
        mHandler.postDelayed({ SyncCallService.enqueueWork(application, work) }, syncRetryTime)
    }
    
    fun registerRecordSyncListener(iSyncCallback: ISyncCallback) {
        mRecordSyncNotifier.register(iSyncCallback)
    }
    
    fun unRegisterRecordSyncListener(iSyncCallback: ISyncCallback) {
        mRecordSyncNotifier.unRegister(iSyncCallback)
    }
}
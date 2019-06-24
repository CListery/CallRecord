package com.yh.recordlib

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import com.yh.recordlib.cons.Constants
import com.yh.recordlib.notifier.RecordSyncNotifier
import com.yh.recordlib.service.SyncCallService
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmMigration
import timber.log.Timber

/**
 * Created by CYH on 2019-06-21 16:39
 */
class CallRecordController private constructor(
    val application: Application,
    mNeedInitSync: Boolean,
    mDBFileName: String,
    mDBVersion: Long,
    mMigration: RealmMigration?,
    private val mSyncRetryTime: Long,
    private val mMaxRetryCount: Int
) {
    
    companion object {
        private var mInstances: CallRecordController? = null
        
        @Synchronized
        fun get(
            ctx: Application? = null,
            needInitSync: Boolean = false,
            dbFileName: String = "CallRecord.realm",
            dbVersion: Long = 1L,
            migration: (() -> RealmMigration)? = null,
            syncRetryTime: Long = BuildConfig.CALL_RECORD_RETRY_TIME,
            maxRetryCount: Int = 2
        ): CallRecordController {
            if(null == mInstances) {
                if(null == ctx) {
                    throw Exception("CallRecordController not yet!")
                }
                mInstances = CallRecordController(
                    ctx,
                    needInitSync,
                    dbFileName,
                    dbVersion,
                    migration?.invoke(),
                    syncRetryTime,
                    maxRetryCount
                )
            }
            return mInstances!!
        }
    }
    
    private val mHandlerThread: HandlerThread = HandlerThread("Thread-CallRecordController")
    private val mHandler: Handler
    
    private val mRecordSyncNotifier: RecordSyncNotifier by lazy { RecordSyncNotifier.get() }
    
    init {
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
        
        Realm.init(application)
        val builder = RealmConfiguration.Builder()
            .name(mDBFileName)
            .schemaVersion(mDBVersion)
        if(null == mMigration) {
            builder.deleteRealmIfMigrationNeeded()
        } else {
            builder.migration(mMigration) // Migration to run instead of throwing an exception
        }
        val callRecordConfig = builder.build()
        Realm.setDefaultConfiguration(callRecordConfig)
        
        if(mNeedInitSync) {
            SyncCallService.enqueueWork(application)
        }
    }
    
    fun retry(work: Intent) {
        val retryCount = work.getIntExtra(Constants.EXTRA_RETRY, 0)
        Timber.e("retry ${work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)} -> $retryCount")
        if(retryCount >= mMaxRetryCount) {
            Timber.e("Can not retry sync this record ${work.getStringExtra(Constants.EXTRA_LAST_RECORD_ID)}")
            return
        }
        work.putExtra(Constants.EXTRA_RETRY, retryCount.inc())
        mHandler.postDelayed({ SyncCallService.enqueueWork(application, work) }, mSyncRetryTime)
    }
    
    fun registerRecordSyncListener(iSyncCallback: ISyncCallback) {
        mRecordSyncNotifier.register(iSyncCallback)
    }
    
    fun unRegisterRecordSyncListener(iSyncCallback: ISyncCallback) {
        mRecordSyncNotifier.unRegister(iSyncCallback)
    }
}
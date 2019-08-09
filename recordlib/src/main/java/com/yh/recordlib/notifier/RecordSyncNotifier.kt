package com.yh.recordlib.notifier

import android.os.Handler
import android.os.HandlerThread
import com.yh.recordlib.ISyncCallback
import com.yh.recordlib.entity.CallRecord

/**
 * Created by CYH on 2019-06-24 16:24
 */
class RecordSyncNotifier private constructor() {
    
    companion object {
        @JvmStatic
        private var mInstances: RecordSyncNotifier? = null

        @JvmStatic
        @Synchronized
        fun get(): RecordSyncNotifier {
            if(null == mInstances) {
                mInstances = RecordSyncNotifier()
            }
            return mInstances!!
        }
    }
    
    private val mHandlerThread: HandlerThread = HandlerThread("Thread-RecordSyncNotifier")
    private val mHandler: Handler
    private val mSyncCallbacks by lazy { ArrayList<ISyncCallback>() }
    
    init {
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
    }
    
    @Synchronized
    fun register(iSyncCallback: ISyncCallback) {
        if(!mSyncCallbacks.contains(iSyncCallback)) {
            mSyncCallbacks.add(iSyncCallback)
        }
    }
    
    @Synchronized
    fun unRegister(iSyncCallback: ISyncCallback) {
        mSyncCallbacks.remove(iSyncCallback)
    }
    
    @Synchronized
    fun notifyRecordSyncStatus(callRecords: ArrayList<CallRecord>) {
        mHandler.post {
            ArrayList(mSyncCallbacks).forEach { event ->
                callRecords.forEach { record ->
                    if(record.synced) {
                        event.onSyncSuccess(record.recordId)
                    } else {
                        event.onSuncFail(record.recordId)
                    }
                }
            }
        }
    }
}
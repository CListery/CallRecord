package com.yh.recordlib.notifier

import android.os.Handler
import android.os.Looper
import com.yh.recordlib.IManualSyncCallback
import com.yh.recordlib.ISyncCallback
import com.yh.recordlib.entity.CallRecord
import java.io.File

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
    
    private val mHandler: Handler = Handler(Looper.getMainLooper())
    private val mSyncCallbacks by lazy { HashSet<ISyncCallback>() }
    
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
    fun clearManualSyncListener() {
        val manualSyncListeners = mSyncCallbacks.filterIsInstance<IManualSyncCallback>()
        if(manualSyncListeners.isNotEmpty()) {
            mSyncCallbacks.removeAll(manualSyncListeners)
        }
    }
    
    @Synchronized
    fun notifyRecordSyncStatus(callRecords: ArrayList<CallRecord>) {
        mHandler.post {
            ArrayList(mSyncCallbacks).forEach { event ->
                if(callRecords.isNotEmpty()) {
                    callRecords.forEach { record ->
                        if(record.synced) {
                            event.onSyncSuccess(record)
                        } else {
                            event.onSyncFail(record)
                        }
                    }
                }
            }
        }
    }
    
    @Synchronized
    fun notifyManualSyncDone(realLogFile: File?) {
        if(null == realLogFile || !realLogFile.exists()){
            return
        }
        mHandler.post {
            ArrayList<IManualSyncCallback>(mSyncCallbacks.filterIsInstance<IManualSyncCallback>()).forEach { event ->
                event.onSyncDone(realLogFile)
            }
        }
    }
    
}
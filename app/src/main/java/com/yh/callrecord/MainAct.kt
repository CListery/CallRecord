package com.yh.callrecord

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.codezjx.andlinker.AndLinker
import com.yh.appbasic.logger.logD
import com.yh.appbasic.logger.logE
import com.yh.appbasic.logger.logW
import com.yh.krealmextensions.querySortedAsync
import com.yh.krealmextensions.save
import com.yh.recordlib.CallRecordController
import com.yh.recordlib.IManualSyncCallback
import com.yh.recordlib.ISyncCallback
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.cons.Constants
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.ipc.IRecordCallback
import com.yh.recordlib.ipc.IRecordService
import com.yh.recordlib.service.RecordCallService
import com.yh.recordlib.service.SyncCallService
import io.realm.Sort
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Created by CYH on 2019-05-30 08:53
 */
class MainAct : Activity(),
                AndLinker.BindCallback {
    
    private var mRecordService: IRecordService? = null
    private val mBindSuccess = AtomicBoolean(false)
    
    private var mLastRecordId: String? = null
    
    private val mRecordCallback: IRecordCallback = object : IRecordCallback {
        override fun onRecordIdCreated(callRecord: CallRecord) {
            logD("onRecordIdCreated: $callRecord")
            mLastRecordId = callRecord.recordId
        }
        
        override fun onCallIn(recordId: String) {
            logD("onCallIn: $recordId")
        }
        
        override fun onCallOut(recordId: String) {
            logD("onCallOut: $recordId")
        }
        
        override fun onCallEnd(recordId: String) {
            logD("onCallEnd: $recordId")
        }
        
        override fun onCallOffHook(recordId: String) {
            logD("onCallOffHook: $recordId")
        }
    }
    
    private val mManualSyncListener by lazy {
        object : IManualSyncCallback {
            override fun onSyncDone(logFile: File) {
                logW(logFile)
            }
            
            override fun onSyncSuccess(record: CallRecord) {
                logW("onSyncSuccess: ${record.recordId}")
            }
            
            override fun onSyncFail(record: CallRecord) {
                logE("onSyncFail: ${record.recordId}")
            }
        }
    }
    
    private val mLinker: AndLinker by lazy {
        AndLinker.Builder(App.get())
            .packageName(packageName)
            .className(RecordCallService::class.java.name)
            .build()
    }
    
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.act_main)
        
        if(null != savedInstanceState && savedInstanceState.containsKey(Constants.EXTRA_LAST_RECORD_ID)) {
            mLastRecordId = savedInstanceState.getString(Constants.EXTRA_LAST_RECORD_ID)
        }
        
        CallRecordController.get()
            .registerRecordSyncListener(object : ISyncCallback {
                override fun onSyncSuccess(record: CallRecord) {
                    logD("onSyncSuccess: $record")
                }
                
                override fun onSyncFail(record: CallRecord) {
                    logE("onSyncFail: $record")
                }
            })
        
        mLinker.bind()
        mLinker.setBindCallback(this@MainAct)
        
        findViewById<View>(R.id.mSyncBtn)?.setOnClickListener {
            SyncCallService.enqueueWorkById(application, SyncCallService.SYNC_ALL_RECORD_ID)
        }
        findViewById<View>(R.id.mCallBtn)?.setOnClickListener {
            TelephonyCenter.get()
                .call(this, "10010", mRecordService, mRecordCallback)
        }
        findViewById<View>(R.id.mGetMCCMNC)?.setOnClickListener {
            logW("-> ${TelephonyCenter.get().getSimOperator().operatorName}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getSimOperator(1).operatorName}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getSimOperator(2).operatorName}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getAllSimOperator()}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getPhoneNumber()}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getPhoneNumber(1)}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getPhoneNumber(2)}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getIccSerialNumber()}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getIccSerialNumber(1)}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getIccSerialNumber(2)}", loggable = TelephonyCenter.get())
        }
        findViewById<View>(R.id.mGetLastRecord)?.setOnClickListener {
            querySortedAsync<CallRecord>({
                logD("queryLastRecord: ${it.size}")
                val lastCR = it.firstOrNull()
                logD("queryLastRecord: $lastCR")
                lastCR?.apply {
                    findViewById<View>(R.id.mRecordLayout).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.mMobileTxt).text = "Mobile: $phoneNumber"
                    findViewById<TextView>(R.id.mDurationTxt).text = "Duration: $duration"
                    findViewById<TextView>(R.id.mCallTimeTxt).text = "CallTime: ${SimpleDateFormat("yyyy.M.d HH:mm", Locale.CHINESE).format(callStartTime)}"
                    findViewById<TextView>(R.id.mSyncTxt).text = "Synced: $synced"
                    findViewById<TextView>(R.id.mSubIdTxt).text = "SubId: $phoneAccountId\nMccMnc: $mccMnc"
                }
                    ?: apply {
                        findViewById<View>(R.id.mRecordLayout).visibility = View.GONE
                    }
            }, "callStartTime", Sort.DESCENDING)
        }
        findViewById<View>(R.id.mManualSync)?.setOnClickListener {
            CallRecordController.get().registerRecordSyncListener(mManualSyncListener)
            val record = CallRecord()
            record.recordId = UUID.randomUUID().toString().replace("-", "")
            record.phoneNumber = "18988447486"
            record.callStartTime = 1599909960000
            record.callType = CallType.CallOut.ordinal
            record.isNoMapping = true
            record.save()
            thread {
                Thread.sleep(500)
                CallRecordController.get().manualSyncRecord(record)
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if(null != mLastRecordId) {
            outState.putString(Constants.EXTRA_LAST_RECORD_ID, mLastRecordId)
        }
    }
    
    override fun onDestroy() {
        logD("onDestroy")
        CallRecordController.get().clearManualSyncListener()
        mLinker.setBindCallback(null)
        mLinker.unbind()
        super.onDestroy()
    }
    
    override fun onBind() {
        if(mBindSuccess.compareAndSet(false, true)) {
            mRecordService = mLinker.create(IRecordService::class.java)
            if(null != mLastRecordId) {
                mRecordService?.resumeLastRecord(mLastRecordId!!)
            }
        }
    }
    
    override fun onUnBind() {
        logD("onUnBind")
        mBindSuccess.set(false)
    }
}
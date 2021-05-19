package com.yh.callrecord

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.codezjx.andlinker.AndLinker
import com.vicpin.krealmextensions.save
import com.yh.appinject.logger.ext.libD
import com.yh.appinject.logger.ext.libW
import com.yh.appinject.logger.logD
import com.yh.appinject.logger.logE
import com.yh.appinject.logger.logW
import com.yh.recordlib.CallRecordController
import com.yh.recordlib.IManualSyncCallback
import com.yh.recordlib.ISyncCallback
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.cons.Constants
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.ext.queryLastRecord
import com.yh.recordlib.ipc.IRecordCallback
import com.yh.recordlib.ipc.IRecordService
import com.yh.recordlib.service.RecordCallService
import com.yh.recordlib.service.SyncCallService
import java.io.File
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
        override fun onRecordIdCreated(recordId: String) {
            logD("onRecordIdCreated: $recordId")
            mLastRecordId = recordId
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
            .apply { setBindCallback(this@MainAct) }
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
        
        findViewById<View>(R.id.mSyncBtn)?.setOnClickListener {
            SyncCallService.enqueueWork(application)
        }
        findViewById<View>(R.id.mCallBtn)?.setOnClickListener {
            TelephonyCenter.get()
                .call(this, "10010", mRecordService, mRecordCallback)
        }
        findViewById<View>(R.id.mGetMCCMNC)?.setOnClickListener {
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getSimOperator().operatorName}")
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getSimOperator(1).operatorName}")
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getSimOperator(2).operatorName}")
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getAllSimOperator()}")
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getPhoneNumber()}")
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getPhoneNumber(1)}")
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getPhoneNumber(2)}")
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getIccSerialNumber()}")
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getIccSerialNumber(1)}")
            TelephonyCenter.get().libW("-> ${TelephonyCenter.get().getIccSerialNumber(2)}")
        }
        findViewById<View>(R.id.mGetLastRecord)?.setOnClickListener {
            queryLastRecord()?.apply {
                findViewById<View>(R.id.mRecordLayout).visibility = View.VISIBLE
                findViewById<TextView>(R.id.mMobileTxt).text = "Mobile: $phoneNumber\nFake: $isFake"
                findViewById<TextView>(R.id.mDurationTxt).text = "Duration: $duration"
                findViewById<TextView>(R.id.mCallTimeTxt).text = "CallTime: ${getFormatDate()}"
                findViewById<TextView>(R.id.mSyncTxt).text = "Synced: $synced"
                findViewById<TextView>(R.id.mSubIdTxt).text = "SubId: $phoneAccountId\nMccMnc: $mccMnc"
            }
                ?: apply {
                    findViewById<View>(R.id.mRecordLayout).visibility = View.GONE
                }
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
                CallRecordController.get().manualSyncRecord(record, cacheDir.absolutePath, "msr_${record.phoneNumber}")
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        if(null != mLastRecordId) {
            outState?.putString(Constants.EXTRA_LAST_RECORD_ID, mLastRecordId)
        }
    }
    
    override fun onDestroy() {
        CallRecordController.get().clearManualSyncListener()
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
        TelephonyCenter.get().libD("onUnBind")
        mBindSuccess.set(false)
    }
}
package com.yh.callrecord

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import com.codezjx.andlinker.AndLinker
import com.yh.appinject.logger.ext.libD
import com.yh.appinject.logger.ext.libW
import com.yh.recordlib.CallRecordController
import com.yh.recordlib.ISyncCallback
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.cons.Constants
import com.yh.recordlib.ext.queryLastRecord
import com.yh.recordlib.ipc.IRecordCallback
import com.yh.recordlib.ipc.IRecordService
import com.yh.recordlib.service.RecordCallService
import com.yh.recordlib.service.SyncCallService
import kotlinx.android.synthetic.main.act_main.*
import java.util.concurrent.atomic.AtomicBoolean

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
            mLastRecordId = recordId
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
                override fun onSyncFail(recordId: String) {
                    TelephonyCenter.get().libD("onSyncFail: $recordId")
                }
                
                override fun onSyncSuccess(recordId: String) {
                    TelephonyCenter.get().libD("onSyncSuccess: $recordId")
                }
            })
        
        mLinker.bind()
        
        mSyncBtn?.setOnClickListener {
            SyncCallService.enqueueWork(application)
        }
        mCallBtn?.setOnClickListener {
            TelephonyCenter.get()
                .call(this, "10010", mRecordService)
        }
        mGetMCCMNC?.setOnClickListener {
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
        mGetLastRecord?.setOnClickListener {
            queryLastRecord()?.apply {
                mRecordLayout.visibility = View.VISIBLE
                mMobileTxt.text = "Mobile: $phoneNumber\nFake: $isFake"
                mDurationTxt.text = "Duration: $duration"
                mCallTimeTxt.text = "CallTime: ${getFormatDate()}"
                mSyncTxt.text = "Synced: $synced"
                mSubIdTxt.text = "SubId: $phoneAccountId\nMccMnc: $mccMnc"
            } ?: apply {
                mRecordLayout.visibility = View.GONE
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
        super.onDestroy()
        mLinker.unbind()
    }
    
    override fun onBind() {
        if(mBindSuccess.compareAndSet(false, true)) {
            mRecordService = mLinker.create(IRecordService::class.java)
            if(null != mLastRecordId) {
                mRecordService?.resumeLastRecord(mLastRecordId!!)
            }
            mRecordService?.registerRecordCallback(mRecordCallback)
        }
    }
    
    override fun onUnBind() {
        TelephonyCenter.get().libD("onUnBind")
        mBindSuccess.set(false)
    }
}
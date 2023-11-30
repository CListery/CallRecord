package com.yh.callrecord

import android.annotation.SuppressLint
import android.app.Activity
import android.app.onClickById
import android.os.Bundle
import android.widget.TextView
import com.codezjx.andlinker.AndLinker
import com.yh.appbasic.logger.logD
import com.yh.appbasic.logger.logE
import com.yh.appbasic.logger.logW
import com.yh.jsonholder.Jackson
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
            this@MainAct.findViewById<TextView>(R.id.mContentTxt).append("onRecordIdCreated: $callRecord")
            mLastRecordId = callRecord.recordId
        }
        
        override fun onCallIn(recordId: String) {
            logD("onCallIn: $recordId")
            this@MainAct.findViewById<TextView>(R.id.mContentTxt).append("onCallIn: $recordId")
        }
        
        override fun onCallOut(recordId: String) {
            logD("onCallOut: $recordId")
            this@MainAct.findViewById<TextView>(R.id.mContentTxt).append("onCallOut: $recordId")
        }
        
        override fun onCallEnd(recordId: String) {
            logD("onCallEnd: $recordId")
            this@MainAct.findViewById<TextView>(R.id.mContentTxt).append("onCallEnd: $recordId")
        }
        
        override fun onCallOffHook(recordId: String) {
            logD("onCallOffHook: $recordId")
            this@MainAct.findViewById<TextView>(R.id.mContentTxt).append("onCallOffHook: $recordId")
        }
    }
    
    private val mManualSyncListener by lazy {
        object : IManualSyncCallback {
            override fun onSyncDone(logFile: File) {
                logW(logFile)
            }
            
            override fun onSyncSuccess(record: CallRecord) {
                logW("onSyncSuccess: ${record.recordId}")
                this@MainAct.findViewById<TextView>(R.id.mContentTxt).append("onSyncSuccess: ${record.recordId}")
            }
            
            override fun onSyncFail(record: CallRecord) {
                logE("onSyncFail: ${record.recordId}")
                this@MainAct.findViewById<TextView>(R.id.mContentTxt).append("onSyncFail: ${record.recordId}")
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
                    this@MainAct.findViewById<TextView>(R.id.mContentTxt).append("onSyncSuccess: $record")
                }
                
                override fun onSyncFail(record: CallRecord) {
                    logE("onSyncFail: $record")
                    this@MainAct.findViewById<TextView>(R.id.mContentTxt).append("onSyncFail: $record")
                }
            })
        
        mLinker.bind()
        mLinker.setBindCallback(this@MainAct)
        
        onClickById(R.id.mSyncBtn) {
            SyncCallService.enqueueWorkById(application, SyncCallService.SYNC_ALL_RECORD_ID)
        }
        onClickById(R.id.mCallBtn) {
            TelephonyCenter.get().call(this@MainAct, "10010", mRecordService, mRecordCallback)
        }
        onClickById(R.id.mGetMCCMNC) {
            logW("-> ${TelephonyCenter.get().getSimOperator()}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getSimOperator(0)}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getSimOperator(1)}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getAllSimOperator()}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getPhoneNumber()}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getPhoneNumber(0)}", loggable = TelephonyCenter.get())
            logW("-> ${TelephonyCenter.get().getPhoneNumber(1)}", loggable = TelephonyCenter.get())
            this@MainAct.findViewById<TextView>(R.id.mContentTxt).text = """
                        |SimOperator = ${TelephonyCenter.get().getSimOperator()}
                        |SimOperator[1] = ${TelephonyCenter.get().getSimOperator(0)}
                        |SimOperator[2] = ${TelephonyCenter.get().getSimOperator(1)}
                        |AllSimOperator = ${TelephonyCenter.get().getAllSimOperator()}
                        |PhoneNumber = ${TelephonyCenter.get().getPhoneNumber()}
                        |PhoneNumber[1] = ${TelephonyCenter.get().getPhoneNumber(0)}
                        |PhoneNumber[2] = ${TelephonyCenter.get().getPhoneNumber(1)}
                    """.trimMargin()
        }
        onClickById(R.id.mGetLastRecord) {
            querySortedAsync<CallRecord>({
                logD("queryLastRecord: ${it.size}")
                val lastCR = it.firstOrNull()
                logD("queryLastRecord: $lastCR")
                lastCR?.apply {
                    this@MainAct.findViewById<TextView>(R.id.mContentTxt).text = """
                        |Mobile: $phoneNumber
                        |Duration: $duration
                        |CallTime: ${SimpleDateFormat("yyyy.M.d HH:mm", Locale.CHINESE).format(callStartTime)}
                        |Synced: $synced
                        |SubId: $phoneAccountId
                        |MccMnc: $mccMnc
                    """.trimMargin()
                }
                    ?: apply {
                        this@MainAct.findViewById<TextView>(R.id.mContentTxt).text = "NOT FOUND"
                    }
            }, "callStartTime", Sort.DESCENDING)
        }
        onClickById(R.id.mManualSync) {
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
        onClickById(R.id.mNewAPI) {
            this@MainAct.findViewById<TextView>(R.id.mContentTxt).text = Jackson.asPrint(
                TelephonyCenter.get().allSubInfo
            )
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
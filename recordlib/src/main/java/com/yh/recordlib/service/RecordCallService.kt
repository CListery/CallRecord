package com.yh.recordlib.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.codezjx.andlinker.AndLinkerBinder
import com.vicpin.krealmextensions.save
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.entity.FakeCallRecord
import com.yh.recordlib.ext.findRecordById
import com.yh.recordlib.ipc.IRecordCallback
import com.yh.recordlib.ipc.IRecordService
import com.yh.recordlib.media.MediaRecordHelper
import com.yh.recordlib.utils.makeMD5UUID
import com.yh.recordlib.utils.makeRandomUUID
import timber.log.Timber

/**
 * Created by CYH on 2019-05-30 11:05
 */
class RecordCallService : Service() {
    
    companion object {
        @JvmStatic
        private var mCurrentState = TelephonyManager.CALL_STATE_IDLE
        @JvmStatic
        private var mLastRecordId: String? = null
    }
    
    private var mLinkerBinder: AndLinkerBinder? = null
    private var mRecordCallback: IRecordCallback? = null
    private val mRecordService = object : IRecordService {
        override fun resumeLastRecord(recordId: String) {
            if(TelephonyManager.CALL_STATE_IDLE != mCurrentState) {
                mLastRecordId = recordId
            }
        }
        
        override fun startListen() {
            internalStartListen()
        }
        
        override fun stopListen() {
            internalStopListen()
        }
        
        override fun registerRecordCallback(recordCallback: IRecordCallback) {
            mRecordCallback = recordCallback
        }
    }
    
    private val mStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            Timber.d("onCallStateChanged: $state - $phoneNumber")
            internalCallStateChanged(state, phoneNumber)
        }
    }

//    private val mCallStateReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            Timber.d("onReceive: ${intent?.action} -> ${System.currentTimeMillis()}")
//            Timber.d(
//                "onReceive: TelephonyManager.EXTRA_STATE -> ${intent?.getStringExtra(
//                    TelephonyManager.EXTRA_STATE
//                )}"
//            )
//            Timber.d(
//                "onReceive: TelephonyManager.EXTRA_INCOMING_NUMBER -> ${intent?.getStringExtra(
//                    TelephonyManager.EXTRA_INCOMING_NUMBER
//                )}"
//            )
//        }
//    }
    
    /**
     * outgoing : CALL_STATE_OFFHOOK -> CALL_STATE_IDLE
     *
     * incoming : CALL_STATE_RINGING -> CALL_STATE_OFFHOOK -> CALL_STATE_IDLE
     */
    private fun internalCallStateChanged(state: Int, phoneNumber: String?) {
        when(state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                if(TelephonyManager.CALL_STATE_IDLE != mCurrentState) {
                    //挂断
                    Timber.d("IDLE 1: $mLastRecordId")
                    if(null != mLastRecordId) {
                        val lastRecord = findRecordById(mLastRecordId!!)
                        Timber.d("IDLE 2: $lastRecord")
                        if(null != lastRecord) {
                            lastRecord.callEndTime = System.currentTimeMillis()
                            lastRecord.save()
                            SyncCallService.enqueueWork(
                                applicationContext, mLastRecordId
                            )
                        }
                    }
                    MediaRecordHelper.get(application)
                        .stopRecord()
                    internalStopListen()
                }
                mLastRecordId = null
            }
            
            TelephonyManager.CALL_STATE_RINGING -> {
                if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                    //呼入开始
                    val callRecord = makeRecord(phoneNumber, CallType.CallIn)
                    MediaRecordHelper.get(application)
                        .startRecord(callRecord)
                }
            }
            
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                    //呼出开始
                    val callRecord = makeRecord(phoneNumber, CallType.CallOut)
                    callRecord.callOffHookTime = System.currentTimeMillis()
                    MediaRecordHelper.get(application)
                        .startRecord(callRecord)
                } else if(TelephonyManager.CALL_STATE_RINGING == mCurrentState) {
                    //呼入接听
                    if(null != mLastRecordId) {
                        var callRecord = findRecordById(mLastRecordId!!)
                        if(null == callRecord) {
                            callRecord = makeRecord(phoneNumber, CallType.CallIn)
                        }
                        callRecord.callOffHookTime = System.currentTimeMillis()
                        callRecord.save()
                        MediaRecordHelper.get(application)
                            .startRecord(callRecord)
                    }
                }
            }
        }
        mCurrentState = state
    }
    
    @Synchronized
    private fun makeRecord(phoneNumber: String?, callType: CallType): CallRecord {
        val recordId: String = createRecordId()
        var recordPhoneNumber: String? = phoneNumber
        var fake = false
        if(null == recordPhoneNumber || TextUtils.isEmpty(recordPhoneNumber)) {
            fake = true
            recordPhoneNumber = createFakePhoneNumber()
            Timber.w("Fake phone number: $recordPhoneNumber")
            val fakeRecord = FakeCallRecord()
            fakeRecord.recordId = recordId
            fakeRecord.fakeNumber = recordPhoneNumber
            fakeRecord.save()
        }
        val record = CallRecord()
        record.recordId = recordId
        record.isFake = fake
        record.callType = callType.ordinal
        record.callStartTime = System.currentTimeMillis()
        record.phoneNumber = recordPhoneNumber
        record.save()
        mLastRecordId = recordId
        mRecordCallback?.onRecordIdCreated(recordId)
        return record
    }
    
    @Synchronized
    private fun createRecordId() = makeRandomUUID()
    
    @Synchronized
    private fun createFakePhoneNumber() = makeMD5UUID()
    
    private fun internalStartListen() {
        val telephonyService = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyService.listen(mStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }
    
    private fun internalStopListen() {
        val telephonyService = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyService.listen(mStateListener, PhoneStateListener.LISTEN_NONE)
    }
    
    override fun onCreate() {
        super.onCreate()
        
        mLinkerBinder = AndLinkerBinder.Factory.newBinder()
        mLinkerBinder?.registerObject(mRecordService)

//        val receiverFilter = IntentFilter()
//        receiverFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
//        receiverFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL)
//        receiverFilter.addAction(Intent.ACTION_CALL_BUTTON)
//        receiverFilter.addAction(Intent.ACTION_CALL)
//        registerReceiver(mCallStateReceiver, receiverFilter)
    }
    
    override fun onBind(intent: Intent?): IBinder? = mLinkerBinder
    
    override fun onDestroy() {
        super.onDestroy()
        mLinkerBinder?.unRegisterObject(mRecordService)
//        unregisterReceiver(mCallStateReceiver)
    }
}
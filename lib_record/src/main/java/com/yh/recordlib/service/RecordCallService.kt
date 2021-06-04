package com.yh.recordlib.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.codezjx.andlinker.AndLinkerBinder
import com.vicpin.krealmextensions.save
import com.yh.appinject.logger.ext.libD
import com.yh.appinject.logger.ext.libW
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.ext.findRecordById
import com.yh.recordlib.ipc.IRecordCallback
import com.yh.recordlib.ipc.IRecordService
import com.yh.recordlib.media.MediaRecordHelper
import com.yh.recordlib.utils.makeRandomUUID

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
        
        override fun startListen(callNumber: String, callType: CallType) {
            TelephonyCenter.get().libW("startListen")
            if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                makeRecord(callNumber, callType)
                internalStartListen()
            }
        }
        
        override fun stopListen() {
            TelephonyCenter.get().libW("stopListen: $mCurrentState")
            if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                internalStopListen()
            }
        }
        
        override fun registerRecordCallback(recordCallback: IRecordCallback) {
            mRecordCallback = recordCallback
        }
        
        override fun unRegisterRecordCallback(recordCallback: IRecordCallback) {
            if(null != mRecordCallback && mRecordCallback == recordCallback) {
                mRecordCallback = null
            }
        }
    }
    
    private val mStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            TelephonyCenter.get().libD("onCallStateChanged: $state - $phoneNumber")
            internalCallStateChanged(state)
        }
    }
    
    /**
     * outgoing : CALL_STATE_OFFHOOK -> CALL_STATE_IDLE
     *
     * incoming : CALL_STATE_RINGING -> CALL_STATE_OFFHOOK -> CALL_STATE_IDLE
     */
    private fun internalCallStateChanged(state: Int) {
        when(state) {
            TelephonyManager.CALL_STATE_IDLE    -> {
                if(TelephonyManager.CALL_STATE_IDLE != mCurrentState) {
                    //挂断
                    mCurrentState = state
                    TelephonyCenter.get().libD("IDLE 1: $mLastRecordId")
                    if(!mLastRecordId.isNullOrEmpty()) {
                        val lastRecord = findRecordById(mLastRecordId!!)
                        TelephonyCenter.get().libD("IDLE 2: $lastRecord")
                        if(null != lastRecord) {
                            lastRecord.callEndTime = System.currentTimeMillis()
                            lastRecord.save()
                            SyncCallService.enqueueWork(applicationContext, mLastRecordId)
                            mRecordCallback?.onCallEnd(lastRecord.recordId)
                            mRecordCallback = null
                        }
                    }
                    MediaRecordHelper.get(application).stopRecord()
                    internalStopListen()
                    mLastRecordId = null
                }
            }
            
            TelephonyManager.CALL_STATE_RINGING -> {
                if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                    //呼入开始
                    mCurrentState = state
                    if(!mLastRecordId.isNullOrEmpty()) {
                        val callRecord = findRecordById(mLastRecordId!!)
                        if(null != callRecord) {
                            callRecord.callStartTime = System.currentTimeMillis()
                            callRecord.save()
                            MediaRecordHelper.get(application).startRecord(callRecord)
                            mRecordCallback?.onCallIn(callRecord.recordId)
                        }
                    }
                }
            }
            
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                    //呼出开始
                    mCurrentState = state
                    if(!mLastRecordId.isNullOrEmpty()) {
                        val callRecord = findRecordById(mLastRecordId!!)
                        if(null != callRecord) {
                            callRecord.callOffHookTime = System.currentTimeMillis()
                            callRecord.save()
                            MediaRecordHelper.get(application).startRecord(callRecord)
                            mRecordCallback?.onCallOut(callRecord.recordId)
                            mRecordCallback?.onCallOffHook(callRecord.recordId)
                        }
                    }
                } else if(TelephonyManager.CALL_STATE_RINGING == mCurrentState) {
                    //呼入接听
                    mCurrentState = state
                    if(!mLastRecordId.isNullOrEmpty()) {
                        val callRecord = findRecordById(mLastRecordId!!)
                        if(null != callRecord) {
                            callRecord.callOffHookTime = System.currentTimeMillis()
                            callRecord.save()
                            MediaRecordHelper.get(application).startRecord(callRecord)
                            mRecordCallback?.onCallOffHook(callRecord.recordId)
                        }
                    }
                }
            }
        }
    }
    
    @Synchronized
    private fun makeRecord(phoneNumber: String, callType: CallType): CallRecord {
        val record = CallRecord()
        if(CallType.CallOut == callType) {
            record.callStartTime = System.currentTimeMillis()
        }
        record.recordId = createRecordId()
        record.callType = callType.ordinal
        record.phoneNumber = phoneNumber
        record.hasChinaTELECOM = TelephonyCenter.get().hasTelecomCard()
        record.save()
        mLastRecordId = record.recordId
        mRecordCallback?.onRecordIdCreated(record)
        return record
    }
    
    @Synchronized
    private fun createRecordId() = makeRandomUUID()
    
    private fun internalStartListen() {
        TelephonyCenter.get().libW("internalStartListen")
        val telephonyService = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyService.listen(mStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }
    
    private fun internalStopListen() {
        TelephonyCenter.get().libW("internalStopListen")
        val telephonyService = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyService.listen(mStateListener, PhoneStateListener.LISTEN_NONE)
    }
    
    override fun onCreate() {
        super.onCreate()
        
        mLinkerBinder = AndLinkerBinder.Factory.newBinder()
        mLinkerBinder?.registerObject(mRecordService)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = mLinkerBinder
    
    override fun onDestroy() {
        internalStopListen()
        mLinkerBinder?.unRegisterObject(mRecordService)
        super.onDestroy()
    }
    
}
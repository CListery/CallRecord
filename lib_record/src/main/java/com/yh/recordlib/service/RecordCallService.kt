package com.yh.recordlib.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.codezjx.andlinker.AndLinkerBinder
import com.yh.appbasic.logger.logD
import com.yh.appbasic.logger.logW
import com.yh.krealmextensions.save
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
        
        private const val EMPTY_RECORD_ID = ""
        
        @JvmStatic
        private var mLastRecordId: String = EMPTY_RECORD_ID
    }
    
    private var mLinkerBinder: AndLinkerBinder? = null
    private var mRecordCallback: IRecordCallback? = null
    private val mRecordService = object : IRecordService {
        override fun resumeLastRecord(recordId: String) {
            mLastRecordId = recordId
        }
        
        override fun startListen(callNumber: String, callType: CallType) {
            logW("startListen", loggable = TelephonyCenter.get())
            makeRecord(callNumber, callType)
            internalStartListen()
        }
        
        override fun stopListen() {
            logW("stopListen: $mCurrentState", loggable = TelephonyCenter.get())
            internalStopListen()
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
            logD("onCallStateChanged: $state - $phoneNumber", loggable = TelephonyCenter.get())
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
                    logD("IDLE 1: $mLastRecordId", loggable = TelephonyCenter.get())
                    if(EMPTY_RECORD_ID != mLastRecordId) {
                        val cr = findRecordById(mLastRecordId)
                        logD("IDLE 2: $cr", loggable = TelephonyCenter.get())
                        if(null != cr) {
                            cr.callEndTime = System.currentTimeMillis()
                            cr.save()
                            SyncCallService.enqueueWorkById(applicationContext, cr.recordId)
                            mRecordCallback?.onCallEnd(cr.recordId)
                        }
                        mRecordCallback = null
                    }
                    MediaRecordHelper.get(application).stopRecord()
                    internalStopListen()
                    mLastRecordId = EMPTY_RECORD_ID
                }
            }
            
            TelephonyManager.CALL_STATE_RINGING -> {
                if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                    //呼入开始
                    mCurrentState = state
                    if(EMPTY_RECORD_ID != mLastRecordId) {
                        val cr = findRecordById(mLastRecordId)
                        if(null != cr) {
                            cr.callStartTime = System.currentTimeMillis()
                            cr.save()
                            MediaRecordHelper.get(application).startRecord(cr)
                            mRecordCallback?.onCallIn(cr.recordId)
                        }
                    }
                }
            }
            
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                    //呼出开始
                    mCurrentState = state
                    if(EMPTY_RECORD_ID != mLastRecordId) {
                        val cr = findRecordById(mLastRecordId)
                        if(null != cr) {
                            cr.callOffHookTime = System.currentTimeMillis()
                            cr.save()
                            MediaRecordHelper.get(application).startRecord(cr)
                            mRecordCallback?.onCallOut(cr.recordId)
                            mRecordCallback?.onCallOffHook(cr.recordId)
                        }
                    }
                } else if(TelephonyManager.CALL_STATE_RINGING == mCurrentState) {
                    //呼入接听
                    mCurrentState = state
                    if(EMPTY_RECORD_ID != mLastRecordId) {
                        val cr = findRecordById(mLastRecordId)
                        if(null != cr) {
                            cr.callOffHookTime = System.currentTimeMillis()
                            cr.save()
                            MediaRecordHelper.get(application).startRecord(cr)
                            mRecordCallback?.onCallOffHook(cr.recordId)
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
            // 避免监听不到通话状态改变回调
            record.callOffHookTime = record.callStartTime
        }
        val recordId = createRecordId(phoneNumber)
        record.recordId = recordId
        record.callType = callType.ordinal
        record.phoneNumber = phoneNumber
        record.hasChinaTELECOM = TelephonyCenter.get().hasTelecomCard()
        record.save()
        mLastRecordId = recordId
        mRecordCallback?.onRecordIdCreated(record)
        return record
    }
    
    @Synchronized
    private fun createRecordId(phoneNumber: String) = makeRandomUUID(phoneNumber)
    
    private fun internalStartListen() {
        logW("internalStartListen", loggable = TelephonyCenter.get())
        val telephonyService = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyService.listen(mStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }
    
    private fun internalStopListen() {
        logW("internalStopListen", loggable = TelephonyCenter.get())
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
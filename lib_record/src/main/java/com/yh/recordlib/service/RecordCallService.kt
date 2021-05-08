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
import com.yh.appinject.lifecycle.IAppForegroundEvent
import com.yh.appinject.logger.ext.libD
import com.yh.appinject.logger.ext.libW
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.entity.FakeCallRecord
import com.yh.recordlib.ext.deleteRecordById
import com.yh.recordlib.ext.findRecordById
import com.yh.recordlib.ipc.IRecordCallback
import com.yh.recordlib.ipc.IRecordService
import com.yh.recordlib.media.MediaRecordHelper
import com.yh.recordlib.utils.makeMD5UUID
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
        
        override fun startListen(callNumber: String?) {
            TelephonyCenter.get().libW("startListen")
            if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                if(null == mLastRecordId && !TextUtils.isEmpty(callNumber)) {
                    makeRecord(callNumber, CallType.CallOut)
                }
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
    }
    
    private val mStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            TelephonyCenter.get().libD("onCallStateChanged: $state - $phoneNumber")
            internalCallStateChanged(state, TelephonyCenter.get().filterGarbageInPhoneNumber(phoneNumber))
        }
    }
    
    private val mForegroundEvent = object : IAppForegroundEvent {
        override fun onForegroundStateChange(isForeground: Boolean) {
            TelephonyCenter.get().libW("onForegroundStateChange: $isForeground")
            if(isForeground) {
                unKeepAlive()
            } else {
                keepAlive()
            }
        }
    }

//    private val mCallStateReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            TelephonyCenter.get().libD("onReceive: ${intent?.action} -> ${System.currentTimeMillis()}")
//            TelephonyCenter.get().libD(
//                "onReceive: TelephonyManager.EXTRA_STATE -> ${intent?.getStringExtra(
//                    TelephonyManager.EXTRA_STATE
//                )}"
//            )
//            TelephonyCenter.get().libD(
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
                    mCurrentState = state
                    TelephonyCenter.get().libD("IDLE 1: $mLastRecordId")
                    if(null != mLastRecordId) {
                        val lastRecord = findRecordById(mLastRecordId!!)
                        TelephonyCenter.get().libD("IDLE 2: $lastRecord")
                        if(null != lastRecord) {
                            lastRecord.callEndTime = System.currentTimeMillis()
                            lastRecord.save()
                            SyncCallService.enqueueWork(
                                applicationContext, mLastRecordId
                            )
                            mRecordCallback?.onCallEnd(lastRecord.recordId)
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
                    val callRecord = makeRecord(phoneNumber, CallType.CallIn)
                    MediaRecordHelper.get(application).startRecord(callRecord)
                    mRecordCallback?.onCallIn(callRecord.recordId)
                }
            }
            
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if(TelephonyManager.CALL_STATE_IDLE == mCurrentState) {
                    //呼出开始
                    mCurrentState = state
                    val callRecord: CallRecord
                    callRecord = if(null != mLastRecordId) {
                        val targetRecord = findRecordById(mLastRecordId!!)
                        if(null != targetRecord && (TextUtils.isEmpty(phoneNumber) || TextUtils.equals(phoneNumber, targetRecord.phoneNumber))) {
                            // 如果系统未返回拨号号码，则以呼出时号码为准
                            targetRecord
                        } else {
                            // 号码不相同，删除呼出时创建的记录
                            if(null != targetRecord) {
                                deleteRecordById(targetRecord.recordId)
                            }
                            makeRecord(phoneNumber, CallType.CallOut)
                        }
                    } else {
                        makeRecord(phoneNumber, CallType.CallOut)
                    }
                    callRecord.callOffHookTime = System.currentTimeMillis()
                    callRecord.save()
                    MediaRecordHelper.get(application).startRecord(callRecord)
                    mRecordCallback?.onCallOut(callRecord.recordId)
                    mRecordCallback?.onCallOffHook(callRecord.recordId)
                } else if(TelephonyManager.CALL_STATE_RINGING == mCurrentState) {
                    //呼入接听
                    mCurrentState = state
                    if(null != mLastRecordId) {
                        var callRecord = findRecordById(mLastRecordId!!)
                        if(null == callRecord) {
                            callRecord = makeRecord(phoneNumber, CallType.CallIn)
                        }
                        callRecord.callOffHookTime = System.currentTimeMillis()
                        callRecord.save()
                        MediaRecordHelper.get(application).startRecord(callRecord)
                        mRecordCallback?.onCallOffHook(callRecord.recordId)
                    }
                }
            }
        }
    }
    
    @Synchronized
    private fun makeRecord(phoneNumber: String?, callType: CallType): CallRecord {
        val recordId: String = createRecordId()
        var recordPhoneNumber: String? = phoneNumber
        var fake = false
        if(null == recordPhoneNumber || TextUtils.isEmpty(recordPhoneNumber)) {
            fake = true
            recordPhoneNumber = createFakePhoneNumber()
            TelephonyCenter.get().libW("Fake phone number: $recordPhoneNumber")
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
        record.hasChinaTELECOM = TelephonyCenter.get().hasTelecomCard()
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
        TelephonyCenter.get().libW("internalStartListen")
        TelephonyCenter.get().registerActivityLifecycleCallbacks(mForegroundEvent)
        val telephonyService = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyService.listen(mStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }
    
    private fun internalStopListen() {
        TelephonyCenter.get().libW("internalStopListen")
        val telephonyService = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyService.listen(mStateListener, PhoneStateListener.LISTEN_NONE)
        TelephonyCenter.get().unRegisterActivityLifecycleCallbacks(mForegroundEvent)
        unKeepAlive()
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
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = mLinkerBinder
    
    override fun onDestroy() {
        internalStopListen()
        mLinkerBinder?.unRegisterObject(mRecordService)
//        unregisterReceiver(mCallStateReceiver)
        super.onDestroy()
    }
    
    private fun keepAlive() {
        startForeground(TelephonyCenter.GRAY_SERVICE_ID, TelephonyCenter.get().getNotification())
    }
    
    private fun unKeepAlive() {
        stopForeground(true)
    }
}
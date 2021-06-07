package com.yh.recordlib.entity

import com.yh.appinject.logger.ext.libW
import com.yh.recordlib.TelephonyCenter
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import io.realm.annotations.Required
import org.jetbrains.annotations.TestOnly
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

/**
 * Created by CYH on 2019-05-30 10:57
 */
@RealmClass
open class CallRecord : RealmObject {
    
    @PrimaryKey
    @Required
    var recordId: String = ""
    
    /**
     * 记录通话号码
     */
    @Required
    var phoneNumber: String = ""
    
    var callStartTime: Long = 0L
    var callOffHookTime: Long = 0L
    var callEndTime: Long = 0L
    
    fun getStartSecond(): Long {
        if(isValidTime(callStartTime)) {
            return callStartTime.div(1000)
        }
        return 0
    }
    
    fun getOffHookSecond(): Long {
        if(isValidTime(callOffHookTime)) {
            return callOffHookTime.div(1000)
        }
        return getStartSecond()
    }
    
    fun getEndSecond(): Long {
        if(isValidTime(callEndTime)) {
            return callEndTime.div(1000)
        }
        return 0
    }
    
    private fun isValidTime(millisTime: Long): Boolean {
        return (millisTime > 0)
    }
    
    var callType: Int = CallType.Unknown.ordinal
    
    val realCallType: CallType get() = CallType.typeOf(callType)
    
    var audioFilePath: String? = ""
    
    /**
     * 是否以及与系统数据库同步
     */
    var synced: Boolean = false
    
    /**
     * 系统数据库中该通话记录的id
     */
    var callLogId: Long = 0L
    
    /**
     * 系统数据库中该通话记录的持续时长
     */
    var duration: Long = 0L
    
    fun getDurationMillis() = duration * 1000
    
    /**
     * 是否需要重新计算通话记录持续时长
     */
    var needRecalculated: Boolean = false
    
    /**
     * 重新计算持续时长标志位
     */
    var recalculated: Boolean = false
    
    /**
     * 系统数据库中该通话记录的类型
     * 0 - 未知
     * 1 - 呼入 - CallLog.Calls.INCOMING_TYPE
     * 2 - 呼出  - CallLog.Calls.OUTGOING_TYPE
     * 3 - 未接 - CallLog.Calls.MISSED_TYPE
     * 4 - 语言邮箱 - CallLog.Calls.VOICEMAIL_TYPE
     * 5 - 拒接 - CallLog.Calls.REJECTED_TYPE
     * 6 - 系统拦截 - CallLog.Calls.BLOCKED_TYPE
     */
    var callState: Int = 0
    
    /**
     * 系统数据库中对应PHONE_ACCOUNT_ID
     * 0,1,2...
     */
    var phoneAccountId: Int? = 0
    var mccMnc: String? = ""
    
    /**
     * true: 系统记录中找不到相应的记录
     */
    var isNoMapping: Boolean = false
    
    /**
     * true: 长时间不能在系统数据库中找到该条记录
     */
    var isDeleted: Boolean = false
    
    /**
     * 是否已手动同步过
     */
    var isManualSynced: Boolean = false
    
    /**
     * 拨打时是否有电信卡
     */
    var hasChinaTELECOM: Boolean = false
    
    constructor() : super()
    
    @TestOnly
    constructor(
        recordId: String,
        phoneNumber: String,
        callStartTime: Long,
        callOffHookTime: Long,
        callEndTime: Long,
        callType: Int,
        audioFilePath: String? = null,
        synced: Boolean,
        callLogId: Long,
        duration: Long,
        needRecalculated: Boolean = false,
        recalculated: Boolean,
        callState: Int,
        phoneAccountId: Int? = -1,
        mccMnc: String? = "",
        isNoMapping: Boolean,
        isDeleted: Boolean,
        isManualSynced: Boolean = false,
        hasChinaTELECOM: Boolean = false
    ) : super() {
        this.recordId = recordId
        this.phoneNumber = phoneNumber
        this.callStartTime = callStartTime
        this.callOffHookTime = callOffHookTime
        this.callEndTime = callEndTime
        this.callType = callType
        this.audioFilePath = audioFilePath
        this.synced = synced
        this.callLogId = callLogId
        this.duration = duration
        this.needRecalculated = needRecalculated
        this.recalculated = recalculated
        this.callState = callState
        this.phoneAccountId = phoneAccountId
        this.mccMnc = mccMnc
        this.isNoMapping = isNoMapping
        this.isDeleted = isDeleted
        this.isManualSynced = isManualSynced
        this.hasChinaTELECOM = hasChinaTELECOM
    }
    
    /**
     * App 被系统回收，没有获取到结束时间
     */
    fun recalculateTime() {
        if(callEndTime <= 0) {
            if(callOffHookTime <= callStartTime) {
                callOffHookTime = callStartTime
            }
            callEndTime = callOffHookTime + getDurationMillis()
        }
    }
    
    fun recalculateDuration(originStartTime: Long, systemCallRecord: SystemCallRecord) {
        TelephonyCenter.get().libW("$synced - $recalculated - $phoneAccountId")
        if(!synced) {
            return
        }
        if(recalculated) {
            return
        }
    
        if(CallType.CallOut != realCallType) {
            return
        }
    
        if(phoneAccountId ?: -1 < 0) {
            //未能获取到卡槽信息
            if(hasChinaTELECOM) {
                internalRecalculateDuration(originStartTime, systemCallRecord)
            }
        } else {
            if(checkNeedRecalculate()) {
                internalRecalculateDuration(originStartTime, systemCallRecord)
            }
        }
    }
    
    /**
     * 大于 80 秒则认定为接通就不需要再计算
     */
    private fun internalRecalculateDuration(originStartTime: Long, systemCallRecord: SystemCallRecord) {
        TelephonyCenter.get().libW("d:$duration - e:$callEndTime - ss:${systemCallRecord.date} - os:$originStartTime")
        
        if(duration in 1..91) {
            // 经测试最长的假通话时长可达到90秒
            needRecalculated = true
            
            if(systemCallRecord.date - originStartTime > 10000) {
                // 系统通话记录开始时间大于本地通话记录开始时间
                // 说明从开始呼出到接通期间有等待时间，即开始时间被系统重置过，通话时长是可信的
                // 这里10s是考虑到网络等待时间，有可能先处于连接网络阶段，然后又开始错误计时
                // 处于网络等待阶段是不计时的，所以取一个较长的10s来作为时间阀值
                // 正常接通的情况下10s通话时长过短，可以过滤掉
                recalculated = true
                return
            }
            
            val tmpDuration = min(callEndTime - callOffHookTime, callEndTime - callStartTime).div(1000)
            TelephonyCenter.get().libW("t:$tmpDuration")
            if(duration == tmpDuration) {
                //一开始拨打就接通的情况是电信卡在安卓机上的 bug
                duration = 0
                recalculated = true
            } else if(duration < tmpDuration) {
                if(tmpDuration - duration <= 2) {
                    //接通过快也判定为电信卡bug
                    duration = 0
                    recalculated = true
                }
            }
        }
    }
    
    private fun checkNeedRecalculate(): Boolean {
        val subscriptionId = phoneAccountId ?: -1
        TelephonyCenter.get().libW("checkNeedRecalculate subId: $subscriptionId")
        var targetSimOperator = TelephonyCenter.get().getSimOperator(subscriptionId)
        
        if(TelephonyCenter.SimOperator.Unknown == targetSimOperator) {
            val allSimOperator = TelephonyCenter.get().getAllSimOperator()
            if(allSimOperator.isEmpty()) {
                return false
            }
            
            targetSimOperator = if(allSimOperator.size > 1) {
                if(subscriptionId > 1) {
                    allSimOperator[1]
                } else {
                    allSimOperator[0]
                }
            } else {
                allSimOperator[0]
            }
        }
        
        this.mccMnc = targetSimOperator.mccMnc
        
        if(TelephonyCenter.SimOperator.ChinaTELECOM != targetSimOperator) {
            return false
        }
        return true
    }
    
    fun getFormatDate(): String {
        return SimpleDateFormat("yyyy.M.d HH:mm", Locale.CHINESE).format(callStartTime)
    }
    
    override fun toString(): String {
        return "CallRecord(recordId='$recordId', phoneNumber='$phoneNumber', callStartTime=$callStartTime, callOffHookTime=$callOffHookTime, callEndTime=$callEndTime, callType=$callType, audioFilePath=$audioFilePath, synced=$synced, callLogId=$callLogId, duration=$duration, needRecalculated=$needRecalculated, recalculated=$recalculated, callState=$callState, phoneAccountId=$phoneAccountId, mccMnc=$mccMnc, isNoMapping=$isNoMapping, isDeleted=$isDeleted, isManualSynced=$isManualSynced, hasChinaTELECOM=$hasChinaTELECOM)"
    }
}
package com.yh.recordlib.entity

import android.os.Build
import com.vicpin.krealmextensions.save
import com.yh.appinject.logger.ext.libW
import com.yh.recordlib.TelephonyCenter
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import io.realm.annotations.Required
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

/**
 * Created by CYH on 2019-05-30 10:57
 */
@RealmClass
open class CallRecord : RealmObject() {

    @PrimaryKey
    @Required
    var recordId: String = ""

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
        return 0
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

    var audioFilePath: String? = ""

    /**
     * phone number无法正常获取时该值为true
     */
    var isFake: Boolean = false

    /**
     * 是否以及与系统数据库同步
     */
    var synced: Boolean = false
    /**
     * 系统数据库中该通话记录的id
     */
    open var callLogId: Long = 0L
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

    fun recalculateDuration() {
        TelephonyCenter.get().libW("$synced - $recalculated - $phoneAccountId")
        if(!synced) {
            return
        }
        if(recalculated) {
            return
        }

        if(phoneAccountId ?: -1 < 0) {
            //未能获取到卡槽信息
            if(hashTelecomCard()) {
                internalRecalculateDuration()
            }
        } else {
            if(checkNeedRecalculate()) {
                internalRecalculateDuration()
            }
        }
    }

    /**
     * 大于 1 分钟则认定为接通就不需要再计算
     */
    private fun internalRecalculateDuration() {
        TelephonyCenter.get().libW("d:$duration - e:$callEndTime")
        if(duration in 1..60) {
            needRecalculated = true
            if(callEndTime <= 0) {
                //App 被系统回收，没有获取到结束时间，并且没获取到有效的 lastModify
                callEndTime = if(callOffHookTime > 0) {
                    callOffHookTime + getDurationMillis()
                } else {
                    callStartTime + getDurationMillis()
                }
                //为了防止 duration == tmpDuration 增加 5s 偏移
                callEndTime += 5000
            }
            val tmpDuration = min(
                callEndTime - callOffHookTime, callEndTime - callStartTime
            ).div(1000)
            TelephonyCenter.get().libW("t:$tmpDuration")
            if(duration == tmpDuration) {
                if(Build.VERSION.SDK_INT < 27){
                    //某些高版本机型已经修复了电信卡一开始拨打就记时的问题
                    //一开始拨打就接通的情况是电信卡在安卓机上的 bug
                    duration = 0
                    recalculated = true
                }
            }/*
            暂不校验接通过快的情况
            else if(duration < tmpDuration) {
                if(tmpDuration - duration <= 2) {
                    //接通过快视为已拨打就接通的情况
                    duration = 0
                    recalculated = true
                }
            }*/
        }
    }

    private fun hashTelecomCard(): Boolean {
        return TelephonyCenter.get().getAllSimOperator()
            .contains(TelephonyCenter.SimOperator.ChinaTELECOM)
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
        return "CallRecord(recordId='$recordId', phoneNumber='$phoneNumber', callStartTime=$callStartTime, callOffHookTime=$callOffHookTime, callEndTime=$callEndTime, callType=$callType, audioFilePath=$audioFilePath, isFake=$isFake, synced=$synced, callLogId=$callLogId, duration=$duration, recalculated=$recalculated, callState=$callState, phoneAccountId=$phoneAccountId, mccMnc=$mccMnc, isNoMapping=$isNoMapping, isDeleted=$isDeleted)"
    }
}
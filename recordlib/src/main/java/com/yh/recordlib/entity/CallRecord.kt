package com.yh.recordlib.entity

import com.vicpin.krealmextensions.save
import com.yh.recordlib.TelephonyCenter
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import io.realm.annotations.Required
import timber.log.Timber
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
        if(!synced) {
            return
        }
        if(recalculated) {
            return
        }

        needRecalculated = checkNeedRecalculate()
        if(!needRecalculated) {
            save()
            return
        }
        recalculated = true

        if(duration > 0) {
            val tmpDuration = min(getEndSecond() - getOffHookSecond(), getEndSecond() - getStartSecond())
            Timber.w("recalculateDuration: $duration -> $tmpDuration")
            if (duration == tmpDuration) {
                //未接通
                duration = 0
            } else if (duration < tmpDuration) {
                if (tmpDuration - duration <= 2) {
                    //未接通
                    duration = 0
                }
            }
        }
        save()
    }

    private fun checkNeedRecalculate(): Boolean {
        val subscriptionId = phoneAccountId ?: 0
        Timber.w("checkNeedRecalculate subId: $subscriptionId")
        var targetSimOperator = TelephonyCenter.get()
            .getSimOperator(subscriptionId)

        if(TelephonyCenter.SimOperator.Unknown == targetSimOperator) {
            val allSimOperator = TelephonyCenter.get()
                .getAllSimOperator()
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
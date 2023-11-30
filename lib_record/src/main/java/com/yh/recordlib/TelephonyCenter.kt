package com.yh.recordlib

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.text.TextUtils
import androidx.annotation.ArrayRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
import androidx.core.content.getSystemService
import com.kotlin.runCatchingSafety
import com.kotlin.safeFieldGet
import com.kotlin.safeGet
import com.yh.appbasic.logger.ILogger
import com.yh.appbasic.logger.LogOwner
import com.yh.appbasic.logger.logW
import com.yh.appbasic.share.AppBasicShare
import com.yh.appinject.InjectHelper
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.ext.runOnApiAbove
import com.yh.recordlib.inject.IRecordAppInject
import com.yh.recordlib.ipc.IRecordCallback
import com.yh.recordlib.ipc.IRecordService
import com.yh.recordlib.utils.isMIUI
import kotlin.concurrent.thread

/**
 * Created by CYH on 2019-06-03 10:09
 *
 * 某些低版本单卡设备在保存卡二时 subid 会大于等于 2
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class TelephonyCenter private constructor() : InjectHelper<IRecordAppInject>(), ILogger {
    
    companion object {
        
        private const val TAG = "TelephonyCenter"
        
        const val GRAY_SERVICE_ID = 0x991
        private const val NOTIFY_CHANNEL_ID = "sync_call_record_channel_id_1"
        private const val NOTIFY_CHANNEL_NAME = "同步通话记录"
        private const val NOTIFY_NAME = "不要关闭该通知"
        private const val NOTIFY_CONTENT = "尊园之星正在同步通话记录"
        
        @JvmStatic
        private val mInstance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { TelephonyCenter() }
        
        @JvmStatic
        fun get() = mInstance
        
        @JvmStatic
        private fun getStringArray(
            @ArrayRes
            id: Int
        ): Array<String> {
            return AppBasicShare.context.resources.getStringArray(id)
        }
    }
    
    enum class SimOperator(val operatorName: String, val operatorArray: Array<String>) {
        ChinaTELECOM(
            "中国电信",
            arrayOf(
                "46003",
                "46005",
                "46011",
            )
        ),
        ChinaMOBILE(
            "中国移动",
            arrayOf(
                "46000",
                "46002",
                "46004",
                "46007",
                "46008",
            )
        ),
        ChinaUNICOM(
            "中国联通",
            arrayOf(
                "46001",
                "46006",
                "46009",
            )
        ),
        ChinaBroadnet("中国广电", arrayOf("49015")),
        ChinaTieTong("中国铁通", arrayOf("46020")),
        Unknown("未知运营商", arrayOf());
        
        var mccMnc: String = "unknown"
        
        override fun toString(): String {
            return "$operatorName(mccMnc='$mccMnc')"
        }
        
        companion object {
            
            fun from(info: SubInfo): SimOperator {
                return (values().find {
                    it.mccMnc == info.mcc + info.mnc
                }
                    ?: values().find {
                        it.operatorName == info.carrier
                    }
                    ?: Unknown).apply { mccMnc = info.mcc + info.mnc }
            }
            
            fun from(mccmnc: String?): SimOperator {
                if(mccmnc.isNullOrEmpty()) {
                    return Unknown
                }
                return (values().find {
                    it.mccMnc == mccmnc
                }
                    ?: Unknown).apply { this.mccMnc = mccmnc }
            }
        }
    }
    
    data class SubInfo(
        val displayName: String,
        val iccId: String,
        val number: String,
        val subId: Int,
        val mcc: String,
        val mnc: String,
        val carrier: String = "",
        val slotId: Int,
        val other: Map<String, Any?>
    ) {
        
        companion object {
            
            fun from(clazz: Class<*>, data: Any?): SubInfo {
                var number = clazz.safeFieldGet<Any>("number", data).toString()
                if(number.length > 11) {
                    number = get().filterGarbageInPhoneNumber(number)
                    if(number.length > 11) {
                        number = number.substring(number.length - 11)
                    }
                }
                
                return SubInfo(
                    displayName = clazz.safeFieldGet<Any>("displayName", data).toString(),
                    iccId = clazz.safeFieldGet<Any>("iccId", data).toString(),
                    number = number,
                    subId = clazz.safeFieldGet<Any>("subId", data).toString().toInt(),
                    mcc = clazz.safeFieldGet<Any>("mcc", data).toString(),
                    mnc = clazz.safeFieldGet<Any>("mnc", data).toString(),
                    slotId = clazz.safeFieldGet<Any>("slotId", data).toString().toInt(),
                    other = clazz.declaredFields.filterNot { it.name == "CREATOR" }.associate {
                        it.name to it.safeGet<Any>(data)
                    }
                )
            }
            
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
            fun from(data: SubscriptionInfo): SubInfo {
                val cSubscriptionInfo = data::class.java
                
                var mcc = ""
                var mnc = ""
                
                runOnApiAbove(29, {
                    mcc = cSubscriptionInfo.getDeclaredMethod("getMccString")
                        .runCatchingSafety { invoke(data) }
                        .getOrNull()
                        .toString()
                    mnc = cSubscriptionInfo.getDeclaredMethod("getMncString")
                        .runCatchingSafety { invoke(data) }
                        .getOrNull()
                        .toString()
                }, otherwise = @Suppress("DEPRECATION") {
                    mcc = data.mcc.toString()
                    mnc = data.mnc.toString()
                })
                
                var number = data.number
                if(number.length > 11) {
                    number = get().filterGarbageInPhoneNumber(number)
                    if(number.length > 11) {
                        number = number.substring(number.length - 11)
                    }
                }
                
                return SubInfo(
                    displayName = data.displayName.toString(),
                    iccId = data.iccId,
                    number = number,
                    subId = data.subscriptionId,
                    mcc = mcc,
                    mnc = mnc,
                    carrier = data.carrierName.toString(),
                    slotId = data.simSlotIndex,
                    other = cSubscriptionInfo.declaredMethods.filterNot {
                        it.name in arrayOf(
                            "toString",
                            "writeToParcel",
                            "hashCode",
                            "equals",
                        )
                    }.associate {
                        it.name to it.runCatchingSafety(false) { invoke(data) }.getOrNull()
                    }
                )
            }
        }
        
    }
    
    val allSubInfo: List<SubInfo> by lazy { getAllSubInfoList(AppBasicShare.context) }
    
    // packages/providers/ContactsProvider/src/com/android/providers/contacts/CallLogProvider.java
    var callsProjections: Array<String>? = null
    fun safeProjections(): Array<String>? {
        if(callsProjections.isNullOrEmpty()) {
            return null
        }
        return callsProjections
    }
    
    override fun onCreateLogOwner(logOwner: LogOwner) {
        
    }
    
    override fun init() {
        // initNotification()
        thread { allSubInfo.size }
    }
    
    private fun getAllSubInfoList(context: Context): List<SubInfo> {
        return if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            val subscriptionManager = context.getSystemService<SubscriptionManager>()!!
            val activeSubscriptionInfoList = run {
                val cSubscriptionManager = Class.forName("android.telephony.SubscriptionManager")
                val mGetAllSubscriptionInfoList = cSubscriptionManager.getDeclaredMethod("getActiveSubscriptionInfoList")
                mGetAllSubscriptionInfoList.invoke(subscriptionManager) as List<*>
            }.filterIsInstance<SubscriptionInfo>()
            
            activeSubscriptionInfoList.map { subInfo ->
                SubInfo.from(subInfo)
            }
        } else {
            val cSubscriptionManager = Class.forName("android.telephony.SubscriptionManager")
            val mGetAllSubInfoList = cSubscriptionManager.getDeclaredMethod("getActiveSubInfoList")
            val allSubInfoList = mGetAllSubInfoList.invoke(null) as List<*>
            val cSubInfoRecord = Class.forName("android.telephony.SubInfoRecord")
            
            allSubInfoList.filterIsInstance(cSubInfoRecord).map { subInfo ->
                SubInfo.from(cSubInfoRecord, subInfo)
            }
        }
    }
    
    private fun initNotification() {
        val context = AppBasicShare.context
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        
        if(Build.VERSION.SDK_INT >= 26) {
            val notificationChannel = NotificationChannel(
                NOTIFY_CHANNEL_ID, NOTIFY_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.description = "尊园之星通话记录同步通知，禁用后将无法同步系统数据库，将无法进行报备操作!"
            notificationChannel.setSound(null, null)
            notificationChannel.enableLights(false)
            notificationChannel.enableVibration(false)
            notificationManagerCompat.createNotificationChannel(notificationChannel)
            if(null != notificationManagerCompat.getNotificationChannel("sync_call_record_channel_id_01")) {
                notificationManagerCompat.deleteNotificationChannel("sync_call_record_channel_id_01")
            }
        }
    }
    
    fun setupRecordConfigure(configure: RecordConfigure) {
        inject.setRecordConfigure(configure)
    }
    
    fun getRecordConfigure() = inject.getRecordConfigure()
    
    fun getNotification(): Notification {
        val builder = NotificationCompat.Builder(AppBasicShare.context, NOTIFY_CHANNEL_ID)
        
        val intent = PendingIntent.getActivity(
            AppBasicShare.context, 0, Intent(), PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        builder.setContentTitle(NOTIFY_NAME) //设置通知栏标题
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(intent)
            .setFullScreenIntent(intent, false)
            .setContentText(NOTIFY_CONTENT)
            .setTicker("$NOTIFY_NAME:$NOTIFY_CONTENT") //通知首次出现在通知栏，带上升动画效果的
            .setWhen(System.currentTimeMillis()) //通知产生的时间，会在通知信息里显示，一般是系统获取到的时间
            .setSound(null)
            .setVibrate(null)
            .setLights(0, 0, 0)
            .setAutoCancel(false)
            .setSmallIcon(inject.getNotificationIcon()) //设置通知小 ICON
        
        //        builder.setCategory(NotificationCompat.CATEGORY_SERVICE)
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        val notify = builder.build()
        notify.flags = notify.flags or NotificationCompat.FLAG_FOREGROUND_SERVICE
        return notify
    }
    
    /**
     * 获取 SIM 卡数量
     */
    fun getSimCount() = allSubInfo.size
    
    /**
     * 获取运营商，默认获取卡一
     */
    fun getSimOperator(slotId: Int = 0) = allSubInfo.find { it.slotId == slotId }?.let { SimOperator.from(it) }
        ?: SimOperator.Unknown
    
    /**
     * 获取设备上所有SIM卡运营商
     */
    fun getAllSimOperator() = allSubInfo.map { SimOperator.from(it) }
    
    /**
     * 通过 MCCMNC 解析运营商
     */
    fun parseSimOperator(mccmnc: String?) = SimOperator.from(mccmnc)
    
    /**
     * 获取对应卡槽的电话号码
     */
    fun getPhoneNumber(slotId: Int = 0) = allSubInfo.find { it.slotId == slotId }?.number
        ?: ""
    
    /**
     * 是否有电信卡
     */
    fun hasTelecomCard(): Boolean = getAllSimOperator().contains(SimOperator.ChinaTELECOM)
    
    /**
     * 过滤掉某些定制系统的电话号码中携带的垃圾字符串
     */
    fun filterGarbageInPhoneNumber(phoneNumber: String?): String {
        if(null == phoneNumber || TextUtils.isEmpty(phoneNumber)) {
            return ""
        }
        var result: String = phoneNumber
        val garbageStr = arrayOf(" ", "-")
        garbageStr.forEach { garbage ->
            result = result.replace(garbage, "")
        }
        return result
    }
    
    fun call(
        context: Context,
        callNumber: String?,
        iRecordService: IRecordService?,
        iRecordCallback: IRecordCallback?
    ) {
        if(null == callNumber || null == iRecordService) {
            logW("call fail!", loggable = this)
            return
        }
        if(PermissionChecker.PERMISSION_GRANTED != PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            )) {
            showTipMsg("请打开拨打电话的权限")
            return
        }
        if(null != iRecordCallback) {
            iRecordService.registerRecordCallback(iRecordCallback)
        }
        iRecordService.startListen(callNumber, CallType.CallOut)
        val intent = Intent(Intent.ACTION_CALL) // 必须使用call方式，否则无法校验电信卡时长
        intent.data = Uri.parse("tel:$callNumber")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        if(isMIUI) {
            showTipMsg("如小米手机无法拨打请进入系统设置手动开启拨打电话权限")
        }
    }
    
    fun listenCall(callNumber: String?, iRecordService: IRecordService?, iRecordCallback: IRecordCallback?) {
        if(null == callNumber || null == iRecordService) {
            logW("call fail!", loggable = this)
            return
        }
        if(null != iRecordCallback) {
            iRecordService.registerRecordCallback(iRecordCallback)
        }
        iRecordService.startListen(callNumber, CallType.CallIn)
    }
    
}
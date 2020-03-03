package com.yh.recordlib

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ServiceManager
import android.os.SystemProperties
import android.telephony.TelephonyManager
import android.text.TextUtils
import androidx.annotation.ArrayRes
import com.android.internal.telephony.IPhoneSubInfo
import com.android.internal.telephony.ITelephony
import com.android.internal.telephony.ITelephonyRegistry
import com.yh.recordlib.cons.TelephonyProperties
import com.yh.recordlib.ipc.IRecordService
import com.yh.recordlib.lang.InvalidSubscriberIdException
import timber.log.Timber

/**
 * Created by CYH on 2019-06-03 10:09
 *
 * 某些低版本单卡设备在保存卡二时 subid 会大于等于 2
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class TelephonyCenter private constructor() {

    companion object {
        private const val TAG = "TelephonyCenter"

        @JvmStatic
        private var mInstance: TelephonyCenter? = null

        @Synchronized
        @JvmStatic
        fun get(): TelephonyCenter {
            if(null == mInstance) {
                mInstance = TelephonyCenter()
            }
            return mInstance!!
        }

        @JvmStatic
        private fun getStringArray(
            @ArrayRes
            id: Int
        ): Array<String> {
            return CallRecordController.get()
                .application.resources.getStringArray(id)
        }

        init {
            disableAndroidPWarning()
        }

        @SuppressLint("PrivateApi")
        private fun disableAndroidPWarning() {
            if (Build.VERSION.SDK_INT < 28) {
                return
            }
            Timber.w("try disableAndroidPWarning!")
            try {
                val aClass = Class.forName("android.content.pm.PackageParser\$Package")
                val declaredConstructor = aClass.getDeclaredConstructor(String::class.java)
                declaredConstructor.isAccessible = true
            } catch (e: Exception) {
            }

            try {
                val cls = Class.forName("android.app.ActivityThread")
                val declaredMethod = cls.getDeclaredMethod("currentActivityThread")
                declaredMethod.isAccessible = true
                val activityThread = declaredMethod.invoke(null)
                val mHiddenApiWarningShown = cls.getDeclaredField("mHiddenApiWarningShown")
                mHiddenApiWarningShown.isAccessible = true
                mHiddenApiWarningShown.setBoolean(activityThread, true)
            } catch (e: Exception) {
            }
        }
    }

    enum class SimOperator(val operatorName: String, val operatorArray: () -> Array<String>) {
        ChinaTELECOM("中国电信", { getStringArray(R.array.MCCMNC_China_TELECOM) }),
        ChinaMOBILE("中国移动", { getStringArray(R.array.MCCMNC_China_MOBILE) }),
        ChinaUNICOM("中国联通", { getStringArray(R.array.MCCMNC_China_UNICOM) }),
        ChinaTieTong("中移铁通", { getStringArray(R.array.MCCMNC_China_Tietong) }),
        Unknown("未知运营商", { arrayOf() });

        var mccMnc: String = "unknown"
    }

    /**
     * Enum indicating multisim variants
     *  DSDS - Dual SIM Dual Standby
     *  DSDA - Dual SIM Dual Active
     *  TSTS - Triple SIM Triple Standby
     * @hide
     */
    enum class MultiSimVariants {

        DSDS,
        DSDA,
        TSTS,
        UNKNOWN
    }

    private val mTM: TelephonyManager? by lazy {
        CallRecordController.get().application.getSystemService(
            Context.TELEPHONY_SERVICE
        ) as? TelephonyManager
    }
    private val mITelephony: ITelephony? by lazy { initITelephony() }
    private val mIPhoneSubInfo: IPhoneSubInfo? by lazy { initIPhoneSubInfo() }
    private val mITelephonyRegister: ITelephonyRegistry? by lazy { initITelephonyRegister() }

    private fun initITelephony(): ITelephony? {
        var iTelephony: ITelephony? =
            ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE))
        if(null != iTelephony) {
            Timber.w("initITelephony DONE! -> $iTelephony")
            return iTelephony
        }
        try {
            val getITelephony = TelephonyManager::class.java.getDeclaredMethod("getITelephony")
            getITelephony.isAccessible = true
            iTelephony = getITelephony.invoke(mTM) as? ITelephony
            Timber.w("initITelephony DONE! -> $iTelephony")
        } catch(e: Exception) {
            Timber.e(e)
        }
        return iTelephony
    }

    private fun initIPhoneSubInfo(): IPhoneSubInfo? {
        var iPhoneSubInfo: IPhoneSubInfo? = IPhoneSubInfo.Stub.asInterface(
            ServiceManager.getService("iphonesubinfo")
        )
        if(null != iPhoneSubInfo) {
            Timber.w("initIPhoneSubInfo DONE! -> $iPhoneSubInfo")
            return iPhoneSubInfo
        }
        try {
            val getSubscriberInfo =
                TelephonyManager::class.java.getDeclaredMethod("getSubscriberInfo")
            getSubscriberInfo.isAccessible = true
            iPhoneSubInfo = getSubscriberInfo.invoke(mTM) as? IPhoneSubInfo
            Timber.w("initIPhoneSubInfo DONE! -> $iPhoneSubInfo")
        } catch(e: Exception) {
            Timber.e(e)
        }
        return iPhoneSubInfo
    }

    @SuppressLint("PrivateApi")
    private fun initITelephonyRegister(): ITelephonyRegistry? {
        var iTelephonyRegistry: ITelephonyRegistry? = ITelephonyRegistry.Stub.asInterface(
            ServiceManager.getService("telephony.registry")
        )
        if(null != iTelephonyRegistry) {
            Timber.w("initITelephonyRegister DONE! -> $iTelephonyRegistry")
            return iTelephonyRegistry
        }
        try {
            val getTelephonyRegistry = TelephonyManager::class.java.getDeclaredMethod("getTelephonyRegistry")
            getTelephonyRegistry.isAccessible = true
            iTelephonyRegistry = getTelephonyRegistry.invoke(mTM) as? ITelephonyRegistry
            Timber.w("initITelephonyRegister DONE! -> $iTelephonyRegistry")
        } catch(e: Exception) {
            Timber.e(e)
        }
        return iTelephonyRegistry
    }

    fun getTM() = mTM
    fun getITelephony() = mITelephony
    fun getSubscriberInfo() = mIPhoneSubInfo
    fun getTelephonyRegistry() = mITelephonyRegister

    /**
     * 获取运营商，默认获取卡一
     */
    fun getSimOperator(phoneId: Int = 0): SimOperator {
        return parseSimOperator(getMccMncBySubscriptionId(phoneId))
    }

    /**
     * 获取 MCCMNC，默认获取卡一
     */
    fun getMccMncBySubscriptionId(phoneId: Int = 0, defaultVal: String = ""): String {
        var mccMnc = defaultVal
        val prop = SystemProperties.get(
            TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, defaultVal
        )
        if(!TextUtils.isEmpty(prop)) {
            val values = prop.split(",")
            if(phoneId >= 0 && phoneId < values.size) {
                mccMnc = values[phoneId]
            }
        }

        if(TextUtils.isEmpty(mccMnc)) {
            val subscriptionId = getValidPhoneIdBySubid(phoneId)
            mccMnc = when(subscriptionId) {
                0 -> SystemProperties.get(
                    TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, defaultVal
                )
                1 -> SystemProperties.get(
                    TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC + ".2", defaultVal
                )
                else -> defaultVal
            }
        }
        return if(TextUtils.isEmpty(mccMnc)) defaultVal else mccMnc
    }

    /**
     * 获取设备上所有SIM卡运营商
     */
    fun getAllSimOperator(): ArrayList<SimOperator> {
        val allMccMnc = arrayListOf<String>()
        val prop = SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, "")
        if(!TextUtils.isEmpty(prop)) {
            prop.split(",").forEach {
                allMccMnc.add(it)
            }
        }

        //某些低版本的机器会支持双卡，但不是Android原生支持
        if(allMccMnc.size < 2) {
            var sim1MccMnc = SystemProperties.get(
                TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, ""
            )
            var sim2MccMnc = SystemProperties.get(
                TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC + ".2", ""
            )

            if(null == allMccMnc.getOrNull(0)) {
                if(TextUtils.isEmpty(sim1MccMnc)) {
                    sim1MccMnc = getIccSerialNumber(0)
                }
                allMccMnc.add(0, sim1MccMnc)
            } else if(!allMccMnc.contains(sim1MccMnc)) {
                allMccMnc.add(0, sim1MccMnc)
            }

            if(null == allMccMnc.getOrNull(1)) {
                if(TextUtils.isEmpty(sim1MccMnc)) {
                    sim2MccMnc = getIccSerialNumber(2)
                }
                allMccMnc.add(sim2MccMnc)
            } else if(!allMccMnc.contains(sim2MccMnc)) {
                allMccMnc.add(sim2MccMnc)
            }
        }
        if(allMccMnc.isNotEmpty()) {
            val simOperators = ArrayList<SimOperator>(allMccMnc.size)
            allMccMnc.forEach {
                simOperators.add(parseSimOperator(it))
            }
            return simOperators
        }
        return arrayListOf()
    }

    /**
     * 通过 MCCMNC or ICCID 解析运营商
     */
    fun parseSimOperator(origin: String): SimOperator {
        if(origin.length >= 20) {//iccid
            //中国移动编码格式
            //89860 0MFSS YYGXX XXXXP
            //89860 00224 64019 60025
            //中国联通编码格式
            //89860 1YYMH AAAXX XXXXP
            //89860 11188 30038 13090
            //中国电信编码格式
            //89860 3MYYH HHXXX XXXXX
            return when(origin.substring(0, 6)) {
                "898600" -> SimOperator.ChinaMOBILE
                "898601" -> SimOperator.ChinaUNICOM
                "898603" -> SimOperator.ChinaTELECOM
                else -> SimOperator.Unknown
            }
        } else {//mccmnc
            if(TextUtils.isEmpty(origin)) {
                return SimOperator.Unknown.apply { this.mccMnc = origin }
            }
            for(simOperator in SimOperator.values().filter { it != SimOperator.Unknown }) {
                val targetMccMnc = simOperator.operatorArray.invoke().find { it == origin }
                if(null != targetMccMnc) {
                    return simOperator.apply { this.mccMnc = origin }
                }
            }
            return SimOperator.Unknown.apply { this.mccMnc = origin }
        }
    }

    fun getMultiSimConfiguration(): MultiSimVariants {
        val proMultiSimConfig = SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG)
        return MultiSimVariants.values().find { it.name.equals(proMultiSimConfig, true) }
            ?: MultiSimVariants.UNKNOWN
//        return when(SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG)) {
//            "dsds" -> MultiSimVariants.DSDS
//            "dsda" -> MultiSimVariants.DSDA
//            "tsts" -> MultiSimVariants.TSTS
//            else -> MultiSimVariants.UNKNOWN
//        }
    }

    /**
     * Returns the number of phones available.
     * Returns 0 if none of voice, sms, data is not supported
     * Returns 1 for Single standby mode (Single SIM functionality)
     * Returns 2 for Dual standby mode.(Dual SIM functionality)
     */
    fun getPhoneCount(): Int {
        return when(getMultiSimConfiguration()) {
            MultiSimVariants.UNKNOWN -> 0
            MultiSimVariants.DSDS, MultiSimVariants.DSDA -> TelephonyProperties.MAX_PHONE_COUNT_DUAL_SIM
            MultiSimVariants.TSTS -> TelephonyProperties.MAX_PHONE_COUNT_TRI_SIM
        }
    }

    /**
     * SDK<21
     *     Returns the phone number string for line 1, for example, the MSISDN
     *     for a GSM phone. Return null if it is unavailable.
     *
     * SDK>=21
     *     Returns the phone number string for line 1, for example, the MSISDN
     *     for a GSM phone for a particular subscription. Return null if it is unavailable.
     *
     * @param phoneId whose phone number for line 1 is returned
     */
    @Throws(Exception::class)
    fun getPhoneNumber(phoneId: Int = 0): String {
        val subscriptionId = getValidPhoneIdBySubid(phoneId)
        return when {
            Build.VERSION.SDK_INT >= 28 -> {
                val clazz = TelephonyManager::class.java
                val method = clazz.getDeclaredMethod("getLine1Number", Int::class.java)
                method.isAccessible = true
                val result = method.invoke(mTM, subscriptionId)
                result as? String ?: ""
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                mIPhoneSubInfo?.getLine1NumberForSubscriber(
                    subscriptionId, CallRecordController.get().application.packageName
                ) ?: ""
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val clazz = IPhoneSubInfo::class.java
                val method = clazz.getDeclaredMethod(
                    "getLine1NumberForSubscriber", Long::class.java
                )
                method.isAccessible = true
                val result = method.invoke(getSubscriberInfo(), subscriptionId)
                result as? String ?: ""
            }

            else -> {
                val clazz = IPhoneSubInfo::class.java
                val method = clazz.getDeclaredMethod("getLine1Number")
                method.isAccessible = true
                val result = method.invoke(getSubscriberInfo())
                result as? String ?: ""
            }
        }
    }

    /**
     * SDK<21
     *     Returns the serial number of the SIM, if applicable. Return null if it is unavailable.
     *
     * SDK>=21
     *     Returns the serial number for the given subscription, if applicable. Return null if it is unavailable.
     *
     * @param phoneId subId for which Sim Serial number is returned
     */
    @Throws(Exception::class)
    fun getIccSerialNumber(phoneId: Int = 0): String {
        val subscriptionId = getValidPhoneIdBySubid(phoneId)
        return when {
            Build.VERSION.SDK_INT >= 28 -> {
                val clazz = TelephonyManager::class.java
                val method = clazz.getDeclaredMethod("getSimSerialNumber", Int::class.java)
                method.isAccessible = true
                val result = method.invoke(mTM, subscriptionId)
                result as? String ?: ""
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                mIPhoneSubInfo?.getIccSerialNumberForSubscriber(
                    subscriptionId, CallRecordController.get().application.packageName
                ) ?: ""
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val clazz = IPhoneSubInfo::class.java
                val method = clazz.getDeclaredMethod(
                    "getIccSerialNumberForSubscriber", Long::class.java
                )
                method.isAccessible = true
                val result = method.invoke(getSubscriberInfo(), subscriptionId)
                result as? String ?: ""
            }

            else -> {
                var result: String?
                if(subscriptionId > 0) {
                    result = SystemProperties.get(
                        TelephonyProperties.PROPERTY_RIL_SIM_ICCID_KEYS[subscriptionId], ""
                    )
                } else {
                    val clazz = IPhoneSubInfo::class.java
                    val method = clazz.getDeclaredMethod("getIccSerialNumber")
                    method.isAccessible = true
                    val iccid = method.invoke(getSubscriberInfo())
                    result = iccid as? String
                    if(null == result || TextUtils.isEmpty(result.toString())) {
                        result = SystemProperties.get(
                            TelephonyProperties.PROPERTY_RIL_SIM_ICCID_KEYS[subscriptionId], ""
                        )
                    }
                }
                result ?: ""
            }
        }
    }

    fun getValidPhoneIdBySubid(subId: Int?): Int {
        val phoneId = subId ?: 0

        val max = getPhoneCount()
        return if(0 <= max) {
            if(phoneId > 1) {
                1
            } else {
                0
            }
        } else {
            if(phoneId > max) {
                max - 1
            } else {
                phoneId
            }
        }
    }

    private fun isValidPhoneId(phoneId: Int): Boolean {
        val max = getPhoneCount()
        if(phoneId >= max) {
            try {
                throw InvalidSubscriberIdException(phoneId, max)
            } catch(e: Exception) {
                Timber.e(e)
                return false
            }
        }
        return true
    }

    fun call(context: Context, callNumber: String?, iRecordService: IRecordService?) {
        if(null == callNumber || null == iRecordService) {
            Timber.w("call fail!")
            return
        }
        iRecordService.startListen()
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$callNumber")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
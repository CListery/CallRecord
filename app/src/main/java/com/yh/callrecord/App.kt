package com.yh.callrecord

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.CallLog
import android.text.TextUtils
import android.widget.Toast
import com.kotlin.timeCurSecond
import com.yh.appbasic.logger.logD
import com.yh.appbasic.logger.logOwner
import com.yh.appbasic.logger.logW
import com.yh.appbasic.logger.owner.AppLogger
import com.yh.appbasic.logger.owner.LibLogger
import com.yh.appbasic.share.AppBasicShare
import com.yh.callrecord.db.CallRecordDBMigration
import com.yh.krealmextensions.RealmConfigManager
import com.yh.recordlib.CallRecordController
import com.yh.recordlib.RecordConfigure
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.inject.IRecordAppInject
import java.io.File

/**
 * Created by CYH on 2019-05-30 10:50
 */

class App : Application(), IRecordAppInject {

    private var mRecordConfig: RecordConfigure? = null

    companion object {
        @JvmStatic
        private var mApplicationCtx: App? = null

        @JvmStatic
        fun get(): App {
            return mApplicationCtx!!
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

//        if (!isInAppProcess(base)) {
//            return
//        }

        logW("attachBaseContext: $base")

        mApplicationCtx = this
    }

    override fun onCreate() {
        super.onCreate()
        logW("onCreate: $mApplicationCtx")
    
        AppBasicShare.install(this)
    
        if (null == mApplicationCtx) {
            return
        }

        AppLogger.on()
        LibLogger.off()
        
        RealmConfigManager.isEnableUiThreadOption = true
    }
    
    fun initCallRecord() {
        TelephonyCenter.get().register(this)
        TelephonyCenter.get().logOwner.on()
        val dbFileDirName =
            PreferencesUtils.getCommonPref().getString("record_configure_dbFileDirName", "")
        if (!TextUtils.isEmpty(dbFileDirName)) {
            CallRecordController.get()
        } else {
            val recordConfigure: RecordConfigure = makeBaseRecordConfigure(
                filesDir.absolutePath.toString() + File.separator + "center"
            )
            TelephonyCenter.get().setupRecordConfigure(recordConfigure)
            CallRecordController.initialization(recordConfigure)
        }
        
        contentResolver.registerContentObserver(//
            CallLog.Calls.CONTENT_URI,
            true,
            object : ContentObserver(Handler { msg ->
                logD("handleMessage: $msg")
                true
            }) {
                override fun onChange(selfChange: Boolean) {
                    logD("onChange: $selfChange")
                }
                
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    logD("onChange: $selfChange -> $uri")
                }
                
                override fun deliverSelfNotifications(): Boolean {
                    logD("deliverSelfNotifications")
                    return super.deliverSelfNotifications()
                }
            })
    }
    
    private fun isInAppProcess(ctx: Context?): Boolean {
        if (null == ctx) return false
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return false
        val runningApps = am.runningAppProcesses
        val myPid = android.os.Process.myPid()
        runningApps.forEach { process ->
            if (null != process) {
                if (process.pid == myPid) {
                    return ctx.packageName == process.processName
                }
            }
        }
        return false
    }

    override fun onTerminate() {
        logW("onTerminate")
        super.onTerminate()
    }

    override fun setRecordConfigure(configure: RecordConfigure) {
        mRecordConfig = configure

        PreferencesUtils.getCommonPref().edit()
            .putString("record_configure_dbFileDirName", configure.dbFileDirName.invoke()).apply()
    }

    override fun getRecordConfigure(): RecordConfigure {
        if (null == mRecordConfig) {
            val dbFileDirName: String? =
                PreferencesUtils.getCommonPref().getString("record_configure_dbFileDirName", "")
            mRecordConfig = makeBaseRecordConfigure(dbFileDirName)
        }
        return mRecordConfig!!
    }

    private fun makeBaseRecordConfigure(dbFileDirName: String?): RecordConfigure {
        if (TextUtils.isEmpty(dbFileDirName)) {
            throw RuntimeException("make RecordConfig fail, dbFileDirName can not be NULL!")
        }
        return RecordConfigure(
            get(),
            true, { dbFileDirName!! },
            BuildConfig.RECORD_DB_VERSION, { CallRecordDBMigration() },
            com.yh.recordlib.BuildConfig.CALL_RECORD_RETRY_TIME,
            BuildConfig.MAX_RETRY_SYNC_RECORD_COUNT,
            manualSyncCustomizeLogs = {
                mapOf(
                    "time" to timeCurSecond.toString()
                )
            }
        )
    }

    override fun showTipMsg(msg: String) {
        Toast.makeText(mApplicationCtx, msg, Toast.LENGTH_SHORT).show()
    }

    override fun getNotificationIcon(): Int {
        return R.mipmap.ic_launcher
    }
}

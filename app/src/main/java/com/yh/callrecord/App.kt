package com.yh.callrecord

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.CallLog
import android.support.multidex.MultiDex
import com.yh.callrecord.db.CallRecordDBMigration
import com.yh.recordlib.CallRecordController
import timber.log.Timber

/**
 * Created by CYH on 2019-05-30 10:50
 */

class App : Application() {
    
    private var mCRM: CallRecordController? = null
    
    companion object {
        @JvmStatic
        private var mApplicationCtx: App? = null

        @JvmStatic
        fun get(): App {
            return mApplicationCtx!!
        }
    }
    
    override fun attachBaseContext(base: Context?) {
        MultiDex.install(base)
        super.attachBaseContext(base)
        
        if(!isInAppProcess(base)) {
            return
        }
        
        if(BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.w("attachBaseContext: $base")
        
        mApplicationCtx = this
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.w("onCreate: $mApplicationCtx")
        
        if(null == mApplicationCtx) {
            return
        }
        
        mCRM = CallRecordController.initialization(
            ctx = mApplicationCtx!!,
            needInitSync = false,
            dbFileName = { BuildConfig.CALL_RECORD_DB },
            dbVersion = BuildConfig.RECORD_DB_VERSION,
            migration = { CallRecordDBMigration() },
            maxRetryCount = BuildConfig.MAX_RETRY_SYNC_RECORD_COUNT
        )
        
        contentResolver.registerContentObserver(//
            CallLog.Calls.CONTENT_URI,
            true,
            object : ContentObserver(Handler(Handler.Callback { msg ->
                Timber.d("handleMessage: $msg")
                true
            })) {
                override fun onChange(selfChange: Boolean) {
                    Timber.d("onChange: $selfChange")
                }
                
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    Timber.d("onChange: $selfChange -> $uri")
                }
                
                override fun deliverSelfNotifications(): Boolean {
                    Timber.d("deliverSelfNotifications")
                    return super.deliverSelfNotifications()
                }
            })
    }
    
    private fun isInAppProcess(ctx: Context?): Boolean {
        if(null == ctx) return false
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                 ?: return false
        val runningApps = am.runningAppProcesses
        val myPid = android.os.Process.myPid()
        runningApps.forEach { process ->
            if(null != process) {
                if(process.pid == myPid) {
                    return ctx.packageName == process.processName
                }
            }
        }
        return false
    }
    
    override fun onTerminate() {
        Timber.w("onTerminate")
        super.onTerminate()
    }
}

package com.yh.recordlib

import android.app.Application
import com.yh.recordlib.entity.RecordRealmModule
import io.realm.RealmMigration

/**
 * Created by CYH on 2020/3/20 16:52
 */
class RecordConfigure(
    val ctx: Application,
    val needInitSync: Boolean = false,
    val dbFileDirName: () -> String = { ctx.filesDir.absolutePath },
    val dbVersion: Long = 1L,
    val migration: (() -> RealmMigration)? = null,
    val syncRetryTime: Long = BuildConfig.CALL_RECORD_RETRY_TIME,
    val maxRetryCount: Int = 2,
    val modules: (() -> Array<*>)? = { arrayOf(RecordRealmModule()) },
    val maxCallTimeOffset: Long = BuildConfig.MAX_CALL_TIME_OFFSET,
    val minCallTimeOffset: Long = BuildConfig.MIN_CALL_TIME_OFFSET,
    val startTimeOffset:Long = BuildConfig.START_CALL_TIME_OFFSET
)
package com.yh.recordlib

import android.app.Application
import io.realm.RealmMigration
import com.yh.recordlib.db.DefCallRecordDBMigration

/**
 * Created by CYH on 2020/3/20 16:52
 *
 * @param [ctx] Application
 * @param [needInitSync] whether to perform full synchronization during initialization. Default: false
 * @param [dbFileDirName] the directory to save the Realm file in. Directory must be writable.
 * @param [dbVersion] Sets the schema version of the Realm. [DefCallRecordDBMigration.getFinalVersion]
 * @param [migration] Sets the RealmMigration to be run if a migration is needed.
 * @param [syncRetryTime] retry interval when the synchronous system's CallRecord data fails. Default: [com.yh.recordlib.BuildConfig.CALL_RECORD_RETRY_TIME]
 * @param [maxRetryCount] max retry count. Default: 2
 * @param [modules] the additional Realm modules
 * @param [maxCallTimeOffset] max time offset for sync-record's
 * @param [minCallTimeOffset] min time offset for sync-record's
 * @param [startTimeOffset] query the start time offset of the system call log database
 * @param [syncTimeOffset] Synchronize un-synchronized call records of unit time before the current time
 */
class RecordConfigure(
    val ctx: Application,
    val needInitSync: Boolean = false,
    val dbFileDirName: () -> String = { ctx.filesDir.absolutePath },
    val dbVersion: Long = 1L,
    val migration: (() -> RealmMigration)? = null,
    val syncRetryTime: Long = BuildConfig.CALL_RECORD_RETRY_TIME,
    val maxRetryCount: Int = 2,
    val modules: (() -> Array<*>)? = { emptyArray<Any>() },
    val maxCallTimeOffset: Long = BuildConfig.MAX_CALL_TIME_OFFSET,
    val minCallTimeOffset: Long = BuildConfig.MIN_CALL_TIME_OFFSET,
    val startTimeOffset:Long = BuildConfig.START_CALL_TIME_OFFSET,
    val syncTimeOffset: Long = Long.MAX_VALUE
)
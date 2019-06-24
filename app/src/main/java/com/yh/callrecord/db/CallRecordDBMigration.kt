package com.yh.callrecord.db

import io.realm.DynamicRealm
import io.realm.RealmMigration
import timber.log.Timber

/**
 * Created by CYH on 2019-05-30 10:54
 */
class CallRecordDBMigration : RealmMigration {
    
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        Timber.w("migrate: $oldVersion -> $newVersion")
    }
}
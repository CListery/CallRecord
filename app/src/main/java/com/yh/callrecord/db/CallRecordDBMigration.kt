package com.yh.callrecord.db

import com.yh.appinject.logger.logW
import io.realm.DynamicRealm
import io.realm.RealmMigration

/**
 * Created by CYH on 2019-05-30 10:54
 */
class CallRecordDBMigration : RealmMigration {
    
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        logW("migrate: $oldVersion -> $newVersion")
    }
}
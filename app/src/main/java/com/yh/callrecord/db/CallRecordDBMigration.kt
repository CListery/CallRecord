package com.yh.callrecord.db

import com.yh.appinject.logger.ext.libW
import com.yh.recordlib.TelephonyCenter
import io.realm.DynamicRealm
import io.realm.RealmMigration

/**
 * Created by CYH on 2019-05-30 10:54
 */
class CallRecordDBMigration : RealmMigration {

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        TelephonyCenter.get().libW("migrate: $oldVersion -> $newVersion")
    }
}
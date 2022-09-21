package com.yh.recordlib.db

import com.yh.appbasic.logger.logW
import com.yh.recordlib.BuildConfig
import com.yh.recordlib.TelephonyCenter
import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmMigration

/**
 * Created by CYH on 2019-05-30 10:54
 */
class DefCallRecordDBMigration(private val realmMigration: RealmMigration?) : RealmMigration {
    
    companion object {
        /**
         * F    F    F    F
         * 1111 1111 1111 1111
         *
         * mainVersion => max:255
         *
         * subVersion => max:255
         */
        @JvmStatic
        fun getFinalVersion(dbVersion:Long): Long {
            val mainVersion = BuildConfig.RECORD_DB_VERSION.shl(8).and(0xFF00)
            val subVersion = dbVersion.and(0xFF)
            return mainVersion.or(subVersion)
        }
        
        @JvmStatic
        fun getVersions(finalVersion:Long):Pair<Long, Long>{
            val mainVersion = finalVersion.ushr(8).and(0xFF)
            val subVersion = finalVersion.and(0xFF)
            return mainVersion to subVersion
        }
    }
    
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        val oldVersions = getVersions(oldVersion)
        val newVersions = getVersions(newVersion)
        logW("main migrate: ${oldVersions.first} -> ${newVersions.first}", loggable = TelephonyCenter.get())
        
        val newMainVersion = newVersions.first
        var oldMainVersion = oldVersions.first
        
        val schema = realm.schema
        @Suppress("UNUSED_CHANGED_VALUE")
        if(0L == oldMainVersion) {
            schema.get("CallRecord")?.addField("isManualSynced", Boolean::class.java)?.transform {
                it.set("isManualSynced", false)
            }
            oldMainVersion++
        }
        if(1L == oldMainVersion){
            schema.get("CallRecord")?.addField("hasChinaTELECOM", Boolean::class.java)?.transform {
                it.set("hasChinaTELECOM", false)
            }
            oldMainVersion++
        }
        if(2L == oldMainVersion){
            schema.get("CallRecord")?.addField("originCallNumber", String::class.java, FieldAttribute.REQUIRED)?.transform {
                val phoneNumber: String? =
                    try {
                        it.getString("phoneNumber")
                    } catch(e: Exception) {
                        null
                    }
                it.setString(
                    "originCallNumber",
                    phoneNumber
                        ?: ""
                )
            }
            schema.get("SystemCallRecord")?.removeField("lastModify")
            oldMainVersion++
        }
        if(3L == oldMainVersion){
            schema.remove("FakeCallRecord")
            schema.get("CallRecord")?.removeField("originCallNumber")
            schema.get("CallRecord")?.removeField("isFake")
            oldMainVersion++
        }
        if(4L == oldMainVersion){
            schema.get("CallRecord")?.addField("syncCount", Int::class.java, FieldAttribute.REQUIRED)?.transform {
                it.set("syncCount", 0)
            }
            oldMainVersion++
        }
        if(5L == oldMainVersion){
            schema.get("CallRecord")?.addField("syncedTime", Long::class.java, FieldAttribute.REQUIRED)?.transform {
                it.set("syncedTime", 0)
            }
            oldMainVersion++
        }
        if(oldVersions.second < newVersions.second) {
            realmMigration?.migrate(realm, oldVersions.second, newVersions.second)
        }
    }
    
    override fun hashCode(): Int {
        return 66
    }
    
    override fun equals(other: Any?): Boolean {
        return other is DefCallRecordDBMigration
    }
}
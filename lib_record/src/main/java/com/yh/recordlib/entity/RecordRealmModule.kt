package com.yh.recordlib.entity

import io.realm.annotations.RealmModule

@RealmModule(
    library = true, classes = [CallRecord::class, SystemCallRecord::class]
)
class RecordRealmModule
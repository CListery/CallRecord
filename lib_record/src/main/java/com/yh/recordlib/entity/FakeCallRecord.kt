package com.yh.recordlib.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import io.realm.annotations.Required

/**
 * Created by CYH on 2019-06-14 17:01
 */
@RealmClass
open class FakeCallRecord : RealmObject() {
    
    @PrimaryKey
    @Required
    open var recordId: String = ""
    
    @Required
    open var fakeNumber: String = ""
}
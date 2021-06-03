package com.yh.callrecord

import com.google.common.truth.Truth
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.SystemCallRecord

val dataRecords = arrayListOf(
    RecordData(
        listOf(
            CallRecord(recordId = "8a14202030004a3114620c3e6f6f", phoneNumber = "18208800796", callStartTime = 1622546171448, callOffHookTime = 1622546172722, callEndTime = 1622546985873, callType = 2, isFake = false, synced = false, callLogId = 0, duration = 0, recalculated = false, callState = 0, phoneAccountId = 0, isNoMapping = true, isDeleted = false)
        ),
        listOf(
            SystemCallRecord(callId = 35118, date = 1622546186676, lastModify = 1622546346239, duration = 66, type = 2, phoneAccountId = 0, phoneNumber = "18208800796"),
            SystemCallRecord(callId = 35119, date = 1622546330166, lastModify = 1622546346239, duration = 0, type = 2, phoneAccountId = 0, phoneNumber = "18208800796")
        )
    ) { recordMappingInfo ->
        Truth.assertThat(recordMappingInfo.noMappingRecords.isNullOrEmpty()).isTrue()
        Truth.assertThat(recordMappingInfo.mappingRecords.size).isEqualTo(1)
        Truth.assertThat(recordMappingInfo.mappingRecords.keys.first().duration).isEqualTo(66)
    },
    RecordData(
        listOf(
            CallRecord(recordId="209000120018208280c49c129494", phoneNumber="13700652636", callStartTime=1622518657521, callOffHookTime=1622518657521, callEndTime=1622518685750, callType=2, isFake=false, synced=true, callLogId=6270, duration=28, recalculated=false, callState=2, phoneAccountId=1, mccMnc="46002", isNoMapping=false, isDeleted=false)
        ),
        listOf(
            SystemCallRecord(callId = 6270, date = 1622518657521, lastModify = 1622518982842, duration = 28, type = 2, phoneAccountId = 1, phoneNumber = "13700652636")
        )
    ) { recordMappingInfo ->
        Truth.assertThat(recordMappingInfo.noMappingRecords.isNullOrEmpty()).isTrue()
        Truth.assertThat(recordMappingInfo.mappingRecords.size).isEqualTo(1)
        Truth.assertThat(recordMappingInfo.mappingRecords.keys.first().duration).isEqualTo(28)
    },
    RecordData(
        listOf(
            CallRecord(recordId="0882896154222c328b5008609292", phoneNumber="15911669556", callStartTime=1622528021264, callOffHookTime=1622528021264, callEndTime=1622528078030, callType=2, isFake=false, synced=true, callLogId=5712, duration=56, recalculated=true, callState=2, phoneAccountId=1, mccMnc="46003", isNoMapping=false, isDeleted=false)
        ),
        listOf(
            SystemCallRecord(callId=5712, date=1622528021264, lastModify=1622528867957, duration=56, type=2, phoneAccountId=1, phoneNumber="15911669556")
        )
    ) { recordMappingInfo ->
        Truth.assertThat(recordMappingInfo.noMappingRecords.isNullOrEmpty()).isTrue()
        Truth.assertThat(recordMappingInfo.mappingRecords.size).isEqualTo(1)
        Truth.assertThat(recordMappingInfo.mappingRecords.keys.first().duration).isEqualTo(56)
    },
    RecordData(
        listOf(
            CallRecord(recordId = "8a14202030004a3114620c3e6f6f", phoneNumber = "18208800796", callStartTime = 1622546171448, callOffHookTime = 1622546172722, callEndTime = 1622546985873, callType = 2, isFake = false, synced = false, callLogId = 0, duration = 0, recalculated = false, callState = 0, phoneAccountId = 0, isNoMapping = true, isDeleted = false),
            CallRecord(recordId="209000120018208280c49c129494", phoneNumber="13700652636", callStartTime=1622518657521, callOffHookTime=1622518657521, callEndTime=1622518685750, callType=2, isFake=false, synced=true, callLogId=6270, duration=28, recalculated=false, callState=2, phoneAccountId=1, mccMnc="46002", isNoMapping=false, isDeleted=false),
            CallRecord(recordId="0882896154222c328b5008609292", phoneNumber="15911669556", callStartTime=1622528021264, callOffHookTime=1622528021264, callEndTime=1622528078030, callType=2, isFake=false, synced=true, callLogId=5712, duration=56, recalculated=true, callState=2, phoneAccountId=1, mccMnc="46003", isNoMapping=false, isDeleted=false)
        ),
        listOf(
            SystemCallRecord(callId=5712, date=1622528021264, lastModify=1622528867957, duration=56, type=2, phoneAccountId=1, phoneNumber="15911669556")
        )
    ) { recordMappingInfo ->
        Truth.assertThat(recordMappingInfo.noMappingRecords.size).isEqualTo(2)
        Truth.assertThat(recordMappingInfo.mappingRecords.size).isEqualTo(1)
        Truth.assertThat(recordMappingInfo.mappingRecords.keys.first().duration).isEqualTo(56)
    },
    RecordData(
        listOf(
            CallRecord(recordId="1105010a012043a00840900dadad", phoneNumber="13888629422", callStartTime=1622514152739, callOffHookTime=0, callEndTime=0, callType=2, isFake=false, synced=false, callLogId=0, duration=0, recalculated=false, callState=0, phoneAccountId=0, isNoMapping=true, isDeleted=false),
            CallRecord(recordId="03333361544423423b5003242342", phoneNumber="13888629422", callStartTime=1622514242297, callOffHookTime=0, callEndTime=0, callType=2, isFake=false, synced=false, callLogId=0, duration=0, recalculated=false, callState=0, phoneAccountId=0, isNoMapping=true, isDeleted=false),
            CallRecord(recordId="0882896154222c328b5008609292", phoneNumber="13888629422", callStartTime=1622514275297, callOffHookTime=0, callEndTime=0, callType=2, isFake=false, synced=false, callLogId=0, duration=0, recalculated=false, callState=0, phoneAccountId=0, isNoMapping=true, isDeleted=false)
        ),
        listOf(
            SystemCallRecord(callId=4198, date=1622514517915, lastModify=1622514520231, duration=0, type=2, phoneAccountId=0, phoneNumber="13888629422"),
            SystemCallRecord(callId=4197, date=1622514242651, lastModify=1622514276297, duration=20, type=2, phoneAccountId=0, phoneNumber="13888629422")
        )
    ) { recordMappingInfo ->
        Truth.assertThat(recordMappingInfo.noMappingRecords.size).isEqualTo(2)
        Truth.assertThat(recordMappingInfo.mappingRecords.size).isEqualTo(1)
        Truth.assertThat(recordMappingInfo.mappingRecords.keys.first().duration).isEqualTo(20)
        Truth.assertThat(recordMappingInfo.mappingRecords.values.first().recordId).isEqualTo("03333361544423423b5003242342")
    }
)

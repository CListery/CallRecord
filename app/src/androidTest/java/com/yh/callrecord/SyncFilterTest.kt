package com.yh.callrecord

import android.app.Application
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.yh.recordlib.RecordConfigure
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.utils.RecordFilter.findMappingRecords
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncFilterTest {
    
    @Before
    fun setup() {
        val application = InstrumentationRegistry.getTargetContext().applicationContext as Application
        val recordConfigure = RecordConfigure(
            application,
            startTimeOffset = 20000
        )
        TelephonyCenter.get().setupRecordConfigure(recordConfigure)
    }
    
    @Test
    fun testFilter() {
        dataRecords.forEach { rd ->
            rd.apply {
                checker.invoke(findMappingRecords(systemRecords, callRecords))
            }
        }
    }
    
}
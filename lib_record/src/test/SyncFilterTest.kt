import com.yh.recordlib.BuildConfig
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import com.yh.recordlib.entity.SystemCallRecord
import kotlin.math.max

object SyncFilterTest {
    
    
    // CallRecord(recordId='0251418810092e14003e12e6fafa', phoneNumber='13769132764', callStartTime=1620955119253, callOffHookTime=1620955120066, callEndTime=0, callType=2, audioFilePath=, isFake=false, synced=false, callLogId=0, duration=0, recalculated=false, callState=0, phoneAccountId=0, mccMnc=, isNoMapping=true, isDeleted=false)
    
    @JvmStatic
    fun main(vararg args: String) {
        val cr = CallRecord()
        cr.recordId = "0251418810092e14003e12e6fafa"
        cr.phoneNumber = "13769132764"
        cr.callStartTime = 1620955119253
        cr.callOffHookTime = 1620955120066
        cr.callEndTime = 0
        cr.callType = 2
        cr.isFake = false
        cr.synced = false
        cr.callLogId = 0
        cr.duration = 0
        cr.recalculated = false
        cr.callState = 0
        cr.phoneAccountId = 0
        cr.isNoMapping = true
        cr.isDeleted = false
        
        // SystemCallRecord(callId=10417, date=1620955147504, lastModify=1620955520094, duration=42, type=2, phoneAccountId=0, phoneNumber='13769132764')
        // SystemCallRecord(callId=10418, date=1620955197111, lastModify=1620955520094, duration=0, type=2, phoneAccountId=0, phoneNumber='13769132764')
        // SystemCallRecord(callId=10419, date=1620955547270, lastModify=1620955585142, duration=37, type=2, phoneAccountId=0, phoneNumber='13769132764')
        val sr = SystemCallRecord()
        sr.callId = 10417
        sr.date = 1620955147504
        sr.lastModify = 1620955520094
        sr.duration = 42
        sr.type = 2
        sr.phoneAccountId = 0
        sr.phoneNumber = "13769132764"
    
        val target = precisionFilter(sr, arrayListOf(cr))
        print(target)
    }
    
    private fun precisionFilter(sr: SystemCallRecord, crs: List<CallRecord>, precisionCount: Int = 0): List<CallRecord> {
        if(precisionCount > BuildConfig.MAX_CALL_TIME_OFFSET / BuildConfig.MIN_CALL_TIME_OFFSET) {
            return crs
        }
        val tmp: List<CallRecord> = crs.filter { cr ->
            precisionFilter(sr, cr, precisionCount)
        }
        if(tmp.size > 1) {
            return precisionFilter(sr, tmp, precisionCount.inc())
        }
        return tmp
    }
    
    /**
     * 精确过滤
     */
    private fun precisionFilter(sr: SystemCallRecord, cr: CallRecord, precisionCount: Int): Boolean {
        //重新计算时间偏移量
        val timeOffset = BuildConfig.MAX_CALL_TIME_OFFSET - (BuildConfig.MIN_CALL_TIME_OFFSET * precisionCount)
        val crStartTime = max(cr.callStartTime, cr.callOffHookTime)
        if(crStartTime > 0 && cr.callEndTime <= 0) { // 未能监听到结束时间的意外情况
            if(sr.duration > 0) {
                return sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
            }
        }
        val endTimeFilter = if(sr.duration > 0) { //（结束时长过滤器）内部数据库记录的通话结束时间 处于
            // 1.系统数据库通话记录 [开始时间+通话持续时长 +- 一分钟误差范围内]
            cr.callEndTime in (sr.date + sr.getDurationMillis() - timeOffset)..(sr.date + sr.getDurationMillis() + timeOffset)
                    // 2.系统数据库通话记录 [最后修改时间 +- 一分钟误差范围内]（部分机型有该值）
                    || (sr.lastModify > 0 && cr.callEndTime in (sr.lastModify - timeOffset)..(sr.lastModify + timeOffset))
        } else {
            //系统记录未接通,只判断开始时间
            return sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
        }
        val startTimeFilter = // 开始时长过滤器
            if(CallType.CallIn.ordinal == cr.callType) { // 呼入
                if(cr.callOffHookTime > 0) {
                    //当属于呼入类型且接通时开始时间以接通时间为准
                    var filter = sr.date in (cr.callOffHookTime - timeOffset)..(cr.callOffHookTime + timeOffset)
                    if(!filter && endTimeFilter) {
                        //如果某些机型系统数据库中开始时间不是以接通时间,则使用开始时间为准
                        filter = sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
                    }
                    filter
                } else {
                    false
                }
            } else { // 呼出
                // 系统数据库通话记录 [开始时间] == 内部数据库通话记录 [开始时间 +- 一分钟误差范围内]
                sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
            }
        return startTimeFilter && endTimeFilter
    }
    
    /**
     * 粗略过滤
     * 1. 未能获取到号码的情况使用时间范围过滤
     * 2. 号码相同的情况直接过滤
     */
    private fun coarseFilter(
        sr: SystemCallRecord, cr: CallRecord, timeOffset: Long = BuildConfig.MAX_CALL_TIME_OFFSET
    ): Boolean {
        return if(sr.phoneNumber.isEmpty() || cr.isFake) { // 号码未能正常获取，使用时间范围进行模糊过滤
            val crStartTime = max(cr.callStartTime, cr.callOffHookTime) // 获取通话开始时间
            val endTimeFilter = //（结束时长过滤器）内部数据库记录的通话结束时间 处于
                // 1.系统数据库通话记录 [开始时间+通话持续时长 +- 一分钟误差范围内]
                cr.callEndTime in (sr.date + sr.getDurationMillis() - timeOffset)..(sr.date + sr.getDurationMillis() + timeOffset)
                        // 2.系统数据库通话记录 [最后修改时间 +- 一分钟误差范围内]（部分机型有该值）
                        || (sr.lastModify > 0 && cr.callEndTime in (sr.lastModify - timeOffset)..(sr.lastModify + timeOffset))
            val startTimeFilter = // 开始时长过滤器
                if(CallType.CallIn.ordinal == cr.callType) { // 呼入
                    if(cr.callOffHookTime > 0) {
                        var filter = sr.date in (cr.callOffHookTime - timeOffset)..(cr.callOffHookTime + timeOffset)
                        if(!filter && endTimeFilter) {
                            filter = sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
                        }
                        filter
                    } else {
                        false
                    }
                } else { // 呼出
                    // 系统数据库通话记录 [开始时间] == 内部数据库通话记录 [开始时间 +- 一分钟误差范围内]
                    sr.date in (crStartTime - timeOffset)..(crStartTime + timeOffset)
                }
            startTimeFilter && endTimeFilter
        } else {
            //号码过滤
            cr.phoneNumber == sr.phoneNumber
        }
    }
    
    
}
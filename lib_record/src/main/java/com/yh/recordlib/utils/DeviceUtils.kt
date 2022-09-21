package com.yh.recordlib.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import com.yh.appbasic.logger.logE
import com.yh.appbasic.share.AppBasicShare
import com.yh.recordlib.TelephonyCenter
import com.yh.recordlib.ext.convertBytesToMega
import com.yh.recordlib.ext.humanReadableByteCount
import com.yh.recordlib.ext.round2
import com.yh.recordlib.ext.runOnApiAbove
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.util.regex.Pattern

object DeviceUtils {
    
    enum class MemoryType { INTERNAL,
        EXTERNAL
    }
    
    /**
     * Retrieve data from static Build class and system property "java.vm.version"
     */
    private fun getBuildDataInfo(): ArrayList<String> {
        val buildDataList = ArrayList<String>()
        buildDataList.add(
            "VER: Android${Build.VERSION.RELEASE} API-${Build.VERSION.SDK_INT} Kernel-${System.getProperty(
                "os.version"
            )}"
        )
        buildDataList.add("CODENAME: ${Build.VERSION.CODENAME}")
        buildDataList.add("BRAND: ${Build.MANUFACTURER}-${Build.BRAND}-${Build.MODEL}")
        buildDataList.add("VM: ${getVmVersion()}")
        buildDataList.add("ROOT: ${isDeviceRooted()}")
        return buildDataList
    }
    
    /**
     * Specify if device is using ART or Dalvik
     */
    private fun getVmVersion(): String {
        var vm = "Dalvik"
        val vmVersion = System.getProperty("java.vm.version")
        if(vmVersion != null && vmVersion.startsWith("2")) {
            vm = "ART"
        }
        return vm
    }
    
    /**
     * Get Storage info: internal and external
     */
    @JvmStatic
    fun getExternalAndInternalStorageInfo(): ArrayList<String> {
        val storageInfoList = ArrayList<String>()
        val internalTotal = getTotalMemorySize(MemoryType.INTERNAL)
        val internalUsed = internalTotal - getAvailableMemorySize(MemoryType.INTERNAL)
        val internalUsedPercent = (internalUsed.toFloat()
                / internalTotal.toFloat() * 100.0).round2()
        storageInfoList.add(
            "Internal SD: ${humanReadableByteCount(internalUsed)} / ${humanReadableByteCount(
                internalTotal
            )} ($internalUsedPercent%)"
        )
        
        if(isExternalMemoryAvailable()) {
            val externalTotal = getTotalMemorySize(MemoryType.EXTERNAL)
            val externalUsed = externalTotal - getAvailableMemorySize(MemoryType.EXTERNAL)
            val externalUsedPercent = (externalUsed.toFloat()
                    / externalTotal.toFloat() * 100.0).round2()
            storageInfoList.add(
                "External SD: ${humanReadableByteCount(externalUsed)} / ${humanReadableByteCount(
                    externalTotal
                )} ($externalUsedPercent%)"
            )
        }
        return storageInfoList
    }
    
    /**
     * @return true if device support external storage, otherwise false
     */
    private fun isExternalMemoryAvailable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
    
    /**
     * Get total bytes from internal or external storage.
     *
     * @param memoryType type of memory
     * @return full size of the storage
     */
    private fun getTotalMemorySize(memoryType: MemoryType): Long {
        val path: File = when(memoryType) {
            MemoryType.INTERNAL -> Environment.getDataDirectory()
            MemoryType.EXTERNAL -> Environment.getExternalStorageDirectory()
        }
        
        return path.totalSpace
    }
    
    /**
     * Get available bytes from internal or external storage.
     *
     * @param memoryType type of memory
     * @return available size of the storage
     */
    private fun getAvailableMemorySize(memoryType: MemoryType): Long {
        val path: File = when(memoryType) {
            MemoryType.INTERNAL -> Environment.getDataDirectory()
            MemoryType.EXTERNAL -> Environment.getExternalStorageDirectory()
        }
        
        return path.usableSpace
    }
    
    /**
     * Get RAM info: all, available and threshold
     */
    @JvmStatic
    fun getMemoryInfo(): ArrayList<String> {
        val memoryInfoList = ArrayList<String>()
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = AppBasicShare.context
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        runOnApiAbove(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1, {
            val availableMemoryPercent = (memoryInfo.availMem.toDouble()
                    / memoryInfo.totalMem.toDouble() * 100.0).toInt()
            
            memoryInfoList.add("MEMORY: ${convertBytesToMega(memoryInfo.availMem)} / ${convertBytesToMega(memoryInfo.totalMem)} ($availableMemoryPercent%)")
        }, {
            val totalRam = getTotalRamForOldApi()
            val availableMemoryPercent = (memoryInfo.availMem.toDouble()
                    / totalRam.toDouble() * 100.0).toInt()
    
            memoryInfoList.add("MEMORY: ${convertBytesToMega(memoryInfo.availMem)} / ${convertBytesToMega(totalRam)} ($availableMemoryPercent%)")
        })
        
        memoryInfoList.add("THRESHOLD: ${humanReadableByteCount(memoryInfo.threshold)}")
        return memoryInfoList
    }
    
    /**
     * Legacy method for old Android
     */
    private fun getTotalRamForOldApi(): Long {
        var reader: RandomAccessFile? = null
        var totRam: Long = -1
        try {
            reader = RandomAccessFile("/proc/meminfo", "r")
            val load = reader.readLine()
            
            // Get the Number value from the string
            val p = Pattern.compile("(\\d+)")
            val m = p.matcher(load)
            var value = ""
            while(m.find()) {
                value = m.group(1)
                    ?: ""
            }
            reader.close()
            
            totRam = value.toLong()
        } catch(e: Exception) {
            logE("getTotalRamForOldApi", throwable = e, loggable = TelephonyCenter.get())
        } finally {
            reader?.close()
        }
        
        return totRam * 1024 // bytes
    }
    
    /**
     * Check if device is rooted. Source:
     * https://stackoverflow.com/questions/1101380/determine-if-running-on-a-rooted-device
     */
    @JvmStatic
    fun isDeviceRooted(): Boolean =
        checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
    
    private fun checkRootMethod1(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }
    
    private fun checkRootMethod2(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }
    
    private fun checkRootMethod3(): Boolean {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val br = BufferedReader(InputStreamReader(process.inputStream))
            if(br.readLine() != null) return true
            return false
        } catch(t: Throwable) {
            return false
        } finally {
            process?.destroy()
        }
    }
    
}
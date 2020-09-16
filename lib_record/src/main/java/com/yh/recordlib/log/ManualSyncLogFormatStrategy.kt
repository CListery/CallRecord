package com.yh.recordlib.log

import android.os.HandlerThread
import androidx.annotation.NonNull
import com.yh.appinject.logger.FormatStrategy
import com.yh.appinject.logger.LogStrategy
import com.yh.appinject.logger.impl.DiskLogStrategy
import com.yh.appinject.logger.impl.DiskLogStrategy.*
import com.yh.recordlib.utils.logLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ManualSyncLogFormatStrategy(builder: Builder) : FormatStrategy {
    
    companion object {
        private const val TAG = "ManualSyncLog"
        
        private const val HORIZONTAL_LINE = "|"
        private const val NEW_LINE = "\n"
        private const val SEPARATOR = ","
    }
    
    private val date: Date = Date()
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS", Locale.UK)
    private val logStrategy: LogStrategy = builder.logStrategy
    private var logFile: File = builder.logFile!!
    
    fun getRealLogFile(): File? = logFile
    
    override fun log(priority: Int, onceOnlyTag: String?, message: String) {
        date.time = System.currentTimeMillis()
        
        val builder = StringBuilder()
        
        // machine-readable date/time
        builder.append(date.time.toString())
        
        // human-readable date/time
        builder.append(SEPARATOR)
        builder.append(dateFormat.format(date))
        
        // level
        builder.append(SEPARATOR)
        builder.append(logLevel(priority))
        
        // tag
        builder.append(SEPARATOR)
        builder.append(TAG)
        
        builder.append(SEPARATOR)
        // message
        logContent(builder, builder.toString(), message.replace(SEPARATOR, ";"))
        
        logStrategy.log(priority, TAG, builder.toString())
    }
    
    private fun logContent(builder: StringBuilder, header: String, @NonNull message: String) {
        val msgArr = message.split("\n")
        if(msgArr.size > 1) {
            msgArr.forEachIndexed { index, msg ->
                if(index > 0){
                    builder.append(header)
                }
                builder.append("$HORIZONTAL_LINE $msg").append(NEW_LINE)
            }
        } else {
            builder.append(message).append(NEW_LINE)
        }
    }
    
    override fun release() {
        logStrategy.release()
    }
    
    class Builder {
        lateinit var logStrategy: LogStrategy
        var logDir: String = ""
        var logFileName: String = ""
        var logFile: File? = null
        
        fun logDir(dir: String): Builder {
            logDir = dir
            return this
        }
        
        fun logFileName(fileName: String): Builder {
            logFileName = fileName
            return this
        }
        
        @NonNull
        fun build(): ManualSyncLogFormatStrategy {
            if(logDir.isEmpty() || logFileName.isEmpty()) {
                throw NullPointerException("logDir and logFileName can not be Null!")
            }
            val realLogFile = getLogFile(logDir, logFileName)
            logFile = realLogFile
            val ht = HandlerThread("ManualSyncLogger.$logFileName")
            ht.start()
            logStrategy = DiskLogStrategy(WriteHandler(ht.looper, realLogFile))
            return ManualSyncLogFormatStrategy(this)
        }
        
        private fun getLogFile(folderName: String, fileName: String): File {
            val folder = File(folderName)
            if(!folder.exists()) {
                folder.mkdirs()
            }
            
            return File(
                folder, String.format(
                    "%s_%s.%s",
                    fileName,
                    SimpleDateFormat(
                        "yyyyMMdd_HHmm",
                        Locale.ENGLISH
                    ).format(System.currentTimeMillis()),
                    "log"
                )
            )
        }
    }
}
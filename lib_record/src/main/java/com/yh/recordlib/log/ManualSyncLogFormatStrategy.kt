package com.yh.recordlib.log

import android.os.HandlerThread
import androidx.annotation.NonNull
import com.yh.appbasic.logger.FormatStrategy
import com.yh.appbasic.logger.LogStrategy
import com.yh.appbasic.logger.impl.DiskLogStrategy
import com.yh.appbasic.logger.impl.DiskLogStrategy.*
import com.yh.recordlib.utils.logLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ManualSyncLogFormatStrategy(builder: Builder) : FormatStrategy {
    
    companion object {
        private const val LOG_NAME = "MSL"
        
        private const val HORIZONTAL_LINE = "|"
        private const val NEW_LINE = "\n"
        private const val HEAD_SEPARATOR = "-> "
        private const val CONTENT_SEPARATOR = " "
    }
    
    private val date: Date = Date()
    private val dateFormat: SimpleDateFormat =
        SimpleDateFormat("yyyy.MM.dd_HH:mm:ss.SSS", Locale.UK)
    private val logStrategy: LogStrategy = builder.logStrategy
    private var logFile: File = builder.logFile!!
    
    fun getRealLogFile(): File = logFile
    
    override fun log(priority: Int, onceOnlyTag: String?, message: String) {
        date.time = System.currentTimeMillis()
        
        val builder = StringBuilder()
        
        // message
        logContent(buildHeader(priority), builder, message)
        
        logStrategy.log(priority, "", builder.toString())
    }
    
    private fun logContent(
        @NonNull header: String,
        @NonNull builder: StringBuilder,
        @NonNull message: String
    ) {
        val msgArr = message.split("\n")
        if(msgArr.size > 1) {
            msgArr.forEachIndexed { index, msg ->
                builder.append(header).append("$HORIZONTAL_LINE $msg").append(NEW_LINE)
            }
        } else {
            builder.append(header).append(message).append(NEW_LINE)
        }
    }
    
    private fun buildHeader(priority: Int): String {
        val builder = StringBuilder()
        
        // date/time
        builder.append(dateFormat.format(date))
        builder.append(CONTENT_SEPARATOR)
        
        // level
        builder.append(logLevel(priority))
        builder.append("/")
        // tag
        builder.append(LOG_NAME)
        builder.append(HEAD_SEPARATOR)
        return builder.toString()
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
package com.yh.recordlib.media

import android.app.Application
import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import com.vicpin.krealmextensions.save
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by CYH on 2019-05-30 16:26
 */

class MediaRecordHelper(private val mCtx: Application) {
    
    companion object {
        @JvmStatic
        private var mInstance: MediaRecordHelper? = null

        @JvmStatic
        @Synchronized
        fun get(ctx: Application): MediaRecordHelper {
            if(null == mInstance) {
                mInstance = MediaRecordHelper(
                    ctx
                )
            }
            return mInstance!!
        }
    }
    
    private var mAudioRecord: MediaRecorder? = null
    private val isRecordStarted = AtomicBoolean(false)
    
    private var mEnable = false
    fun setEnable(enable: Boolean) {
        mEnable = enable
    }
    
    fun startRecord(callRecord: CallRecord) {
        if(!mEnable){
            Timber.w("enable: $mEnable")
            return
        }
        if(isRecordStarted.get()) {
            releaseRecorder()
        } else {
            val audioFile = createAudioFile(callRecord)
            if(null == audioFile) {
                releaseRecorder()
                return
            }
            
            mAudioRecord = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile.absolutePath)
                setOnErrorListener { _, _, _ -> }
            }
            
            mAudioRecord?.prepare()
            
            mAudioRecord?.start()
            isRecordStarted.set(true)
            
            callRecord.audioFilePath = audioFile.absolutePath
            callRecord.save()
        }
    }
    
    fun stopRecord() {
        if(!mEnable){
            Timber.w("enable: $mEnable")
            return
        }
        if(null != mAudioRecord && isRecordStarted.get()) {
            releaseRecorder()
        }
    }
    
    private fun createAudioFile(callRecord: CallRecord): File? {
        val fileNameBuilder = StringBuilder()
        fileNameBuilder.append(callRecord.recordId)
        fileNameBuilder.append("_")
        
        fileNameBuilder.append(CallType.values()[callRecord.callType].name)
        fileNameBuilder.append("_")
        
        val dir = mCtx.getDir("CallAudio", Context.MODE_PRIVATE)
        
        if(!dir.exists()) {
            dir.mkdirs()
        }
        
        val fileName = fileNameBuilder.toString()
        return File.createTempFile(fileName, ".amr", dir)
    }
    
    private fun releaseRecorder() {
        mAudioRecord?.stop()
        mAudioRecord?.release()
        isRecordStarted.set(false)
        mAudioRecord = null
    }
}
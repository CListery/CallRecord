package com.yh.recordlib.media

import android.app.Application
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.yh.recordlib.entity.CallRecord
import com.yh.recordlib.entity.CallType
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by CYH on 2019-05-30 16:26
 */

class AudioRecordHelper(private val mCtx: Application) {
    
    companion object {
        
        private var mInstance: AudioRecordHelper? = null
        
        @Synchronized
        fun get(ctx: Application): AudioRecordHelper {
            if(null == mInstance) {
                mInstance = AudioRecordHelper(
                    ctx
                )
            }
            return mInstance!!
        }
        
        /**
         * 采样率，现在能够保证在所有设备上使用的采样率是44100Hz, 但是其他的采样率（22050, 16000, 11025）在一些设备上也可以使用。
         */
        private val SAMPLE_RATE_INHZ = 44100
        
        /**
         * 声道数。CHANNEL_IN_MONO and CHANNEL_IN_STEREO. 其中CHANNEL_IN_MONO是可以保证在所有设备能够使用的。
         */
        private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        /**
         * 返回的音频数据的格式。 ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, and ENCODING_PCM_FLOAT.
         */
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    private var mMinBufferSize: Int? = null
    private var mAudioRecord: AudioRecord? = null
    private val isRecordStarted = AtomicBoolean(false)
    
    fun startRecord(callRecord: CallRecord) {
        mMinBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT
        )
        if(null == mMinBufferSize) {
            return
        }
        mAudioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE_INHZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            mMinBufferSize!!
        )
        
        if(null == mAudioRecord) {
            return
        }
        
        if(mAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            return
        }
        
        if(isRecordStarted.get()) {
            releaseRecorder()
        } else {
            mAudioRecord?.startRecording()
            isRecordStarted.set(true)
            
            val audioFile = createAudioFile(callRecord)
            if(null == audioFile) {
                releaseRecorder()
                return
            }
            Thread(Runnable {
                val os: FileOutputStream? = FileOutputStream(audioFile)
                os?.use {
                    val data = ByteArray(mMinBufferSize!!)
                    while(isRecordStarted.get()) {
                        val read = mAudioRecord?.read(data, 0, mMinBufferSize!!)
//                        data.forEachIndexed { index, byte ->
//                            data[index] = (byte * 5).toByte()
//                        }
                        // 如果读取音频数据没有出现错误，就将数据写入到文件
                        if(AudioRecord.ERROR_INVALID_OPERATION != read) {
                            os.write(data)
                        }
                    }
                }
            }).start()
        }
    }
    
    fun stopRecord() {
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
        
        fileNameBuilder.append(callRecord.phoneNumber)
        fileNameBuilder.append("_")
        
        val dir = mCtx.getDir("CallAudio", Context.MODE_PRIVATE)
        
        if(!dir.exists()) {
            dir.mkdirs()
        }
        
        val fileName = fileNameBuilder.toString()
        val suffix: String = ".pcm"
        
        return File.createTempFile(fileName, suffix, dir)
    }
    
    private fun releaseRecorder() {
        mAudioRecord?.stop()
        mAudioRecord?.release()
        isRecordStarted.set(false)
        mAudioRecord = null
    }
}
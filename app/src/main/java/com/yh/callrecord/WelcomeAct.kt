package com.yh.callrecord

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import com.yh.sarl.launcher.ContractType
import com.yh.sarl.launcher.SimpleLauncher
import com.yh.sarl.launcher.SimpleLauncher.Companion.simpleLauncher
import com.yh.sarl.onFailure
import com.yh.sarl.onSuccess

class WelcomeAct : AppCompatActivity() {
    
    private val permissionsLauncher: SimpleLauncher<Array<String>, Map<String, Boolean>> =
        simpleLauncher(ContractType.RequestMultiplePermissions, this)
    
    override fun onResume() {
        super.onResume()
        permissionsLauncher
            .input(
                arrayOf(
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE,
                )
            )
            .checker { value -> value?.all { it.value } ?: false }
            .launch()
            .onSuccess {
                App.get().initCallRecord()
                startActivity(Intent(this, MainAct::class.java))
                finish()
            }.onFailure {
                finish()
            }
    }
    
}
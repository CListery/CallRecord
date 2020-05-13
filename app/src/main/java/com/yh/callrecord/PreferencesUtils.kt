package com.yh.callrecord

import android.content.Context
import android.content.SharedPreferences

object PreferencesUtils {
    
    private const val SHARED_PREFERENCES_NAME = "common"
    
    private val mCommonPref: SharedPreferences by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        App.get().getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    
    @JvmStatic
    @Synchronized
    fun getCommonPref(): SharedPreferences {
        return mCommonPref
    }
}

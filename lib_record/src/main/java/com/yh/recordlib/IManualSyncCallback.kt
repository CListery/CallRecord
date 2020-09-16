package com.yh.recordlib

import java.io.File

interface IManualSyncCallback : ISyncCallback {
    fun onSyncDone(logFile: File)
}
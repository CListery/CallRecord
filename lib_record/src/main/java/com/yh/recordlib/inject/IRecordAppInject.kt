package com.yh.recordlib.inject

import com.yh.appinject.IBaseAppInject
import com.yh.recordlib.RecordConfigure

/**
 * Created by CYH on 2020/3/23 09:12
 */
interface IRecordAppInject : IBaseAppInject {
    fun setRecordConfigure(configure: RecordConfigure)
    fun getRecordConfigure(): RecordConfigure
}
package com.yh.recordlib.entity

/**
 * Created by CYH on 2019-05-30 11:57
 */
enum class CallType {
    
    Unknown,
    CallIn,
    CallOut;
    
    companion object {
        
        @JvmStatic
        fun typeOf(ordinal: Int): CallType {
            return values().find { it.ordinal == ordinal } ?: Unknown
        }
    }
}
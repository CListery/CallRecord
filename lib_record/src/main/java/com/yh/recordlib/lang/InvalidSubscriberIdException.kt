package com.yh.recordlib.lang

/**
 * Created by CYH on 2019-06-14 14:33
 */
class InvalidSubscriberIdException(
    phoneId: Int, max: Int
) : RuntimeException("current id: $phoneId   Max: $max")
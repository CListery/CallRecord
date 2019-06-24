/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yh.recordlib.cons

/**
 * Contains a list of string constants used to get or set telephone properties
 * in the system. You can use [os.SystemProperties][android.os.SystemProperties]
 * to get and set these values.
 */
interface TelephonyProperties {
    
    companion object {
        //****** Baseband and Radio Interface version
        
        //TODO T: property strings do not have to be gsm specific
        //        change gsm.*operator.*" properties to "operator.*" properties
        
        val MAX_PHONE_COUNT_DUAL_SIM = 2
        
        val MAX_PHONE_COUNT_TRI_SIM = 3
        
        /**
         * Baseband version
         * Availability: property is available any time radio is on
         */
        val PROPERTY_BASEBAND_VERSION = "gsm.version.baseband"
        
        /** Radio Interface Layer (RIL) library implementation.  */
        val PROPERTY_RIL_IMPL = "gsm.version.ril-impl"
        
        //****** Current Network
        
        /** Alpha name of current registered operator.
         *
         *
         * Availability: when registered to a network. Result may be unreliable on
         * CDMA networks.
         */
        val PROPERTY_OPERATOR_ALPHA = "gsm.operator.alpha"
        //TODO: most of these properties are generic, substitute gsm. with phone. bug 1856959
        
        /** Numeric name (MCC+MNC) of current registered operator.
         *
         *
         * Availability: when registered to a network. Result may be unreliable on
         * CDMA networks.
         */
        val PROPERTY_OPERATOR_NUMERIC = "gsm.operator.numeric"
        
        /** 'true' if the device is on a manually selected network
         *
         * Availability: when registered to a network
         */
        val PROPERTY_OPERATOR_ISMANUAL = "operator.ismanual"
        
        /** 'true' if the device is considered roaming on this network for GSM
         * purposes.
         * Availability: when registered to a network
         */
        val PROPERTY_OPERATOR_ISROAMING = "gsm.operator.isroaming"
        
        /** The ISO country code equivalent of the current registered operator's
         * MCC (Mobile Country Code)
         *
         *
         * Availability: when registered to a network. Result may be unreliable on
         * CDMA networks.
         */
        val PROPERTY_OPERATOR_ISO_COUNTRY = "gsm.operator.iso-country"
        
        /**
         * The contents of this property is the value of the kernel command line
         * product_type variable that corresponds to a product that supports LTE on CDMA.
         * {@see BaseCommands#getLteOnCdmaMode()}
         */
        val PROPERTY_LTE_ON_CDMA_PRODUCT_TYPE = "telephony.lteOnCdmaProductType"
        
        val PROPERTY_LTE_ON_CDMA_DEVICE = "telephony.lteOnCdmaDevice"
        
        val CURRENT_ACTIVE_PHONE = "gsm.current.phone-type"
        
        //****** SIM Card
        /**
         * One of `"UNKNOWN"` `"ABSENT"` `"PIN_REQUIRED"`
         * `"PUK_REQUIRED"` `"NETWORK_LOCKED"` or `"READY"`
         */
        val PROPERTY_SIM_STATE = "gsm.sim.state"
        
        /** The MCC+MNC (mobile country code+mobile network code) of the
         * provider of the SIM. 5 or 6 decimal digits.
         * Availability: SIM state must be "READY"
         */
        val PROPERTY_ICC_OPERATOR_NUMERIC = "gsm.sim.operator.numeric"
        val PROPERTY_ICC_OPERATOR_NUMERIC2 = "gsm.sim.operator.numeric.2"
        
        /** PROPERTY_ICC_OPERATOR_ALPHA is also known as the SPN, or Service Provider Name.
         * Availability: SIM state must be "READY"
         */
        val PROPERTY_ICC_OPERATOR_ALPHA = "gsm.sim.operator.alpha"
        
        /** ISO country code equivalent for the SIM provider's country code */
        val PROPERTY_ICC_OPERATOR_ISO_COUNTRY = "gsm.sim.operator.iso-country"
        
        /**
         * Indicates the available radio technology.  Values include: `"unknown"`,
         * `"GPRS"`, `"EDGE"` and `"UMTS"`.
         */
        val PROPERTY_DATA_NETWORK_TYPE = "gsm.network.type"
        
        /** Indicate if phone is in emergency callback mode  */
        val PROPERTY_INECM_MODE = "ril.cdma.inecmmode"
        
        /** Indicate the timer value for exiting emergency callback mode  */
        val PROPERTY_ECM_EXIT_TIMER = "ro.cdma.ecmexittimer"
        
        /** the international dialing prefix of current operator network  */
        val PROPERTY_OPERATOR_IDP_STRING = "gsm.operator.idpstring"
        
        /**
         * Defines the schema for the carrier specified OTASP number
         */
        val PROPERTY_OTASP_NUM_SCHEMA = "ro.cdma.otaspnumschema"
        
        /**
         * Disable all calls including Emergency call when it set to true.
         */
        val PROPERTY_DISABLE_CALL = "ro.telephony.disable-call"
        
        /**
         * Set to true for vendor RIL's that send multiple UNSOL_CALL_RING notifications.
         */
        val PROPERTY_RIL_SENDS_MULTIPLE_CALL_RING = "ro.telephony.call_ring.multiple"
        
        /**
         * The number of milliseconds between CALL_RING notifications.
         */
        val PROPERTY_CALL_RING_DELAY = "ro.telephony.call_ring.delay"
        
        /**
         * Track CDMA SMS message id numbers to ensure they increment
         * monotonically, regardless of reboots.
         */
        val PROPERTY_CDMA_MSG_ID = "persist.radio.cdma.msgid"
        
        /**
         * Property to override DEFAULT_WAKE_LOCK_TIMEOUT
         */
        val PROPERTY_WAKE_LOCK_TIMEOUT = "ro.ril.wake_lock_timeout"
        
        /**
         * Set to true to indicate that the modem needs to be reset
         * when there is a radio technology change.
         */
        val PROPERTY_RESET_ON_RADIO_TECH_CHANGE = "persist.radio.reset_on_switch"
        
        /**
         * Set to false to disable SMS receiving, default is
         * the value of config_sms_capable
         */
        val PROPERTY_SMS_RECEIVE = "telephony.sms.receive"
        
        /**
         * Set to false to disable SMS sending, default is
         * the value of config_sms_capable
         */
        val PROPERTY_SMS_SEND = "telephony.sms.send"
        
        /**
         * Set to true to indicate a test CSIM card is used in the device.
         * This property is for testing purpose only. This should not be defined
         * in commercial configuration.
         */
        val PROPERTY_TEST_CSIM = "persist.radio.test-csim"
        
        /**
         * Ignore RIL_UNSOL_NITZ_TIME_RECEIVED completely, used for debugging/testing.
         */
        val PROPERTY_IGNORE_NITZ = "telephony.test.ignore.nitz"
        
        /**
         * Property to set multi sim feature.
         * Type:  String(dsds, dsda)
         */
        val PROPERTY_MULTI_SIM_CONFIG = "persist.radio.multisim.config"
        
        /**
         * Property to store default subscription.
         */
        val PROPERTY_DEFAULT_SUBSCRIPTION = "persist.radio.default.sub"
        
        /**
         * Property to enable MMS Mode.
         * Type: string ( default = silent, enable to = prompt )
         */
        val PROPERTY_MMS_TRANSACTION = "mms.transaction"
        
        /**
         * Set to the sim count.
         */
        val PROPERTY_SIM_COUNT = "ro.telephony.sim.count"
        
        /**
         * Controls audio route for video calls.
         * 0 - Use the default audio routing strategy.
         * 1 - Disable the speaker. Route the audio to Headset or Bluetooth
         * or Earpiece, based on the default audio routing strategy.
         */
        val PROPERTY_VIDEOCALL_AUDIO_OUTPUT = "persist.radio.call.audio.output"
        
        val PROPERTY_RIL_SIM_ICCID_KEYS = arrayOf(
            "ril.iccid.sim1", "ril.iccid.sim2", "ril.iccid.sim3", "ril.iccid.sim4"
        )
    }
}
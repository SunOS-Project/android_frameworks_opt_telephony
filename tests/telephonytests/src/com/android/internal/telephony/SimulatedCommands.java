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

/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.internal.telephony.test;

import android.compat.annotation.UnsupportedAppUsage;
import android.hardware.radio.RadioError;
import android.hardware.radio.V1_2.VoiceRegStateResult;
import android.hardware.radio.V1_4.DataRegStateResult;
import android.hardware.radio.V1_4.PdpProtocolType;
import android.hardware.radio.V1_4.SetupDataCallResult;
import android.hardware.radio.modem.ImeiInfo;
import android.net.KeepalivePacketData;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemClock;
import android.os.WorkSource;
import android.telephony.CarrierRestrictionRules;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkScanRequest;
import android.telephony.PcoData;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SignalThresholdInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;
import android.telephony.emergency.EmergencyNumber;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CallFailCause;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.LastCallFailCause;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILUtils;
import com.android.internal.telephony.RadioCapability;
import com.android.internal.telephony.SmsResponse;
import com.android.internal.telephony.SrvccConnection;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.AdnCapacity;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.ReceivedPhonebookRecords;
import com.android.internal.telephony.uicc.SimPhonebookRecord;
import com.android.telephony.Rlog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SimulatedCommands extends BaseCommands
        implements CommandsInterface, SimulatedRadioControl {
    private static final String LOG_TAG = "SimulatedCommands";

    private enum SimLockState {
        NONE,
        REQUIRE_PIN,
        REQUIRE_PUK,
        SIM_PERM_LOCKED
    }

    private enum SimFdnState {
        NONE,
        REQUIRE_PIN2,
        REQUIRE_PUK2,
        SIM_PERM_LOCKED
    }

    private static final SimLockState INITIAL_LOCK_STATE = SimLockState.NONE;
    public static final String DEFAULT_SIM_PIN_CODE = "1234";
    private static final String SIM_PUK_CODE = "12345678";
    private static final SimFdnState INITIAL_FDN_STATE = SimFdnState.NONE;
    public static final String DEFAULT_SIM_PIN2_CODE = "5678";
    private static final String SIM_PUK2_CODE = "87654321";
    public static final String FAKE_LONG_NAME = "Fake long name";
    public static final String FAKE_SHORT_NAME = "Fake short name";
    public static final String FAKE_MCC_MNC = "310260";
    public static final String FAKE_IMEI = "012345678901234";
    public static final String FAKE_IMEISV = "99";
    public static final String FAKE_ESN = "1234";
    public static final String FAKE_MEID = "1234";
    public static final int DEFAULT_PIN1_ATTEMPT = 5;
    public static final int DEFAULT_PIN2_ATTEMPT = 5;
    public static final int ICC_AUTHENTICATION_MODE_DEFAULT = 0;
    public static final int ICC_AUTHENTICATION_MODE_NULL = 1;
    public static final int ICC_AUTHENTICATION_MODE_TIMEOUT = 2;
    // Maximum time in millisecond to wait for a IccSim Challenge before assuming it will not
    // arrive and returning null to the callers.
    public static final  long ICC_SIM_CHALLENGE_TIMEOUT_MILLIS = 2500;

    //***** Instance Variables

    @UnsupportedAppUsage
    SimulatedGsmCallState simulatedCallState;
    HandlerThread mHandlerThread;
    SimLockState mSimLockedState;
    boolean mSimLockEnabled;
    int mPinUnlockAttempts;
    int mPukUnlockAttempts;
    String mPinCode;
    int mPin1attemptsRemaining = DEFAULT_PIN1_ATTEMPT;
    SimFdnState mSimFdnEnabledState;
    boolean mSimFdnEnabled;
    int mPin2UnlockAttempts;
    int mPuk2UnlockAttempts;
    int mPreferredNetworkType;
    int mAllowedNetworkType;
    String mPin2Code;
    boolean mSsnNotifyOn = false;
    private int mVoiceRegState = NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
    private int mVoiceRadioTech = ServiceState.RIL_RADIO_TECHNOLOGY_UMTS;
    private int mDataRegState = NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
    private int mDataRadioTech = ServiceState.RIL_RADIO_TECHNOLOGY_UMTS;
    public boolean mCssSupported;
    public int mRoamingIndicator;
    public int mSystemIsInPrl;
    public int mDefaultRoamingIndicator;
    public int mReasonForDenial;
    public int mMaxDataCalls;
    public boolean mSendSetGsmBroadcastConfigResponse = true;
    public boolean mSendGetSmscAddressResponse = true;

    private SignalStrength mSignalStrength;
    private List<CellInfo> mCellInfoList = null;
    private boolean mShouldReturnCellInfo = true;
    private int[] mImsRegState;
    private IccCardStatus mIccCardStatus;
    private IccIoResult mIccIoResultForApduLogicalChannel;
    private int mChannelId = IccOpenLogicalChannelResponse.INVALID_CHANNEL;

    private Object mDataRegStateResult;
    private Object mVoiceRegStateResult;

    int mPausedResponseCount;
    ArrayList<Message> mPausedResponses = new ArrayList<>();

    int mNextCallFailCause = CallFailCause.NORMAL_CLEARING;

    @UnsupportedAppUsage
    private boolean mDcSuccess = true;
    private SetupDataCallResult mSetupDataCallResult;
    private boolean mIsRadioPowerFailResponse = false;
    private boolean mIsReportSmsMemoryStatusFailResponse = false;
    private String smscAddress;

    public boolean mSetRadioPowerForEmergencyCall;
    public boolean mSetRadioPowerAsSelectedPhoneForEmergencyCall;

    public boolean mCallWaitActivated = false;
    private SrvccConnection[] mSrvccConnections;

    // mode for Icc Sim Authentication
    private int mAuthenticationMode;

    private int[] mImsRegistrationInfo = new int[4];

    private boolean mN1ModeEnabled = false;
    private boolean mVonrEnabled = false;

    //***** Constructor
    public
    SimulatedCommands() {
        super(null);  // Don't log statistics
        mHandlerThread = new HandlerThread("SimulatedCommands");
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();

        simulatedCallState = new SimulatedGsmCallState(looper);

        setRadioState(TelephonyManager.RADIO_POWER_ON, false /* forceNotifyRegistrants */);
        mSimLockedState = INITIAL_LOCK_STATE;
        mSimLockEnabled = (mSimLockedState != SimLockState.NONE);
        mPinCode = DEFAULT_SIM_PIN_CODE;
        mSimFdnEnabledState = INITIAL_FDN_STATE;
        mSimFdnEnabled = (mSimFdnEnabledState != SimFdnState.NONE);
        mPin2Code = DEFAULT_SIM_PIN2_CODE;
        mAuthenticationMode = ICC_AUTHENTICATION_MODE_DEFAULT;
    }

    public void dispose() throws Exception {
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread.join();
        }
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    //***** CommandsInterface implementation

    @Override
    public void getIccCardStatus(Message result) {
        SimulatedCommandsVerifier.getInstance().getIccCardStatus(result);
        if (mIccCardStatus != null) {
            resultSuccess(result, mIccCardStatus);
        } else {
            resultFail(result, null, new RuntimeException("IccCardStatus not set"));
        }
    }

    @Override
    public void supplyIccPin(String pin, Message result)  {
        if (mSimLockedState != SimLockState.REQUIRE_PIN) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: wrong state, state=" +
                    mSimLockedState);
            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            resultFail(result, null, ex);
            return;
        }

        if (pin != null && pin.equals(mPinCode)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: success!");
            mPinUnlockAttempts = 0;
            mSimLockedState = SimLockState.NONE;
            mIccStatusChangedRegistrants.notifyRegistrants();

            resultSuccess(result, null);

            return;
        }

        if (result != null) {
            mPinUnlockAttempts ++;

            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: failed! attempt=" +
                    mPinUnlockAttempts);
            if (mPinUnlockAttempts >= DEFAULT_PIN1_ATTEMPT) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: set state to REQUIRE_PUK");
                mSimLockedState = SimLockState.REQUIRE_PUK;
            }

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            resultFail(result, null, ex);
        }
    }

    @Override
    public void supplyIccPuk(String puk, String newPin, Message result)  {
        if (mSimLockedState != SimLockState.REQUIRE_PUK) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: wrong state, state=" +
                    mSimLockedState);
            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            resultFail(result, null, ex);
            return;
        }

        if (puk != null && puk.equals(SIM_PUK_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: success!");
            mSimLockedState = SimLockState.NONE;
            mPukUnlockAttempts = 0;
            mIccStatusChangedRegistrants.notifyRegistrants();

            resultSuccess(result, null);
            return;
        }

        if (result != null) {
            mPukUnlockAttempts ++;

            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: failed! attempt=" +
                    mPukUnlockAttempts);
            if (mPukUnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: set state to SIM_PERM_LOCKED");
                mSimLockedState = SimLockState.SIM_PERM_LOCKED;
            }

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            resultFail(result, null, ex);
        }
    }

    @Override
    public void supplyIccPin2(String pin2, Message result)  {
        if (mSimFdnEnabledState != SimFdnState.REQUIRE_PIN2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: wrong state, state=" +
                    mSimFdnEnabledState);
            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            resultFail(result, null, ex);
            return;
        }

        if (pin2 != null && pin2.equals(mPin2Code)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: success!");
            mPin2UnlockAttempts = 0;
            mSimFdnEnabledState = SimFdnState.NONE;

            resultSuccess(result, null);
            return;
        }

        if (result != null) {
            mPin2UnlockAttempts ++;

            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: failed! attempt=" +
                    mPin2UnlockAttempts);
            if (mPin2UnlockAttempts >= DEFAULT_PIN2_ATTEMPT) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: set state to REQUIRE_PUK2");
                mSimFdnEnabledState = SimFdnState.REQUIRE_PUK2;
            }

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            resultFail(result, null, ex);
        }
    }

    @Override
    public void supplyIccPuk2(String puk2, String newPin2, Message result)  {
        if (mSimFdnEnabledState != SimFdnState.REQUIRE_PUK2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: wrong state, state=" +
                    mSimLockedState);
            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            resultFail(result, null, ex);
            return;
        }

        if (puk2 != null && puk2.equals(SIM_PUK2_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: success!");
            mSimFdnEnabledState = SimFdnState.NONE;
            mPuk2UnlockAttempts = 0;

            resultSuccess(result, null);
            return;
        }

        if (result != null) {
            mPuk2UnlockAttempts ++;

            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: failed! attempt=" +
                    mPuk2UnlockAttempts);
            if (mPuk2UnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: set state to SIM_PERM_LOCKED");
                mSimFdnEnabledState = SimFdnState.SIM_PERM_LOCKED;
            }

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            resultFail(result, null, ex);
        }
    }

    @Override
    public void changeIccPin(String oldPin, String newPin, Message result)  {
        if (oldPin != null && oldPin.equals(mPinCode)) {
            mPinCode = newPin;
            resultSuccess(result, null);

            return;
        }

        Rlog.i(LOG_TAG, "[SimCmd] changeIccPin: pin failed!");

        CommandException ex = new CommandException(
                CommandException.Error.PASSWORD_INCORRECT);
        resultFail(result, null, ex);
    }

    @Override
    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        if (oldPin2 != null && oldPin2.equals(mPin2Code)) {
            mPin2Code = newPin2;
            resultSuccess(result, null);

            return;
        }

        Rlog.i(LOG_TAG, "[SimCmd] changeIccPin2: pin2 failed!");

        CommandException ex = new CommandException(
                CommandException.Error.PASSWORD_INCORRECT);
        resultFail(result, null, ex);
    }

    @Override
    public void
    changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        unimplemented(result);
    }

    @Override
    public void
    setSuppServiceNotifications(boolean enable, Message result) {
        resultSuccess(result, null);

        if (enable && mSsnNotifyOn) {
            Rlog.w(LOG_TAG, "Supp Service Notifications already enabled!");
        }

        mSsnNotifyOn = enable;
    }

    @Override
    public void queryFacilityLock(String facility, String pin,
                                   int serviceClass, Message result) {
        queryFacilityLockForApp(facility, pin, serviceClass, null, result);
    }

    @Override
    public void queryFacilityLockForApp(String facility, String pin, int serviceClass,
            String appId, Message result) {
        if (facility != null && facility.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (result != null) {
                int[] r = new int[1];
                r[0] = (mSimLockEnabled ? 1 : 0);
                Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: SIM is "
                        + (r[0] == 0 ? "unlocked" : "locked"));
                resultSuccess(result, r);
            }
            return;
        } else if (facility != null && facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
            if (result != null) {
                int[] r = new int[1];
                r[0] = (mSimFdnEnabled ? 1 : 0);
                Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: FDN is "
                        + (r[0] == 0 ? "disabled" : "enabled"));
                resultSuccess(result, r);
            }
            return;
        }

        unimplemented(result);
    }

    @Override
    public void setFacilityLock(String facility, boolean lockEnabled, String pin, int serviceClass,
            Message result) {
        setFacilityLockForApp(facility, lockEnabled, pin, serviceClass, null, result);
    }

    @Override
    public void setFacilityLockForApp(String facility, boolean lockEnabled,
                                 String pin, int serviceClass, String appId,
                                 Message result) {
        if (facility != null &&
                facility.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (pin != null && pin.equals(mPinCode)) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin is valid");
                mSimLockEnabled = lockEnabled;

                resultSuccess(result, null);

                return;
            }

            Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin failed!");

            CommandException ex = new CommandException(
                    CommandException.Error.GENERIC_FAILURE);
            resultFail(result, null, ex);

            return;
        }  else if (facility != null &&
                facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
            if (pin != null && pin.equals(mPin2Code)) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 is valid");
                mSimFdnEnabled = lockEnabled;

                resultSuccess(result, null);

                return;
            }

            Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 failed!");

            CommandException ex = new CommandException(
                    CommandException.Error.GENERIC_FAILURE);
            resultFail(result, null, ex);

            return;
        }

        unimplemented(result);
    }

    @Override
    public void supplyNetworkDepersonalization(String netpin, Message result) {
        unimplemented(result);
    }

    @Override
    public void supplySimDepersonalization(PersoSubState persoType,
            String conrolKey, Message result) {
        unimplemented(result);
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result contains a List of DriverCall
     *      The ar.result List is sorted by DriverCall.index
     */
    @Override
    public void getCurrentCalls (Message result) {
        SimulatedCommandsVerifier.getInstance().getCurrentCalls(result);
        if ((mState == TelephonyManager.RADIO_POWER_ON) && !isSimLocked()) {
            //Rlog.i("GSM", "[SimCmds] getCurrentCalls");
            resultSuccess(result, simulatedCallState.getDriverCalls());
        } else {
            //Rlog.i("GSM", "[SimCmds] getCurrentCalls: RADIO_OFF or SIM not ready!");
            resultFail(result, null,
                new CommandException(CommandException.Error.RADIO_NOT_AVAILABLE));
        }
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result contains a List of DataCallResponse
     */
    @Override
    public void getDataCallList(Message result) {
        ArrayList<SetupDataCallResult> dcCallList = new ArrayList<>(0);
        SimulatedCommandsVerifier.getInstance().getDataCallList(result);
        if (mSetupDataCallResult != null) {
            dcCallList.add(mSetupDataCallResult);
        }
        resultSuccess(result, dcCallList);
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    @Override
    public void dial(String address, boolean isEmergencyCall, EmergencyNumber emergencyNumberInfo,
                     boolean hasKnownUserIntentEmergency, int clirMode, Message result) {
        SimulatedCommandsVerifier.getInstance().dial(address, isEmergencyCall,
                emergencyNumberInfo, hasKnownUserIntentEmergency, clirMode, result);
        simulatedCallState.onDial(address);

        resultSuccess(result, null);
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    @Override
    public void dial(String address, boolean isEmergencyCall, EmergencyNumber emergencyNumberInfo,
                     boolean hasKnownUserIntentEmergency, int clirMode, UUSInfo uusInfo,
                     Message result) {
        SimulatedCommandsVerifier.getInstance().dial(address, isEmergencyCall,
                emergencyNumberInfo, hasKnownUserIntentEmergency, clirMode, uusInfo, result);
        simulatedCallState.onDial(address);

        resultSuccess(result, null);
    }

    @Override
    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }
    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is String containing IMSI on success
     */
    @Override
    public void getIMSIForApp(String aid, Message result) {
        resultSuccess(result, "012345678901234");
    }

    /**
     * Hang up one individual connection.
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     *
     *  3GPP 22.030 6.5.5
     *  "Releases a specific active call X"
     */
    @Override
    public void hangupConnection (int gsmIndex, Message result) {
        boolean success;

        success = simulatedCallState.onChld('1', (char)('0'+gsmIndex));

        if (!success){
            Rlog.i("GSM", "[SimCmd] hangupConnection: resultFail");
            resultFail(result, null, new RuntimeException("Hangup Error"));
        } else {
            Rlog.i("GSM", "[SimCmd] hangupConnection: resultSuccess");
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     *  "Releases all held calls or sets User Determined User Busy (UDUB)
     *   for a waiting call."
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void hangupWaitingOrBackground (Message result) {
        boolean success;

        success = simulatedCallState.onChld('0', '\0');

        if (!success){
            resultFail(result, null, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Releases all active calls (if any exist) and accepts
     *  the other (held or waiting) call."
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void hangupForegroundResumeBackground (Message result) {
        boolean success;

        success = simulatedCallState.onChld('1', '\0');

        if (!success){
            resultFail(result, null, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Places all active calls (if any exist) on hold and accepts
     *  the other (held or waiting) call."
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void switchWaitingOrHoldingAndActive (Message result) {
        boolean success;

        success = simulatedCallState.onChld('2', '\0');

        if (!success){
            resultFail(result, null, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Adds a held call to the conversation"
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void conference (Message result) {
        boolean success;

        success = simulatedCallState.onChld('3', '\0');

        if (!success){
            resultFail(result, null, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Connects the two calls and disconnects the subscriber from both calls"
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void explicitCallTransfer (Message result) {
        boolean success;

        success = simulatedCallState.onChld('4', '\0');

        if (!success){
            resultFail(result, null, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Places all active calls on hold except call X with which
     *  communication shall be supported."
     */
    @Override
    public void separateConnection (int gsmIndex, Message result) {
        boolean success;

        char ch = (char)(gsmIndex + '0');
        success = simulatedCallState.onChld('2', ch);

        if (!success){
            resultFail(result, null, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @UnsupportedAppUsage
    @Override
    public void acceptCall (Message result) {
        boolean success;

        SimulatedCommandsVerifier.getInstance().acceptCall(result);
        success = simulatedCallState.onAnswer();

        if (!success){
            resultFail(result, null, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     *  also known as UDUB
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void rejectCall (Message result) {
        boolean success;

        success = simulatedCallState.onChld('0', '\0');

        if (!success){
            resultFail(result, null, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * cause code returned as Integer in Message.obj.response
     * Returns integer cause code defined in TS 24.008
     * Annex H or closest approximation.
     * Most significant codes:
     * - Any defined in 22.001 F.4 (for generating busy/congestion)
     * - Cause 68: ACM >= ACMMax
     */
    @Override
    public void getLastCallFailCause (Message result) {
        LastCallFailCause mFailCause = new LastCallFailCause();
        mFailCause.causeCode = mNextCallFailCause;
        resultSuccess(result, mFailCause);
    }

    @Override
    public void setMute (boolean enableMute, Message result) {unimplemented(result);}

    @Override
    public void getMute (Message result) {unimplemented(result);}

    public void setSignalStrength(SignalStrength signalStrength) {
        mSignalStrength = signalStrength;
    }

    @Override
    public void getSignalStrength (Message result) {
        if (mSignalStrength == null) {
            mSignalStrength = new SignalStrength(
                    new CellSignalStrengthCdma(),
                    new CellSignalStrengthGsm(20, 0, CellInfo.UNAVAILABLE),
                    new CellSignalStrengthWcdma(),
                    new CellSignalStrengthTdscdma(),
                    new CellSignalStrengthLte(),
                    new CellSignalStrengthNr());
        }
        resultSuccess(result, mSignalStrength);
    }

     /**
     * Assign a specified band for RF configuration.
     *
     * @param bandMode one of BM_*_BAND
     * @param result is callback message
     */
    @Override
    public void setBandMode (int bandMode, Message result) {
        resultSuccess(result, null);
    }

    /**
     * Query the list of band mode supported by RF.
     *
     * @param result is callback message
     *        ((AsyncResult)response.obj).result  is an int[] where int[0] is
     *        the size of the array and the rest of each element representing
     *        one available BM_*_BAND
     */
    @Override
    public void queryAvailableBandMode (Message result) {
        int ret[] = new int [4];

        ret[0] = 4;
        ret[1] = Phone.BM_US_BAND;
        ret[2] = Phone.BM_JPN_BAND;
        ret[3] = Phone.BM_AUS_BAND;

        resultSuccess(result, ret);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendTerminalResponse(String contents, Message response) {
        resultSuccess(response, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendEnvelope(String contents, Message response) {
        resultSuccess(response, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendEnvelopeWithStatus(String contents, Message response) {
        resultSuccess(response, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleCallSetupRequestFromSim(
            boolean accept, Message response) {
        resultSuccess(response, null);
    }

    public void setVoiceRadioTech(int voiceRadioTech) {
        mVoiceRadioTech = voiceRadioTech;
    }

    public void setVoiceRegState(int voiceRegState) {
        mVoiceRegState = voiceRegState;
    }

    /**
     * response.obj.result is an String[14]
     * See ril.h for details
     *
     * Please note that registration state 4 ("unknown") is treated
     * as "out of service" above
     */
    @Override
    public void getVoiceRegistrationState(Message result) {
        mGetVoiceRegistrationStateCallCount.incrementAndGet();

        Object ret = mVoiceRegStateResult;
        if (ret == null) {
            ret = new VoiceRegStateResult();
            ((VoiceRegStateResult) ret).regState = mVoiceRegState;
            ((VoiceRegStateResult) ret).rat = mVoiceRadioTech;
            ((VoiceRegStateResult) ret).cssSupported = mCssSupported;
            ((VoiceRegStateResult) ret).roamingIndicator = mRoamingIndicator;
            ((VoiceRegStateResult) ret).systemIsInPrl = mSystemIsInPrl;
            ((VoiceRegStateResult) ret).defaultRoamingIndicator = mDefaultRoamingIndicator;
            ((VoiceRegStateResult) ret).reasonForDenial = mReasonForDenial;
        }

        resultSuccess(result, ret);
    }

    private final AtomicInteger mGetVoiceRegistrationStateCallCount = new AtomicInteger(0);

    @VisibleForTesting
    public int getGetVoiceRegistrationStateCallCount() {
        return mGetVoiceRegistrationStateCallCount.get();
    }

    public void setDataRadioTech(int radioTech) {
        mDataRadioTech = radioTech;
    }

    public void setDataRegState(int dataRegState) {
        mDataRegState = dataRegState;
    }

    @Override
    public void getDataRegistrationState(Message result) {
        mGetDataRegistrationStateCallCount.incrementAndGet();

        Object ret = mDataRegStateResult;
        if (ret == null) {
            ret = new DataRegStateResult();
            ((DataRegStateResult) ret).base.regState = mDataRegState;
            ((DataRegStateResult) ret).base.rat = mDataRadioTech;
            ((DataRegStateResult) ret).base.maxDataCalls = mMaxDataCalls;
            ((DataRegStateResult) ret).base.reasonDataDenied = mReasonForDenial;
        }

        resultSuccess(result, ret);
    }

    private final AtomicInteger mGetDataRegistrationStateCallCount = new AtomicInteger(0);

    @VisibleForTesting
    public int getGetDataRegistrationStateCallCount() {
        return mGetDataRegistrationStateCallCount.get();
    }

    /**
     * response.obj.result is a String[3]
     * response.obj.result[0] is long alpha or null if unregistered
     * response.obj.result[1] is short alpha or null if unregistered
     * response.obj.result[2] is numeric or null if unregistered
     */
    @Override
    public void getOperator(Message result) {
        mGetOperatorCallCount.incrementAndGet();
        String[] ret = new String[3];

        ret[0] = FAKE_LONG_NAME;
        ret[1] = FAKE_SHORT_NAME;
        ret[2] = FAKE_MCC_MNC;

        resultSuccess(result, ret);
    }

    private final AtomicInteger mGetOperatorCallCount = new AtomicInteger(0);

    @VisibleForTesting
    public int getGetOperatorCallCount() {
        return mGetOperatorCallCount.get();
    }

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void sendDtmf(char c, Message result) {
        resultSuccess(result, null);
    }

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void startDtmf(char c, Message result) {
        SimulatedCommandsVerifier.getInstance().startDtmf(c, result);
        resultSuccess(result, null);
    }

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void stopDtmf(Message result) {
        resultSuccess(result, null);
    }

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        SimulatedCommandsVerifier.getInstance().sendBurstDtmf(dtmfString, on, off, result);
        resultSuccess(result, null);
    }

    /**
     * smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     */
    @Override
    public void sendSMS (String smscPDU, String pdu, Message result) {
        SimulatedCommandsVerifier.getInstance().sendSMS(smscPDU, pdu, result);
        resultSuccess(result, new SmsResponse(0 /*messageRef*/, null, SmsResponse.NO_ERROR_CODE));
    }

    /**
     * Send an SMS message, Identical to sendSMS,
     * except that more messages are expected to be sent soon
     * smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     */
    @Override
    public void sendSMSExpectMore (String smscPDU, String pdu, Message result) {
        SimulatedCommandsVerifier.getInstance().sendSMSExpectMore(smscPDU, pdu, result);
        resultSuccess(result, new SmsResponse(0 /*messageRef*/, null, SmsResponse.NO_ERROR_CODE));
    }

    @Override
    public void deleteSmsOnSim(int index, Message response) {
        Rlog.d(LOG_TAG, "Delete message at index " + index);
        unimplemented(response);
    }

    @Override
    public void deleteSmsOnRuim(int index, Message response) {
        Rlog.d(LOG_TAG, "Delete RUIM message at index " + index);
        unimplemented(response);
    }

    @Override
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        Rlog.d(LOG_TAG, "Write SMS to SIM with status " + status);
        unimplemented(response);
    }

    @Override
    public void writeSmsToRuim(int status, byte[] pdu, Message response) {
        Rlog.d(LOG_TAG, "Write SMS to RUIM with status " + status);
        unimplemented(response);
    }

    public void setDataCallResult(final boolean success, final SetupDataCallResult dcResult) {
        mSetupDataCallResult = dcResult;
        mDcSuccess = success;
    }

    public void triggerNITZupdate(String NITZStr) {
        if (NITZStr != null) {
            mNITZTimeRegistrant.notifyRegistrant(new AsyncResult (null, new Object[]{NITZStr,
                    SystemClock.elapsedRealtime()}, null));
        }
    }

    public void triggerNITZupdate(String NITZStr, long ageMs) {
        if (NITZStr != null) {
            mNITZTimeRegistrant.notifyRegistrant(new AsyncResult (null, new Object[]{NITZStr,
                    SystemClock.elapsedRealtime(), ageMs}, null));
        }
    }

    @Override
    public void setupDataCall(int accessNetworkType, DataProfile dataProfile, boolean allowRoaming,
            int reason, LinkProperties linkProperties, int pduSessionId, NetworkSliceInfo sliceInfo,
            TrafficDescriptor trafficDescriptor, boolean matchAllRuleAllowed, Message result) {

        SimulatedCommandsVerifier.getInstance().setupDataCall(accessNetworkType, dataProfile,
                allowRoaming, reason, linkProperties, pduSessionId, sliceInfo, trafficDescriptor,
                matchAllRuleAllowed, result);

        if (mSetupDataCallResult == null) {
            try {
                mSetupDataCallResult = new SetupDataCallResult();
                mSetupDataCallResult.cause = 0;
                mSetupDataCallResult.suggestedRetryTime = -1;
                mSetupDataCallResult.cid = 1;
                mSetupDataCallResult.active = 2;
                mSetupDataCallResult.type = PdpProtocolType.IP;
                mSetupDataCallResult.ifname = "rmnet_data7";
                mSetupDataCallResult.addresses = new ArrayList<>(List.of("12.34.56.78"));
                mSetupDataCallResult.dnses = new ArrayList<>(List.of("98.76.54.32"));
                mSetupDataCallResult.gateways = new ArrayList<>(List.of("11.22.33.44"));
                mSetupDataCallResult.pcscf = new ArrayList<>(List.of(
                        "fd00:976a:c305:1d::8 fd00:976a:c202:1d::7 fd00:976a:c305:1d::5"));
                mSetupDataCallResult.mtu = 1440;
            } catch (Exception e) {
                Rlog.e(LOG_TAG, "setupDataCall: e=" + e);
            }
        }

        DataCallResponse response = RILUtils.convertHalDataCallResult(mSetupDataCallResult);
        if (mDcSuccess) {
            resultSuccess(result, response);
        } else {
            resultFail(result, response, new RuntimeException("Setup data call failed!"));
        }
    }

    @Override
    public void deactivateDataCall(int cid, int reason, Message result) {
        SimulatedCommandsVerifier.getInstance().deactivateDataCall(cid, reason, result);
        resultSuccess(result, RadioError.NONE);
    }

    @Override
    public void setPreferredNetworkType(int networkType , Message result) {
        SimulatedCommandsVerifier.getInstance().setPreferredNetworkType(networkType, result);
        mPreferredNetworkType = networkType;
        resultSuccess(result, null);
    }

    @Override
    public void getPreferredNetworkType(Message result) {
        SimulatedCommandsVerifier.getInstance().getPreferredNetworkType(result);
        int ret[] = new int[1];

        ret[0] = mPreferredNetworkType;
        resultSuccess(result, ret);
    }

    @Override
    public void setAllowedNetworkTypesBitmap(
            @TelephonyManager.NetworkTypeBitMask int networkTypeBitmask, Message response) {
        SimulatedCommandsVerifier.getInstance()
            .setAllowedNetworkTypesBitmap(networkTypeBitmask, response);
        mAllowedNetworkType = networkTypeBitmask;
        resultSuccess(response, null);
    }

    @Override
    public void getAllowedNetworkTypesBitmap(Message response) {
        SimulatedCommandsVerifier.getInstance().getAllowedNetworkTypesBitmap(response);
        int[] ret = new int[1];

        ret[0] = mAllowedNetworkType;
        resultSuccess(response, ret);
    }

    @Override
    public void setLocationUpdates(boolean enable, Message response) {
        SimulatedCommandsVerifier.getInstance().setLocationUpdates(enable, response);
        resultSuccess(response, null);
    }

    @Override
    public void getSmscAddress(Message result) {
        SimulatedCommandsVerifier.getInstance().getSmscAddress(result);
        if (mSendGetSmscAddressResponse) {
            resultSuccess(result, smscAddress);
        }
    }

    @Override
    public void setSmscAddress(String address, Message result) {
        smscAddress = address;
        resultSuccess(result, null);
        SimulatedCommandsVerifier.getInstance().setSmscAddress(address, result);
    }

    @Override
    public void reportSmsMemoryStatus(boolean available, Message result) {
        if (!mIsReportSmsMemoryStatusFailResponse) {
            resultSuccess(result, null);
        } else {
            CommandException ex = new CommandException(CommandException.Error.GENERIC_FAILURE);
            resultFail(result, null, ex);
        }
        SimulatedCommandsVerifier.getInstance().reportSmsMemoryStatus(available, result);
    }

    public void setReportSmsMemoryStatusFailResponse(boolean fail) {
        mIsReportSmsMemoryStatusFailResponse = fail;
    }

    @Override
    public void reportStkServiceIsRunning(Message result) {
        resultSuccess(result, null);
    }

    @Override
    public void getCdmaSubscriptionSource(Message result) {
        unimplemented(result);
    }

    private boolean isSimLocked() {
        if (mSimLockedState != SimLockState.NONE) {
            return true;
        }
        return false;
    }

    @Override
    public void setRadioPower(boolean on, boolean forEmergencyCall,
            boolean preferredForEmergencyCall, Message result) {
        if (mIsRadioPowerFailResponse) {
            resultFail(result, null, new RuntimeException("setRadioPower failed!"));
            return;
        }

        mSetRadioPowerForEmergencyCall = forEmergencyCall;
        mSetRadioPowerAsSelectedPhoneForEmergencyCall = preferredForEmergencyCall;

        if(on) {
            setRadioState(TelephonyManager.RADIO_POWER_ON, false /* forceNotifyRegistrants */);
        } else {
            setRadioState(TelephonyManager.RADIO_POWER_OFF, false /* forceNotifyRegistrants */);
        }
        resultSuccess(result, null);
    }


    @Override
    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message result) {
        unimplemented(result);
        SimulatedCommandsVerifier.getInstance().
                acknowledgeLastIncomingGsmSms(success, cause, result);
    }

    @Override
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message result) {
        unimplemented(result);
    }

    @Override
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu,
            Message result) {
        unimplemented(result);
    }

    @Override
    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data,
            String pin2, Message response) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null, response);
    }

    /**
     * parameters equivalent to 27.007 AT+CRSM command
     * response.obj will be an AsyncResult
     * response.obj.userObj will be a SimIoResult on success
     */
    @Override
    public void iccIOForApp (int command, int fileid, String path, int p1, int p2,
                       int p3, String data, String pin2, String aid, Message result) {
        unimplemented(result);
    }

    /**
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * 1 for "CLIP is provisioned", and 0 for "CLIP is not provisioned".
     *
     * @param response is callback message
     */
    @Override
    public void queryCLIP(Message response) { unimplemented(response); }


    /**
     * response.obj will be a an int[2]
     *
     * response.obj[0] will be TS 27.007 +CLIR parameter 'n'
     *  0 presentation indicator is used according to the subscription of the CLIR service
     *  1 CLIR invocation
     *  2 CLIR suppression
     *
     * response.obj[1] will be TS 27.007 +CLIR parameter 'm'
     *  0 CLIR not provisioned
     *  1 CLIR provisioned in permanent mode
     *  2 unknown (e.g. no network, etc.)
     *  3 CLIR temporary mode presentation restricted
     *  4 CLIR temporary mode presentation allowed
     */

    @Override
    public void getCLIR(Message result) {unimplemented(result);}

    /**
     * clirMode is one of the CLIR_* constants above
     *
     * response.obj is null
     */

    @Override
    public void setCLIR(int clirMode, Message result) {unimplemented(result);}

    /**
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * 0 for disabled, 1 for enabled.
     *
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */

    @Override
    public void queryCallWaiting(int serviceClass, Message response) {
        if (response != null && serviceClass == SERVICE_CLASS_NONE) {
            int[] r = new int[2];
            r[0] = (mCallWaitActivated ? 1 : 0);
            r[1] = (mCallWaitActivated ? SERVICE_CLASS_VOICE : SERVICE_CLASS_NONE);
            resultSuccess(response, r);
            return;
        }

        unimplemented(response);
    }

    /**
     * @param enable is true to enable, false to disable
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */
    @Override
    public void setCallWaiting(boolean enable, int serviceClass,
            Message response) {
        if ((serviceClass & SERVICE_CLASS_VOICE) == SERVICE_CLASS_VOICE) {
            mCallWaitActivated = enable;
        }
        if (response != null) {
            resultSuccess(response, null);
        }
    }

    /**
     * @param action is one of CF_ACTION_*
     * @param cfReason is one of CF_REASON_*
     * @param serviceClass is a sum of SERVICE_CLASSS_*
     */
    @Override
    public void setCallForward(int action, int cfReason, int serviceClass,
            String number, int timeSeconds, Message result) {
        SimulatedCommandsVerifier.getInstance().setCallForward(action, cfReason, serviceClass,
                number, timeSeconds, result);
        resultSuccess(result, null);
    }

    /**
     * cfReason is one of CF_REASON_*
     *
     * ((AsyncResult)response.obj).result will be an array of
     * CallForwardInfo's
     *
     * An array of length 0 means "disabled for all codes"
     */
    @Override
    public void queryCallForwardStatus(int cfReason, int serviceClass,
            String number, Message result) {
        SimulatedCommandsVerifier.getInstance().queryCallForwardStatus(cfReason, serviceClass,
                number, result);
        resultSuccess(result, null);
    }

    @Override
    public void setNetworkSelectionModeAutomatic(Message result) {
        SimulatedCommandsVerifier.getInstance().setNetworkSelectionModeAutomatic(result);
        mMockNetworkSelectionMode = 0;
    }
    @Override
    public void exitEmergencyCallbackMode(Message result) {unimplemented(result);}
    @Override
    public void setNetworkSelectionModeManual(String operatorNumeric, int ran, Message result) {
        SimulatedCommandsVerifier.getInstance().setNetworkSelectionModeManual(
                operatorNumeric, ran, result);
        mMockNetworkSelectionMode = 1;
    }
    @Override
    public void setNetworkSelectionModeManual(OperatorInfo network, Message result) {
        unimplemented(result);
    }

    /**
     * Queries whether the current network selection mode is automatic
     * or manual
     *
     * ((AsyncResult)response.obj).result  is an int[] with element [0] being
     * a 0 for automatic selection and a 1 for manual selection
     */

    @Override
    public void getNetworkSelectionMode(Message result) {
        SimulatedCommandsVerifier.getInstance().getNetworkSelectionMode(result);
        getNetworkSelectionModeCallCount.incrementAndGet();
        int ret[] = new int[1];

        ret[0] = mMockNetworkSelectionMode;
        resultSuccess(result, ret);
    }

    /** 0 for automatic selection and a 1 for manual selection. */
    private int mMockNetworkSelectionMode = 0;

    private final AtomicInteger getNetworkSelectionModeCallCount = new AtomicInteger(0);

    @VisibleForTesting
    public int getGetNetworkSelectionModeCallCount() {
        return getNetworkSelectionModeCallCount.get();
    }

    /**
     * Queries the currently available networks
     *
     * ((AsyncResult)response.obj).result  is a List of NetworkInfo objects
     */
    @Override
    public void getAvailableNetworks(Message result) {
        unimplemented(result);
    }

    /**
     * Starts a network scan
     */
    @Override
    public void startNetworkScan(NetworkScanRequest nsr, Message result) {
        unimplemented(result);
    }

    /**
     * Stops an ongoing network scan
     */
    @Override
    public void stopNetworkScan(Message result) {
        unimplemented(result);
    }

    @Override
    public void getBasebandVersion (Message result) {
        SimulatedCommandsVerifier.getInstance().getBasebandVersion(result);
        resultSuccess(result, "SimulatedCommands");
    }

    /**
     * Simulates an Stk Call Control Alpha message
     * @param alphaString Alpha string to send.
     */
    public void triggerIncomingStkCcAlpha(String alphaString) {
        if (mCatCcAlphaRegistrant != null) {
            mCatCcAlphaRegistrant.notifyResult(alphaString);
        }
    }

    public void sendStkCcAplha(String alphaString) {
        triggerIncomingStkCcAlpha(alphaString);
    }

    /**
     * Simulates an incoming USSD message
     * @param statusCode  Status code string. See <code>setOnUSSD</code>
     * in CommandsInterface.java
     * @param message Message text to send or null if none
     */
    @Override
    public void triggerIncomingUssd(String statusCode, String message) {
        if (mUSSDRegistrant != null) {
            String[] result = {statusCode, message};
            mUSSDRegistrant.notifyResult(result);
        }
    }


    @Override
    public void sendUSSD (String ussdString, Message result) {

        // We simulate this particular sequence
        if (ussdString.equals("#646#")) {
            resultSuccess(result, null);

            // 0 == USSD-Notify
            triggerIncomingUssd("0", "You have NNN minutes remaining.");
        } else {
            resultSuccess(result, null);

            triggerIncomingUssd("0", "All Done");
        }
    }

    // inherited javadoc suffices
    @Override
    public void cancelPendingUssd (Message response) {
        resultSuccess(response, null);
    }

    @Override
    public void setCarrierInfoForImsiEncryption(ImsiEncryptionInfo imsiEncryptionInfo,
                                                Message response) {
        // Just echo back data
        if (response != null) {
            AsyncResult.forMessage(response).result = imsiEncryptionInfo;
            response.sendToTarget();
        }
    }

    //***** SimulatedRadioControl


    /** Start the simulated phone ringing */
    @Override
    public void
    triggerRing(String number) {
        simulatedCallState.triggerRing(number);
        mCallStateRegistrants.notifyRegistrants();
    }

    @Override
    public void
    progressConnectingCallState() {
        simulatedCallState.progressConnectingCallState();
        mCallStateRegistrants.notifyRegistrants();
    }

    /** If a call is DIALING or ALERTING, progress it all the way to ACTIVE */
    @Override
    public void
    progressConnectingToActive() {
        simulatedCallState.progressConnectingToActive();
        mCallStateRegistrants.notifyRegistrants();
    }

    /** automatically progress mobile originated calls to ACTIVE.
     *  default to true
     */
    @Override
    public void
    setAutoProgressConnectingCall(boolean b) {
        simulatedCallState.setAutoProgressConnectingCall(b);
    }

    @Override
    public void
    setNextDialFailImmediately(boolean b) {
        simulatedCallState.setNextDialFailImmediately(b);
    }

    @Override
    public void
    setNextCallFailCause(int gsmCause) {
        mNextCallFailCause = gsmCause;
    }

    @Override
    public void
    triggerHangupForeground() {
        simulatedCallState.triggerHangupForeground();
        mCallStateRegistrants.notifyRegistrants();
    }

    /** hangup holding calls */
    @Override
    public void
    triggerHangupBackground() {
        simulatedCallState.triggerHangupBackground();
        mCallStateRegistrants.notifyRegistrants();
    }

    @Override
    public void triggerSsn(int type, int code) {
        SuppServiceNotification not = new SuppServiceNotification();
        not.notificationType = type;
        not.code = code;
        mSsnRegistrant.notifyRegistrant(new AsyncResult(null, not, null));
    }

    @Override
    public void
    shutdown() {
        setRadioState(TelephonyManager.RADIO_POWER_UNAVAILABLE, false /* forceNotifyRegistrants */);
        Looper looper = mHandlerThread.getLooper();
        if (looper != null) {
            looper.quit();
        }
    }

    /** hangup all */

    @Override
    public void
    triggerHangupAll() {
        simulatedCallState.triggerHangupAll();
        mCallStateRegistrants.notifyRegistrants();
    }

    @Override
    public void
    triggerIncomingSMS(String message) {
        //TODO
    }

    @Override
    public void
    pauseResponses() {
        mPausedResponseCount++;
    }

    @Override
    public void
    resumeResponses() {
        mPausedResponseCount--;

        if (mPausedResponseCount == 0) {
            for (int i = 0, s = mPausedResponses.size(); i < s ; i++) {
                mPausedResponses.get(i).sendToTarget();
            }
            mPausedResponses.clear();
        } else {
            Rlog.e("GSM", "SimulatedCommands.resumeResponses < 0");
        }
    }

    //***** Private Methods

    @UnsupportedAppUsage
    private void unimplemented(Message result) {
        if (result != null) {
            AsyncResult.forMessage(result).exception
                = new RuntimeException("Unimplemented");

            if (mPausedResponseCount > 0) {
                mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    @UnsupportedAppUsage
    protected void resultSuccess(Message result, Object ret) {
        if (result != null) {
            AsyncResult.forMessage(result).result = ret;
            if (mPausedResponseCount > 0) {
                mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    @UnsupportedAppUsage
    private void resultFail(Message result, Object ret, Throwable tr) {
        if (result != null) {
            AsyncResult.forMessage(result, ret, tr);
            if (mPausedResponseCount > 0) {
                mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    // ***** Methods for CDMA support
    @Override
    public void
    getDeviceIdentity(Message response) {
        SimulatedCommandsVerifier.getInstance().getDeviceIdentity(response);
        resultSuccess(response, new String[] {FAKE_IMEI, FAKE_IMEISV, FAKE_ESN, FAKE_MEID});
    }

    @Override
    public void getImei(Message response) {
        SimulatedCommandsVerifier.getInstance().getImei(response);
        ImeiInfo imeiInfo = new ImeiInfo();
        imeiInfo.imei = FAKE_IMEI;
        imeiInfo.svn = FAKE_IMEISV;
        imeiInfo.type = ImeiInfo.ImeiType.SECONDARY;
        resultSuccess(response, imeiInfo);
    }

    @Override
    public void
    getCDMASubscription(Message result) {
        String ret[] = new String[5];
        ret[0] = "123";
        ret[1] = "456";
        ret[2] = "789";
        ret[3] = "234";
        ret[4] = "345";
        resultSuccess(result, ret);
    }

    @Override
    public void
    setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response) {
        unimplemented(response);
    }

    @Override
    public void queryCdmaRoamingPreference(Message response) {
        unimplemented(response);
    }

    @Override
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        unimplemented(response);
    }

    @Override
    public void
    setPhoneType(int phoneType) {
    }

    @Override
    public void getPreferredVoicePrivacy(Message result) {
        unimplemented(result);
    }

    @Override
    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        unimplemented(result);
    }

    /**
     *  Set the TTY mode
     *
     * @param ttyMode is one of the following:
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_OFF}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_FULL}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_HCO}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     * @param response is callback message
     */
    @Override
    public void setTTYMode(int ttyMode, Message response) {
        Rlog.w(LOG_TAG, "Not implemented in SimulatedCommands");
        unimplemented(response);
    }

    /**
     *  Query the TTY mode
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * tty mode:
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_OFF}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_FULL}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_HCO}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     * @param response is callback message
     */
    @Override
    public void queryTTYMode(Message response) {
        unimplemented(response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        unimplemented(response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendCdmaSms(byte[] pdu, Message response){
        SimulatedCommandsVerifier.getInstance().sendCdmaSms(pdu, response);
        resultSuccess(response, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendCdmaSMSExpectMore(byte[] pdu, Message response){
    }

    @Override
    public void setCdmaBroadcastActivation(boolean activate, Message response) {
        SimulatedCommandsVerifier.getInstance().setCdmaBroadcastActivation(activate, response);
        resultSuccess(response, null);
    }

    @Override
    public void getCdmaBroadcastConfig(Message response) {
        unimplemented(response);

    }

    @Override
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        SimulatedCommandsVerifier.getInstance().setCdmaBroadcastConfig(configs, response);
        resultSuccess(response, null);
    }

    public void forceDataDormancy(Message response) {
        unimplemented(response);
    }


    @Override
    public void setGsmBroadcastActivation(boolean activate, Message response) {
        SimulatedCommandsVerifier.getInstance().setGsmBroadcastActivation(activate, response);
        resultSuccess(response, null);
    }


    @Override
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        SimulatedCommandsVerifier.getInstance().setGsmBroadcastConfig(config, response);
        if (mSendSetGsmBroadcastConfigResponse) {
            resultSuccess(response, null);
        }
    }

    @Override
    public void getGsmBroadcastConfig(Message response) {
        unimplemented(response);
    }

    @Override
    public void supplyIccPinForApp(String pin, String aid, Message response) {
        SimulatedCommandsVerifier.getInstance().supplyIccPinForApp(pin, aid, response);
        if (mPinCode != null && mPinCode.equals(pin)) {
            resultSuccess(response, null);
            return;
        }

        Rlog.i(LOG_TAG, "[SimCmd] supplyIccPinForApp: pin failed!");
        CommandException ex = new CommandException(
                CommandException.Error.PASSWORD_INCORRECT);
        resultFail(response, new int[]{
                (--mPin1attemptsRemaining < 0) ? 0 : mPin1attemptsRemaining}, ex);
    }

    @Override
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message response) {
        unimplemented(response);
    }

    @Override
    public void supplyIccPin2ForApp(String pin2, String aid, Message response) {
        unimplemented(response);
    }

    @Override
    public void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message response) {
        unimplemented(response);
    }

    @Override
    public void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message response) {
        SimulatedCommandsVerifier.getInstance().changeIccPinForApp(oldPin, newPin, aidPtr,
                response);
        changeIccPin(oldPin, newPin, response);
    }

    @Override
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr,
            Message response) {
        unimplemented(response);
    }

    @Override
    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response) {
        switch (mAuthenticationMode) {
            case ICC_AUTHENTICATION_MODE_TIMEOUT:
                break;

            case ICC_AUTHENTICATION_MODE_NULL:
                sendMessageResponse(response, null);
                break;

            default:
                if (data == null || data.length() == 0) {
                    sendMessageResponse(response,  null);
                } else {
                    sendMessageResponse(response, new IccIoResult(0, 0, (byte[]) data.getBytes()));
                }
                break;
        }
    }

    /**
     * Helper function to send response msg
     * @param msg Response message to be sent
     * @param ret Return object to be included in the response message
     */
    private void sendMessageResponse(Message msg, Object ret) {
        if (msg != null) {
            AsyncResult.forMessage(msg, ret, null);
            msg.sendToTarget();
        }
    }

    public void setAuthenticationMode(int authenticationMode) {
        mAuthenticationMode = authenticationMode;
    }

    @Override
    public void getVoiceRadioTechnology(Message response) {
        SimulatedCommandsVerifier.getInstance().getVoiceRadioTechnology(response);
        int ret[] = new int[1];
        ret[0] = mVoiceRadioTech;
        resultSuccess(response, ret);
    }

    public void setCellInfoList(List<CellInfo> list) {
        mCellInfoList = list;
    }

    private CellInfoGsm getCellInfoGsm() {
        Parcel p = Parcel.obtain();
        // CellInfo
        p.writeInt(1);
        p.writeInt(1);
        p.writeInt(2);
        p.writeLong(1453510289108L);
        p.writeInt(0);
        // CellIdentity
        p.writeInt(1);
        p.writeString("310");
        p.writeString("260");
        p.writeString("long");
        p.writeString("short");
        // CellIdentityGsm
        p.writeInt(123);
        p.writeInt(456);
        p.writeInt(950);
        p.writeInt(27);
        // CellSignalStrength
        p.writeInt(99);
        p.writeInt(0);
        p.writeInt(3);
        p.setDataPosition(0);

        return CellInfoGsm.CREATOR.createFromParcel(p);
    }

    public synchronized void setCellInfoListBehavior(boolean shouldReturn) {
        mShouldReturnCellInfo = shouldReturn;
    }

    @Override
    public synchronized void getCellInfoList(Message response, WorkSource workSource) {
        if (!mShouldReturnCellInfo) return;

        if (mCellInfoList == null) {
            mCellInfoList = new ArrayList();
            mCellInfoList.add(getCellInfoGsm());
        }

        resultSuccess(response, mCellInfoList);
    }

    @Override
    public int getRilVersion() {
        return 11;
    }

    @Override
    public void setCellInfoListRate(int rateInMillis, Message response, WorkSource workSource) {
        unimplemented(response);
    }

    @Override
    public void setInitialAttachApn(DataProfile dataProfile, Message result) {
        SimulatedCommandsVerifier.getInstance().setInitialAttachApn(dataProfile, result);
        resultSuccess(result, null);
    }

    @Override
    public void setDataProfile(DataProfile[] dps, Message result) {
        SimulatedCommandsVerifier.getInstance().setDataProfile(dps, result);
        resultSuccess(result, null);
    }

    @Override
    public void startHandover(Message result, int callId) {
        SimulatedCommandsVerifier.getInstance().startHandover(result, callId);
        resultSuccess(result, null);
    };

    @Override
    public void cancelHandover(Message result, int callId) {
        SimulatedCommandsVerifier.getInstance().cancelHandover(result, callId);
        resultSuccess(result, null);
    };

    public void setImsRegistrationState(int[] regState) {
        mImsRegState = regState;
    }

    @Override
    public void getImsRegistrationState(Message response) {
        if (mImsRegState == null) {
            mImsRegState = new int[]{1, PhoneConstants.PHONE_TYPE_NONE};
        }

        resultSuccess(response, mImsRegState);
    }

    @Override
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef,
            Message response){
        SimulatedCommandsVerifier.getInstance().sendImsCdmaSms(pdu, retry, messageRef, response);
        resultSuccess(response, new SmsResponse(0 /*messageRef*/, null, SmsResponse.NO_ERROR_CODE));
    }

    @Override
    public void sendImsGsmSms(String smscPDU, String pdu,
            int retry, int messageRef, Message response){
        SimulatedCommandsVerifier.getInstance().sendImsGsmSms(smscPDU, pdu, retry, messageRef,
                response);
        resultSuccess(response, new SmsResponse(0 /*messageRef*/, null, SmsResponse.NO_ERROR_CODE));
    }

    @Override
    public void iccOpenLogicalChannel(String AID, int p2, Message response) {
        SimulatedCommandsVerifier.getInstance().iccOpenLogicalChannel(AID, p2, response);
        Object result = new int[]{mChannelId};
        resultSuccess(response, result);
    }

    @Override
    public void iccCloseLogicalChannel(int channel, boolean isEs10, Message response) {
        unimplemented(response);
    }

    @Override
    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction,
            int p1, int p2, int p3, String data, boolean isEs10Command, Message response) {
        SimulatedCommandsVerifier.getInstance().iccTransmitApduLogicalChannel(channel, cla,
                instruction, p1, p2, p3, data, isEs10Command, response);
        if (mIccIoResultForApduLogicalChannel != null) {
            resultSuccess(response, mIccIoResultForApduLogicalChannel);
        } else {
            resultFail(response, null, new RuntimeException("IccIoResult not set"));
        }
    }

    @Override
    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2,
            int p3, String data, Message response) {
        unimplemented(response);
    }

    @Override
    public void nvReadItem(int itemID, Message response, WorkSource workSource) {
        unimplemented(response);
    }

    @Override
    public void nvWriteItem(int itemID, String itemValue, Message response, WorkSource workSource) {
        unimplemented(response);
    }

    @Override
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        unimplemented(response);
    }

    @Override
    public void nvResetConfig(int resetType, Message response) {
        unimplemented(response);
    }

    @Override
    public void getHardwareConfig(Message result) {
        unimplemented(result);
    }

    @Override
    public void requestShutdown(Message result) {
        setRadioState(TelephonyManager.RADIO_POWER_UNAVAILABLE, false /* forceNotifyRegistrants */);
    }

    @Override
    public void registerForLceInfo(Handler h, int what, Object obj) {
        SimulatedCommandsVerifier.getInstance().registerForLceInfo(h, what, obj);
    }

    @Override
    public void unregisterForLceInfo(Handler h) {
        SimulatedCommandsVerifier.getInstance().unregisterForLceInfo(h);
    }

    @Override
    public void getModemActivityInfo(Message result, WorkSource workSource) {
        unimplemented(result);
    }

    @Override
    public void setAllowedCarriers(CarrierRestrictionRules carrierRestrictionRules,
            Message result, WorkSource workSource) {
        unimplemented(result);
    }

    @Override
    public void getAllowedCarriers(Message result, WorkSource workSource) {
        unimplemented(result);
    }

    @Override
    public void getRadioCapability(Message result) {
        SimulatedCommandsVerifier.getInstance().getRadioCapability(result);
        resultSuccess(result, new RadioCapability(0, 0, 0, 0xFFFF, null, 0));
    }
    public void notifySmsStatus(Object result) {
        if (mSmsStatusRegistrant != null) {
            mSmsStatusRegistrant.notifyRegistrant(new AsyncResult(null, result, null));
        }
    }

    public void notifyGsmBroadcastSms(Object result) {
        if (mGsmBroadcastSmsRegistrant != null) {
            mGsmBroadcastSmsRegistrant.notifyRegistrant(new AsyncResult(null, result, null));
        }
    }

    public void notifyIccSmsFull() {
        if (mIccSmsFullRegistrant != null) {
            mIccSmsFullRegistrant.notifyRegistrant();
        }
    }

    public void notifyEmergencyCallbackMode() {
        if (mEmergencyCallbackModeRegistrant != null) {
            mEmergencyCallbackModeRegistrant.notifyRegistrant();
        }
    }

    @Override
    public void setEmergencyCallbackMode(Handler h, int what, Object obj) {
        SimulatedCommandsVerifier.getInstance().setEmergencyCallbackMode(h, what, obj);
        super.setEmergencyCallbackMode(h, what, obj);
    }

    public void notifyExitEmergencyCallbackMode() {
        if (mExitEmergencyCallbackModeRegistrants != null) {
            mExitEmergencyCallbackModeRegistrants.notifyRegistrants(
                    new AsyncResult (null, null, null));
        }
    }

    public void notifyImsNetworkStateChanged() {
        if(mImsNetworkStateChangedRegistrants != null) {
            mImsNetworkStateChangedRegistrants.notifyRegistrants();
        }
    }

    public void notifyModemReset() {
        if (mModemResetRegistrants != null) {
            mModemResetRegistrants.notifyRegistrants(new AsyncResult(null, "Test", null));
        }
    }

    @Override
    public void registerForExitEmergencyCallbackMode(Handler h, int what, Object obj) {
        SimulatedCommandsVerifier.getInstance().registerForExitEmergencyCallbackMode(h, what, obj);
        super.registerForExitEmergencyCallbackMode(h, what, obj);
    }

    @Override
    public void registerForSrvccStateChanged(Handler h, int what, Object obj) {
        SimulatedCommandsVerifier.getInstance().registerForSrvccStateChanged(h, what, obj);
        super.registerForSrvccStateChanged(h, what, obj);
    }

    public void notifyRadioOn() {
        mOnRegistrants.notifyRegistrants();
    }

    @VisibleForTesting
    public void notifyNetworkStateChanged() {
        mNetworkStateRegistrants.notifyRegistrants();
    }

    @VisibleForTesting
    public void notifyOtaProvisionStatusChanged() {
        if (mOtaProvisionRegistrants != null) {
            int ret[] = new int[1];
            ret[0] = Phone.CDMA_OTA_PROVISION_STATUS_COMMITTED;
            mOtaProvisionRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
        }
    }

    public void notifySignalStrength() {
        if (mSignalStrength == null) {
            mSignalStrength = new SignalStrength(
                    new CellSignalStrengthCdma(),
                    new CellSignalStrengthGsm(20, 0, CellInfo.UNAVAILABLE),
                    new CellSignalStrengthWcdma(),
                    new CellSignalStrengthTdscdma(),
                    new CellSignalStrengthLte(),
                    new CellSignalStrengthNr());
        }

        if (mSignalStrengthRegistrant != null) {
            mSignalStrengthRegistrant.notifyRegistrant(
                    new AsyncResult (null, mSignalStrength, null));
        }
    }

    public void setIccCardStatus(IccCardStatus iccCardStatus){
        mIccCardStatus = iccCardStatus;
    }

    public void setIccIoResultForApduLogicalChannel(IccIoResult iccIoResult) {
        mIccIoResultForApduLogicalChannel = iccIoResult;
    }

    public void setOpenChannelId(int channelId) {
        mChannelId = channelId;
    }

    public void setPin1RemainingAttempt(int pin1attemptsRemaining) {
        mPin1attemptsRemaining = pin1attemptsRemaining;
    }

    private AtomicBoolean mAllowed = new AtomicBoolean(false);

    @Override
    public void setDataAllowed(boolean allowed, Message result) {
        log("setDataAllowed = " + allowed);
        mAllowed.set(allowed);
        resultSuccess(result, null);
    }

    @VisibleForTesting
    public boolean isDataAllowed() {
        return mAllowed.get();
    }

    @Override
    public void registerForPcoData(Handler h, int what, Object obj) {
        SimulatedCommandsVerifier.getInstance().registerForPcoData(h, what, obj);
        mPcoDataRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForPcoData(Handler h) {
        SimulatedCommandsVerifier.getInstance().unregisterForPcoData(h);
        mPcoDataRegistrants.remove(h);
    }

    @Override
    public void registerForNotAvailable(Handler h, int what, Object obj) {
        SimulatedCommandsVerifier.getInstance().registerForNotAvailable(h, what, obj);
        super.registerForNotAvailable(h, what, obj);
    }

    @Override
    public void unregisterForNotAvailable(Handler h) {
        SimulatedCommandsVerifier.getInstance().unregisterForNotAvailable(h);
        super.unregisterForNotAvailable(h);
    }

    @Override
    public void registerForModemReset(Handler h, int what, Object obj) {
        SimulatedCommandsVerifier.getInstance().registerForModemReset(h, what, obj);
        super.registerForModemReset(h, what, obj);
    }

    @Override
    public void sendDeviceState(int stateType, boolean state, Message result) {
        SimulatedCommandsVerifier.getInstance().sendDeviceState(stateType, state, result);
        resultSuccess(result, null);
    }

    @Override
    public void setUnsolResponseFilter(int filter, Message result) {
        SimulatedCommandsVerifier.getInstance().setUnsolResponseFilter(filter, result);
        resultSuccess(result, null);
    }

    @Override
    public void setSignalStrengthReportingCriteria(List<SignalThresholdInfo> signalThresholdInfos,
            Message result) {
        SimulatedCommandsVerifier.getInstance().setSignalStrengthReportingCriteria(
                signalThresholdInfos, result);
        resultSuccess(result, null);
    }

    @Override
    public void setLinkCapacityReportingCriteria(int hysteresisMs, int hysteresisDlKbps,
            int hysteresisUlKbps, int[] thresholdsDlKbps, int[] thresholdsUlKbps, int ran,
            Message result) {
    }

    @Override
    public void setSimCardPower(int state, Message result, WorkSource workSource) {
    }

    @VisibleForTesting
    public void triggerRestrictedStateChanged(int restrictedState) {
        if (mRestrictedStateRegistrant != null) {
            mRestrictedStateRegistrant.notifyRegistrant(
                    new AsyncResult(null, restrictedState, null));
        }
    }

    @Override
    public void setOnRestrictedStateChanged(Handler h, int what, Object obj) {
        super.setOnRestrictedStateChanged(h, what, obj);
        SimulatedCommandsVerifier.getInstance().setOnRestrictedStateChanged(h, what, obj);
    }

    public void setRadioPowerFailResponse(boolean fail) {
        mIsRadioPowerFailResponse = fail;
    }

    @Override
    public void registerForIccRefresh(Handler h, int what, Object obj) {
        super.registerForIccRefresh(h, what, obj);
        SimulatedCommandsVerifier.getInstance().registerForIccRefresh(h, what, obj);
    }

    @Override
    public void unregisterForIccRefresh(Handler h) {
        super.unregisterForIccRefresh(h);
        SimulatedCommandsVerifier.getInstance().unregisterForIccRefresh(h);
    }

    @Override
    public void registerForNattKeepaliveStatus(Handler h, int what, Object obj) {
        SimulatedCommandsVerifier.getInstance().registerForNattKeepaliveStatus(h, what, obj);
    }

    @Override
    public void unregisterForNattKeepaliveStatus(Handler h) {
        SimulatedCommandsVerifier.getInstance().unregisterForNattKeepaliveStatus(h);
    }

    @Override
    public void startNattKeepalive(
            int contextId, KeepalivePacketData packetData, int intervalMillis, Message result) {
        SimulatedCommandsVerifier.getInstance().startNattKeepalive(
                contextId, packetData, intervalMillis, result);
    }

    @Override
    public void stopNattKeepalive(int sessionHandle, Message result) {
        SimulatedCommandsVerifier.getInstance().stopNattKeepalive(sessionHandle, result);
    }

    public Handler getHandler() {
        return mHandlerThread.getThreadHandler();
    }

    @Override
    public void getBarringInfo(Message result) {
        SimulatedCommandsVerifier.getInstance().getBarringInfo(result);
        resultSuccess(result, null);
    }

    @Override
    public void allocatePduSessionId(Message message) {
        SimulatedCommandsVerifier.getInstance().allocatePduSessionId(message);
        resultSuccess(message, 1);
    }

    @Override
    public void releasePduSessionId(Message message, int pduSessionId) {
        SimulatedCommandsVerifier.getInstance().releasePduSessionId(message, pduSessionId);
        resultSuccess(message, null);
    }

    @Override
    public void getSlicingConfig(Message result) {
        SimulatedCommandsVerifier.getInstance().getSlicingConfig(result);
        resultSuccess(result, null);
    }

    @VisibleForTesting
    public void setDataRegStateResult(Object regStateResult) {
        mDataRegStateResult = regStateResult;
    }

    @VisibleForTesting
    public void setVoiceRegStateResult(Object regStateResult) {
        mVoiceRegStateResult = regStateResult;
    }

    @Override
    public void getSimPhonebookRecords(Message result) {
        resultSuccess(result, null);

        // send a fake result
        List<SimPhonebookRecord> phonebookRecordInfoGroup = new ArrayList<SimPhonebookRecord>();
        mSimPhonebookRecordsReceivedRegistrants.notifyRegistrants(
                new AsyncResult(null,
                new ReceivedPhonebookRecords(4, phonebookRecordInfoGroup), null));
    }

    @Override
    public void getSimPhonebookCapacity(Message result) {
        resultSuccess(result, new AdnCapacity(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
    }

    @Override
    public void updateSimPhonebookRecord(SimPhonebookRecord phonebookRecord, Message result) {
        int recordId = phonebookRecord.getRecordId();
        // Based on design, the record ID starts from 1.
        // So if the record ID passed from upper layer is 0, it indicates to insert one new record
        // without record ID specific.
        if (recordId == 0) {
            recordId = 1; // hack code for unit test
        }
        resultSuccess(result, new int[]{recordId});
        notifySimPhonebookChanged();
    }

    @VisibleForTesting
    public void notifySimPhonebookChanged() {
        mSimPhonebookChangedRegistrants.notifyRegistrants();
    }

    public void triggerPcoData(int cid, String bearerProto, int pcoId, byte[] contents) {
        PcoData response = new PcoData(cid, bearerProto, pcoId, contents);
        mPcoDataRegistrants.notifyRegistrants(new AsyncResult(null, response, null));
    }

    @Override
    public void setSrvccCallInfo(SrvccConnection[] srvccConnections, Message result) {
        mSrvccConnections = srvccConnections;
    }

    public SrvccConnection[] getSrvccConnections() {
        return mSrvccConnections;
    }

    @Override
    public void updateImsRegistrationInfo(int regState,
            int imsRadioTech, int suggestedAction, int capabilities, Message result) {
        mImsRegistrationInfo[0] = regState;
        mImsRegistrationInfo[1] = imsRadioTech;
        mImsRegistrationInfo[2] = suggestedAction;
        mImsRegistrationInfo[3] = capabilities;
    }

    public int[] getImsRegistrationInfo() {
        return mImsRegistrationInfo;
    }

    @Override
    public void setN1ModeEnabled(boolean enable, Message result) {
        mN1ModeEnabled = enable;
    }

    public boolean isN1ModeEnabled() {
        return mN1ModeEnabled;
    }

    @Override
    public void isVoNrEnabled(Message message, WorkSource workSource) {
        resultSuccess(message, (Object) mVonrEnabled);
    }

    public void setVonrEnabled(boolean vonrEnable) {
        mVonrEnabled = vonrEnable;
    }
}

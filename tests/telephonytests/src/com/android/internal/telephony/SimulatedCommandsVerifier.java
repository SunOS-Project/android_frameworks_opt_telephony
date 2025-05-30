/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.net.KeepalivePacketData;
import android.net.LinkProperties;
import android.os.Handler;
import android.os.Message;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.NetworkScanRequest;
import android.telephony.SignalThresholdInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.DataProfile;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;
import android.telephony.emergency.EmergencyNumber;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.RadioCapability;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.uicc.SimPhonebookRecord;

import java.util.List;

public class SimulatedCommandsVerifier implements CommandsInterface {
    private static SimulatedCommandsVerifier sInstance;

    private SimulatedCommandsVerifier() {

    }

    @UnsupportedAppUsage
    public static SimulatedCommandsVerifier getInstance() {
        if (sInstance == null) {
            sInstance = new SimulatedCommandsVerifier();
        }
        return sInstance;
    }

    @Override
    public int getRadioState() {
        return TelephonyManager.RADIO_POWER_UNAVAILABLE;
    }

    @Override
    public void getImsRegistrationState(Message result) {

    }

    @Override
    public void registerForRadioStateChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForRadioStateChanged(Handler h) {

    }

    @Override
    public void registerForVoiceRadioTechChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForVoiceRadioTechChanged(Handler h) {

    }

    @Override
    public void registerForImsNetworkStateChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForImsNetworkStateChanged(Handler h) {

    }

    @Override
    public void registerForOn(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForOn(Handler h) {

    }

    @Override
    public void registerForAvailable(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForAvailable(Handler h) {

    }

    @Override
    public void registerForNotAvailable(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForNotAvailable(Handler h) {

    }

    @Override
    public void registerForOffOrNotAvailable(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForOffOrNotAvailable(Handler h) {

    }

    @Override
    public void registerForIccStatusChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForIccStatusChanged(Handler h) {

    }

    @Override
    public void registerForIccSlotStatusChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForIccSlotStatusChanged(Handler h) {

    }

    @Override
    public void registerForCallStateChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForCallStateChanged(Handler h) {

    }

    @Override
    public void registerForNetworkStateChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForNetworkStateChanged(Handler h) {

    }

    @Override
    public void registerForDataCallListChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForDataCallListChanged(Handler h) {

    }

    @Override
    public void registerForApnUnthrottled(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForApnUnthrottled(Handler h) {

    }

    @Override
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForInCallVoicePrivacyOn(Handler h) {

    }

    @Override
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForInCallVoicePrivacyOff(Handler h) {

    }

    @Override
    public void registerForSrvccStateChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForSrvccStateChanged(Handler h) {

    }

    @Override
    public void registerForSubscriptionStatusChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForSubscriptionStatusChanged(Handler h) {

    }

    @Override
    public void registerForHardwareConfigChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForHardwareConfigChanged(Handler h) {

    }

    @Override
    public void setOnNewGsmSms(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnNewGsmSms(Handler h) {

    }

    @Override
    public void setOnNewCdmaSms(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnNewCdmaSms(Handler h) {

    }

    @Override
    public void setOnNewGsmBroadcastSms(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnNewGsmBroadcastSms(Handler h) {

    }

    @Override
    public void setOnSmsOnSim(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnSmsOnSim(Handler h) {

    }

    @Override
    public void setOnSmsStatus(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnSmsStatus(Handler h) {

    }

    @Override
    public void setOnNITZTime(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnNITZTime(Handler h) {

    }

    @Override
    public void setOnUSSD(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnUSSD(Handler h) {

    }

    @Override
    public void setOnSignalStrengthUpdate(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnSignalStrengthUpdate(Handler h) {

    }

    @Override
    public void setOnIccSmsFull(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnIccSmsFull(Handler h) {

    }

    @Override
    public void registerForIccRefresh(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForIccRefresh(Handler h) {

    }

    @Override
    public void setOnIccRefresh(Handler h, int what, Object obj) {

    }

    @Override
    public void unsetOnIccRefresh(Handler h) {

    }

    @Override
    public void setOnCallRing(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnCallRing(Handler h) {

    }

    @Override
    public void setOnRestrictedStateChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnRestrictedStateChanged(Handler h) {

    }

    @Override
    public void setOnSuppServiceNotification(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnSuppServiceNotification(Handler h) {

    }

    @Override
    public void setOnCatSessionEnd(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnCatSessionEnd(Handler h) {

    }

    @Override
    public void setOnCatProactiveCmd(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnCatProactiveCmd(Handler h) {

    }

    @Override
    public void setOnCatEvent(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnCatEvent(Handler h) {

    }

    @Override
    public void setOnCatCallSetUp(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnCatCallSetUp(Handler h) {

    }

    @Override
    public void setSuppServiceNotifications(boolean enable, Message result) {

    }

    @Override
    public void setOnCatCcAlphaNotify(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnCatCcAlphaNotify(Handler h) {

    }

    @Override
    public void setOnSs(Handler h, int what, Object obj) {

    }

    @Override
    public void unSetOnSs(Handler h) {

    }

    @Override
    public void registerForDisplayInfo(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForDisplayInfo(Handler h) {

    }

    @Override
    public void registerForCallWaitingInfo(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForCallWaitingInfo(Handler h) {

    }

    @Override
    public void registerForSignalInfo(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForSignalInfo(Handler h) {

    }

    @Override
    public void registerForNumberInfo(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForNumberInfo(Handler h) {

    }

    @Override
    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForRedirectedNumberInfo(Handler h) {

    }

    @Override
    public void registerForLineControlInfo(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForLineControlInfo(Handler h) {

    }

    @Override
    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForT53ClirInfo(Handler h) {

    }

    @Override
    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForT53AudioControlInfo(Handler h) {

    }

    @Override
    public void setEmergencyCallbackMode(Handler h, int what, Object obj) {

    }

    @Override
    public void registerForCdmaOtaProvision(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForCdmaOtaProvision(Handler h) {

    }

    @Override
    public void registerForRingbackTone(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForRingbackTone(Handler h) {

    }

    @Override
    public void registerForResendIncallMute(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForResendIncallMute(Handler h) {

    }

    @Override
    public void registerForCdmaSubscriptionChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForCdmaSubscriptionChanged(Handler h) {

    }

    @Override
    public void registerForCdmaPrlChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForCdmaPrlChanged(Handler h) {

    }

    @Override
    public void registerForExitEmergencyCallbackMode(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForExitEmergencyCallbackMode(Handler h) {

    }

    @Override
    public void registerForRilConnected(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForRilConnected(Handler h) {

    }

    @Override
    public void supplyIccPin(String pin, Message result) {

    }

    @Override
    public void supplyIccPinForApp(String pin, String aid, Message result) {

    }

    @Override
    public void supplyIccPuk(String puk, String newPin, Message result) {

    }

    @Override
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message result) {

    }

    @Override
    public void supplyIccPin2(String pin2, Message result) {

    }

    @Override
    public void supplyIccPin2ForApp(String pin2, String aid, Message result) {

    }

    @Override
    public void supplyIccPuk2(String puk2, String newPin2, Message result) {

    }

    @Override
    public void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message result) {

    }

    @Override
    public void changeIccPin(String oldPin, String newPin, Message result) {

    }

    @Override
    public void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message result) {

    }

    @Override
    public void changeIccPin2(String oldPin2, String newPin2, Message result) {

    }

    @Override
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr, Message result) {

    }

    @Override
    public void changeBarringPassword(String facility, String oldPwd, String newPwd,
                                      Message result) {

    }

    @Override
    public void supplyNetworkDepersonalization(String netpin, Message result) {

    }

    @Override
    public void supplySimDepersonalization(PersoSubState persoType,
            String controlKey, Message result) {

    }

    @Override
    public void getCurrentCalls(Message result) {

    }

    @Override
    public void getDataCallList(Message result) {

    }

    @Override
    public void dial(String address, boolean isEmergencyCall, EmergencyNumber emergencyNumberInfo,
                     boolean hasKnownUserIntentEmergency, int clirMode, Message result) {
    }

    @Override
    public void dial(String address, boolean isEmergencyCall, EmergencyNumber emergencyNumberInfo,
                     boolean hasKnownUserIntentEmergency, int clirMode, UUSInfo uusInfo,
                     Message result) {
    }

    @Override
    public void getIMSI(Message result) {

    }

    @Override
    public void getIMSIForApp(String aid, Message result) {

    }

    @Override
    public void hangupConnection(int gsmIndex, Message result) {

    }

    @Override
    public void hangupWaitingOrBackground(Message result) {

    }

    @Override
    public void hangupForegroundResumeBackground(Message result) {

    }

    @Override
    public void switchWaitingOrHoldingAndActive(Message result) {

    }

    @Override
    public void conference(Message result) {

    }

    @Override
    public void setPreferredVoicePrivacy(boolean enable, Message result) {

    }

    @Override
    public void getPreferredVoicePrivacy(Message result) {

    }

    @Override
    public void separateConnection(int gsmIndex, Message result) {

    }

    @Override
    public void acceptCall(Message result) {

    }

    @Override
    public void rejectCall(Message result) {

    }

    @Override
    public void explicitCallTransfer(Message result) {

    }

    @Override
    public void getLastCallFailCause(Message result) {

    }

    @Override
    public void setMute(boolean enableMute, Message response) {

    }

    @Override
    public void getMute(Message response) {

    }

    @Override
    public void getSignalStrength(Message response) {

    }

    @Override
    public void getVoiceRegistrationState(Message response) {

    }

    @Override
    public void getDataRegistrationState(Message response) {

    }

    @Override
    public void getOperator(Message response) {

    }

    @Override
    public void sendDtmf(char c, Message result) {

    }

    @Override
    public void startDtmf(char c, Message result) {

    }

    @Override
    public void stopDtmf(Message result) {

    }

    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {

    }

    @Override
    public void sendSMS(String smscPDU, String pdu, Message response) {

    }

    @Override
    public void sendSMSExpectMore(String smscPDU, String pdu, Message response) {

    }

    @Override
    public void sendCdmaSms(byte[] pdu, Message response) {

    }

    @Override
    public void sendCdmaSMSExpectMore(byte[] pdu, Message response) {

    }

    @Override
    public void sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef,
                              Message response) {

    }

    @Override
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message response) {

    }

    @Override
    public void deleteSmsOnSim(int index, Message response) {

    }

    @Override
    public void deleteSmsOnRuim(int index, Message response) {

    }

    @Override
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {

    }

    @Override
    public void writeSmsToRuim(int status, byte[] pdu, Message response) {

    }

    @Override
    public void setRadioPower(boolean on, Message response) {

    }

    @Override
    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message response) {

    }

    @Override
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message response) {

    }

    @Override
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message response) {

    }

    @Override
    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data,
                      String pin2, Message response) {

    }

    @Override
    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3,
                            String data, String pin2, String aid, Message response) {

    }

    @Override
    public void queryCLIP(Message response) {

    }

    @Override
    public void getCLIR(Message response) {

    }

    @Override
    public void setCLIR(int clirMode, Message response) {

    }

    @Override
    public void queryCallWaiting(int serviceClass, Message response) {

    }

    @Override
    public void setCallWaiting(boolean enable, int serviceClass, Message response) {

    }

    @UnsupportedAppUsage
    @Override
    public void setCallForward(int action, int cfReason, int serviceClass, String number,
                               int timeSeconds, Message response) {

    }

    @Override
    public void queryCallForwardStatus(int cfReason, int serviceClass, String number,
                                       Message response) {

    }

    @Override
    public void setNetworkSelectionModeAutomatic(Message response) {

    }

    @Override
    public void setNetworkSelectionModeManual(String operatorNumeric, int ran, Message response) {

    }

    @Override
    public void setNetworkSelectionModeManual(OperatorInfo network, Message response) {

    }

    @Override
    public void getNetworkSelectionMode(Message response) {

    }

    @Override
    public void getAvailableNetworks(Message response) {

    }

    @Override
    public void startNetworkScan(NetworkScanRequest nsr, Message response) {

    }

    @Override
    public void stopNetworkScan(Message response) {

    }

    @Override
    public void getBasebandVersion(Message response) {

    }

    @Override
    public void queryFacilityLock(String facility, String password, int serviceClass,
                                  Message response) {

    }

    @Override
    public void queryFacilityLockForApp(String facility, String password, int serviceClass,
                                        String appId, Message response) {

    }

    @Override
    public void setFacilityLock(String facility, boolean lockState, String password,
                                int serviceClass, Message response) {

    }

    @Override
    public void setFacilityLockForApp(String facility, boolean lockState, String password,
                                      int serviceClass, String appId, Message response) {

    }

    @Override
    public void sendUSSD(String ussdString, Message response) {

    }

    @Override
    public void cancelPendingUssd(Message response) {

    }

    @Override
    public void setBandMode(int bandMode, Message response) {

    }

    @Override
    public void queryAvailableBandMode(Message response) {

    }

    @Override
    public void setPreferredNetworkType(int networkType, Message response) {

    }

    @Override
    public void getPreferredNetworkType(Message response) {

    }

    @Override
    public void setAllowedNetworkTypesBitmap(
            @TelephonyManager.NetworkTypeBitMask int networkTypeBitmask, Message response) {

    }

    @Override
    public void getAllowedNetworkTypesBitmap(Message response) {

    }

    @Override
    public void setLocationUpdates(boolean enable, Message response) {

    }

    @Override
    public void getSmscAddress(Message result) {

    }

    @Override
    public void setSmscAddress(String address, Message result) {

    }

    @Override
    public void reportSmsMemoryStatus(boolean available, Message result) {

    }

    @Override
    public void reportStkServiceIsRunning(Message result) {

    }

    @Override
    public void sendTerminalResponse(String contents, Message response) {

    }

    @Override
    public void sendEnvelope(String contents, Message response) {

    }

    @Override
    public void sendEnvelopeWithStatus(String contents, Message response) {

    }

    @Override
    public void handleCallSetupRequestFromSim(boolean accept, Message response) {

    }

    @Override
    public void setGsmBroadcastActivation(boolean activate, Message result) {

    }

    @Override
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {

    }

    @Override
    public void getGsmBroadcastConfig(Message response) {

    }

    @Override
    public void getDeviceIdentity(Message response) {

    }

    @Override
    public void getImei(Message response) {

    }

    @Override
    public void getCDMASubscription(Message response) {

    }

    @Override
    public void sendCDMAFeatureCode(String featureCode, Message response) {

    }

    @Override
    public void setPhoneType(int phoneType) {

    }

    @Override
    public void queryCdmaRoamingPreference(Message response) {

    }

    @Override
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {

    }

    @Override
    public void setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response) {

    }

    @Override
    public void getCdmaSubscriptionSource(Message response) {

    }

    @Override
    public void setTTYMode(int ttyMode, Message response) {

    }

    @Override
    public void queryTTYMode(Message response) {

    }

    @Override
    public void setupDataCall(int accessNetworkType, DataProfile dataProfile, boolean allowRoaming,
            int reason, LinkProperties linkProperties, int pduSessionId, NetworkSliceInfo sliceInfo,
            TrafficDescriptor trafficDescriptor, boolean matchAllRuleAllowed, Message result) {
    }

    @Override
    public void deactivateDataCall(int cid, int reason, Message result) {

    }

    @Override
    public void setCdmaBroadcastActivation(boolean activate, Message result) {

    }

    @Override
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {

    }

    @Override
    public void getCdmaBroadcastConfig(Message result) {

    }

    @Override
    public void exitEmergencyCallbackMode(Message response) {

    }

    @Override
    public void getIccCardStatus(Message result) {

    }

    @Override
    public void requestIccSimAuthentication(int authContext, String data, String aid,
                                            Message response) {

    }

    @Override
    public void getVoiceRadioTechnology(Message result) {

    }

    @Override
    public void registerForCellInfoList(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForCellInfoList(Handler h) {

    }

    @Override
    public void registerForPhysicalChannelConfiguration(Handler h, int what, Object obj) {
    }

    @Override
    public void unregisterForPhysicalChannelConfiguration(Handler h) {
    }

    @Override
    public void setInitialAttachApn(DataProfile dataProfile, Message result) {

    }

    @Override
    public void setDataProfile(DataProfile[] dps, Message result) {

    }

    @Override
    public void testingEmergencyCall() {

    }

    @Override
    public void iccOpenLogicalChannel(String aid, int p2, Message response) {

    }

    @Override
    public void iccCloseLogicalChannel(int channel, boolean isEs10, Message response) {

    }

    @Override
    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction, int p1,
                                              int p2, int p3, String data,
                                              boolean isEs10Command, Message response) {

    }

    @Override
    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2, int p3,
                                            String data, Message response) {

    }

    @Override
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {

    }

    @Override
    public void nvResetConfig(int resetType, Message response) {

    }

    @Override
    public void getHardwareConfig(Message result) {

    }

    @Override
    public int getRilVersion() {
        return 0;
    }

    @Override
    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus,
                                    Message result) {

    }

    @Override
    public void setDataAllowed(boolean allowed, Message result) {

    }

    @Override
    public void requestShutdown(Message result) {

    }

    @Override
    public void setRadioCapability(RadioCapability rc, Message result) {

    }

    @Override
    public void getRadioCapability(Message result) {

    }

    @Override
    public void registerForRadioCapabilityChanged(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForRadioCapabilityChanged(Handler h) {

    }

    @Override
    public void registerForLceInfo(Handler h, int what, Object obj) {

    }

    @Override
    public void unregisterForLceInfo(Handler h) {

    }

    @Override
    public void setCarrierInfoForImsiEncryption(ImsiEncryptionInfo imsiEncryptionInfo,
                                                Message result) {

    }

    @Override
    public void registerForPcoData(Handler h, int what, Object obj) {
    }

    @Override
    public void unregisterForPcoData(Handler h) {
    }

    @Override
    public void registerForModemReset(Handler h, int what, Object obj) {
    }

    @Override
    public void unregisterForModemReset(Handler h) {
    }

    @Override
    public void sendDeviceState(int stateType, boolean state, Message result) {
    }

    @Override
    public void setUnsolResponseFilter(int filter, Message result){
    }

    @Override
    public void setSignalStrengthReportingCriteria(List<SignalThresholdInfo> signalThresholdInfos,
            Message result) {
    }

    @Override
    public void setLinkCapacityReportingCriteria(int hysteresisMs, int hysteresisDlKbps,
            int hysteresisUlKbps, int[] thresholdsDlKbps, int[] thresholdsUlKbps, int ran,
            Message result) {
    }

    @Override
    public void registerForCarrierInfoForImsiEncryption(Handler h, int what, Object obj) {
    }

    @Override
    public void registerForNetworkScanResult(Handler h, int what, Object obj) {
    }

    @Override
    public void unregisterForNetworkScanResult(Handler h) {
    }

    @Override
    public void unregisterForCarrierInfoForImsiEncryption(Handler h) {
    }

    @Override
    public void registerForNattKeepaliveStatus(Handler h, int what, Object obj) {
    }

    @Override
    public void unregisterForNattKeepaliveStatus(Handler h) {
    }

    @Override
    public void registerForEmergencyNumberList(Handler h, int what, Object obj) {
    }

    @Override
    public void unregisterForEmergencyNumberList(Handler h) {
    }

    @Override
    public void startNattKeepalive(
            int contextId, KeepalivePacketData packetData, int intervalMillis, Message result) {
    }

    @Override
    public void stopNattKeepalive(int sessionHandle, Message result)  {
    }

    @Override
    public void registerUiccApplicationEnablementChanged(Handler h, int what, Object obj) {}

    @Override
    public void unregisterUiccApplicationEnablementChanged(Handler h) {}

    @Override
    public void getBarringInfo(Message result) {
    }

    @Override
    public void allocatePduSessionId(Message result) {
    }

    @Override
    public void releasePduSessionId(Message result, int pduSessionId) {
    }

    @Override
    public void getSlicingConfig(Message result) {
    }

    @Override
    public void getSimPhonebookRecords(Message result){
    }

    @Override
    public void getSimPhonebookCapacity(Message result){
    }

    @Override
    public void updateSimPhonebookRecord(SimPhonebookRecord phonebookRecord, Message result){
    }

    @Override
    public void registerForSimPhonebookChanged(Handler h, int what, Object obj){
    }

    @Override
    public void unregisterForSimPhonebookChanged(Handler h){
    }

    @Override
    public void registerForSimPhonebookRecordsReceived(Handler h, int what, Object obj){
    }

    @Override
    public void unregisterForSimPhonebookRecordsReceived(Handler h){
    }

    @Override
    public void registerForSlicingConfigChanged(Handler h, int what, Object obj) {
    }

    @Override
    public void unregisterForSlicingConfigChanged(Handler h) {
    }

    @Override
    public void startHandover(Message result, int callId) {
    }

    @Override
    public void cancelHandover(Message result, int callId) {
    }

    @Override
    public void getEnhancedRadioCapability(Message result) {
    }

    /**
     * Register to listen for the changes in the primary IMEI with respect to the sim slot.
     */
    @Override
    public void registerForImeiMappingChanged(Handler h, int what, Object obj) {

    }
}

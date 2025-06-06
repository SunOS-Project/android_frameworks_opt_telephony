/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.internal.telephony.imsphone;

import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.telephony.CarrierConfigManager.USSD_OVER_CS_ONLY;
import static android.telephony.CarrierConfigManager.USSD_OVER_CS_PREFERRED;
import static android.telephony.CarrierConfigManager.USSD_OVER_IMS_ONLY;
import static android.telephony.CarrierConfigManager.USSD_OVER_IMS_PREFERRED;
import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_NONE;
import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_TRIGGER_CLEAR_RAT_BLOCKS;
import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK;
import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK_WITH_TIMEOUT;
import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_TRIGGER_RAT_BLOCK;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_3G;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NR;

import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.IMS_MMTEL_CAPABILITY_SMS;
import static com.android.internal.telephony.CommandsInterface.IMS_MMTEL_CAPABILITY_VIDEO;
import static com.android.internal.telephony.CommandsInterface.IMS_MMTEL_CAPABILITY_VOICE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyChar;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.sysprop.TelephonyProperties;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.ims.stub.ImsUtImplBase;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsEcbmStateListener;
import com.android.ims.ImsUtInterface;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.EcbmHandler;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.domainselection.DomainSelectionResolver;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.imsphone.ImsPhone.SS;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ImsPhoneTest extends TelephonyTest {
    // Mocked classes
    private ImsPhoneCall mForegroundCall;
    private ImsPhoneCall mBackgroundCall;
    private ImsPhoneCall mRingingCall;
    private Handler mTestHandler;
    Connection mConnection;
    ImsUtInterface mImsUtInterface;
    private FeatureFlags mFeatureFlags;
    EcbmHandler mEcbmHandler;

    private final Executor mExecutor = Runnable::run;

    private ImsPhone mImsPhoneUT;
    private PersistableBundle mBundle;
    private boolean mDoesRilSendMultipleCallRing;
    private static final int EVENT_SUPP_SERVICE_NOTIFICATION = 1;
    private static final int EVENT_SUPP_SERVICE_FAILED = 2;
    private static final int EVENT_INCOMING_RING = 3;
    private static final int EVENT_EMERGENCY_CALLBACK_MODE_EXIT = 4;
    private static final int EVENT_CALL_RING_CONTINUE = 15;

    private boolean mIsPhoneUtInEcm = false;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mForegroundCall = mock(ImsPhoneCall.class);
        mBackgroundCall = mock(ImsPhoneCall.class);
        mRingingCall = mock(ImsPhoneCall.class);
        mTestHandler = mock(Handler.class);
        mConnection = mock(Connection.class);
        mImsUtInterface = mock(ImsUtInterface.class);
        mFeatureFlags = mock(FeatureFlags.class);

        mImsCT.mForegroundCall = mForegroundCall;
        mImsCT.mBackgroundCall = mBackgroundCall;
        mImsCT.mRingingCall = mRingingCall;
        doReturn(Call.State.IDLE).when(mForegroundCall).getState();
        doReturn(Call.State.IDLE).when(mBackgroundCall).getState();
        doReturn(Call.State.IDLE).when(mRingingCall).getState();
        doReturn(mExecutor).when(mContext).getMainExecutor();

        doReturn(true).when(mTelephonyManager).isVoiceCapable();

        mImsPhoneUT = new ImsPhone(mContext, mNotifier, mPhone, (c, p) -> mImsManager, true,
                mFeatureFlags);

        mDoesRilSendMultipleCallRing = TelephonyProperties.ril_sends_multiple_call_ring()
                .orElse(true);
        replaceInstance(Handler.class, "mLooper", mTestHandler, mImsPhoneUT.getLooper());
        replaceInstance(Phone.class, "mLooper", mPhone, mImsPhoneUT.getLooper());
        mImsPhoneUT.registerForSuppServiceNotification(mTestHandler,
                EVENT_SUPP_SERVICE_NOTIFICATION, null);
        mImsPhoneUT.registerForSuppServiceFailed(mTestHandler,
                EVENT_SUPP_SERVICE_FAILED, null);
        mImsPhoneUT.registerForIncomingRing(mTestHandler,
                EVENT_INCOMING_RING, null);
        mImsPhoneUT.setVoiceCallSessionStats(mVoiceCallSessionStats);
        mImsPhoneUT.setImsStats(mImsStats);
        doReturn(mImsUtInterface).when(mImsCT).getUtInterface();
        // When the mock GsmCdmaPhone gets setIsInEcbm called, ensure isInEcm matches.
        // TODO: Move this to a separate test class for EcbmHandler
        doAnswer(invocation -> {
            mIsPhoneUtInEcm = (Boolean) invocation.getArguments()[0];
            return null;
        }).when(mEcbmHandler).setIsInEcm(anyBoolean());
        doAnswer(invocation -> mIsPhoneUtInEcm).when(mPhone).isInEcm();

        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        processAllMessages();
    }


    @After
    public void tearDown() throws Exception {
        mImsPhoneUT = null;
        mBundle = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallDeflection() throws Exception {
        doReturn(Call.State.INCOMING).when(mRingingCall).getState();

        // dial string length > 1
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("00"));

        // ringing call is not idle
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("0"));
        verify(mImsCT).rejectCall();

        // ringing is idle, background call is not idle
        doReturn(Call.State.IDLE).when(mRingingCall).getState();
        doReturn(Call.State.ACTIVE).when(mBackgroundCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("0"));
        verify(mImsCT).hangup(mBackgroundCall);
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallWaiting() throws Exception {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 2
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("100"));

        // dial string length > 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("10"));
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_SUPP_SERVICE_FAILED, messageArgumentCaptor.getValue().what);
        assertEquals(Phone.SuppService.HANGUP,
                ((AsyncResult)messageArgumentCaptor.getValue().obj).result);

        // foreground call is not idle
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("1"));
        verify(mImsCT).hangup(any(ImsPhoneCall.class));

        // foreground call is idle
        doReturn(Call.State.IDLE).when(mForegroundCall).getState();
        doReturn(Call.State.INCOMING).when(mRingingCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("1"));
        verify(mImsCT).holdActiveCallForWaitingCall();
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallHold() throws Exception {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 2
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("200"));

        // dial string length > 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("20"));
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_SUPP_SERVICE_FAILED, messageArgumentCaptor.getValue().what);
        assertEquals(Phone.SuppService.SEPARATE,
                ((AsyncResult) messageArgumentCaptor.getValue().obj).result);

        // ringing call is idle, only an active call present
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("2"));
        verify(mImsCT).holdActiveCall();

        // background call is holding
        doReturn(Call.State.HOLDING).when(mBackgroundCall).getState();
        doReturn(Call.State.IDLE).when(mForegroundCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("2"));
        verify(mImsCT).unholdHeldCall();

        // background call is holding and there's an active foreground call
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("2"));
        verify(mImsCT, times(2)).holdActiveCall();

        // ringing call is not idle
        doReturn(Call.State.IDLE).when(mForegroundCall).getState();
        doReturn(Call.State.IDLE).when(mBackgroundCall).getState();
        doReturn(Call.State.INCOMING).when(mRingingCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("2"));
        verify(mImsCT).acceptCall(ImsCallProfile.CALL_TYPE_VOICE);

        // Verify b/286499659, fixed media type
        doReturn(true).when(mFeatureFlags).answerAudioOnlyWhenAnsweringViaMmiCode();
        doReturn(Call.State.IDLE).when(mForegroundCall).getState();
        doReturn(Call.State.IDLE).when(mBackgroundCall).getState();
        doReturn(Call.State.INCOMING).when(mRingingCall).getState();
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("2"));
        verify(mImsCT).acceptCall(VideoProfile.STATE_AUDIO_ONLY);
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandMultiparty() {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 1
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("30"));

        // dial string length == 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("3"));
        verify(mImsCT).conference();
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallEct() throws Exception {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 1
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("40"));

        // dial string length == 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("4"));
        verify(mImsCT).explicitCallTransfer();
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommandCallCcbs() {
        doReturn(Call.State.ACTIVE).when(mForegroundCall).getState();

        // dial string length > 1
        assertEquals(false, mImsPhoneUT.handleInCallMmiCommands("50"));

        // dial string length == 1
        assertEquals(true, mImsPhoneUT.handleInCallMmiCommands("5"));
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_SUPP_SERVICE_FAILED, messageArgumentCaptor.getValue().what);
        assertEquals(Phone.SuppService.UNKNOWN,
                ((AsyncResult)messageArgumentCaptor.getValue().obj).result);
    }

    @Test
    @SmallTest
    public void testDispose() {
        // add MMI to verify that dispose removes it
        mImsPhoneUT.sendUssdResponse("1234");
        verify(mImsCT).sendUSSD(eq("1234"), any(Message.class));
        List<?> list = mImsPhoneUT.getPendingMmiCodes();
        assertNotNull(list);
        assertEquals(1, list.size());

        mImsPhoneUT.dispose();
        assertEquals(0, list.size());
        verify(mImsCT).dispose();
        verify(mSST, times(2)).unregisterForDataRegStateOrRatChanged(anyInt(), eq(mImsPhoneUT));
    }

    @Test
    @SmallTest
    public void testGettersAndPassThroughs() throws Exception {
        Message msg = mTestHandler.obtainMessage();

        assertNotNull(mImsPhoneUT.getServiceState());
        assertEquals(mImsCT, mImsPhoneUT.getCallTracker());

        mImsPhoneUT.acceptCall(0);
        verify(mImsCT).acceptCall(0);

        mImsPhoneUT.rejectCall();
        verify(mImsCT).rejectCall();

        assertEquals(false, mImsPhoneUT.canConference());
        doReturn(true).when(mImsCT).canConference();
        assertEquals(true, mImsPhoneUT.canConference());
        verify(mImsCT, times(2)).canConference();

        doNothing().when(mImsCT).checkForDialIssues();
        assertEquals(true, mImsPhoneUT.canDial());
        doThrow(CallStateException.class).when(mImsCT).checkForDialIssues();
        assertEquals(false, mImsPhoneUT.canDial());
        verify(mImsCT, times(2)).checkForDialIssues();

        mImsPhoneUT.conference();
        verify(mImsCT).conference();

        mImsPhoneUT.clearDisconnected();
        verify(mImsCT).clearDisconnected();

        assertEquals(false, mImsPhoneUT.canTransfer());
        doReturn(true).when(mImsCT).canTransfer();
        assertEquals(true, mImsPhoneUT.canTransfer());
        verify(mImsCT, times(2)).canTransfer();

        mImsPhoneUT.explicitCallTransfer();
        verify(mImsCT).explicitCallTransfer();

        assertEquals(mForegroundCall, mImsPhoneUT.getForegroundCall());
        assertEquals(mBackgroundCall, mImsPhoneUT.getBackgroundCall());
        assertEquals(mRingingCall, mImsPhoneUT.getRingingCall());

        mImsPhoneUT.notifyNewRingingConnection(mConnection);
        verify(mPhone).notifyNewRingingConnectionP(mConnection);

        mImsPhoneUT.notifyForVideoCapabilityChanged(true);
        verify(mPhone).notifyForVideoCapabilityChanged(true);

        mImsPhoneUT.setMute(true);
        verify(mImsCT).setMute(true);

        mImsPhoneUT.setUiTTYMode(1234, null);
        verify(mImsCT).setUiTTYMode(1234, null);

        doReturn(false).when(mImsCT).getMute();
        assertEquals(false, mImsPhoneUT.getMute());
        doReturn(true).when(mImsCT).getMute();
        assertEquals(true, mImsPhoneUT.getMute());
        verify(mImsCT, times(2)).getMute();

        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        assertEquals(PhoneConstants.State.IDLE, mImsPhoneUT.getState());
        doReturn(PhoneConstants.State.RINGING).when(mImsCT).getState();
        assertEquals(PhoneConstants.State.RINGING, mImsPhoneUT.getState());

        mImsPhoneUT.sendUSSD("1234", msg);
        verify(mImsCT).sendUSSD("1234", msg);

        mImsPhoneUT.cancelUSSD(msg);
        verify(mImsCT).cancelUSSD(msg);

    }

    @Test
    @SmallTest
    public void testSuppServiceNotification() {
        SuppServiceNotification ssn = new SuppServiceNotification();
        mImsPhoneUT.notifySuppSvcNotification(ssn);

        // verify registrants are notified
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler, times(1)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        Message message = messageArgumentCaptor.getValue();
        assertEquals(EVENT_SUPP_SERVICE_NOTIFICATION, message.what);
        assertEquals(ssn, ((AsyncResult) message.obj).result);
        assertEquals(null, ((AsyncResult) message.obj).userObj);
        assertEquals(null, ((AsyncResult) message.obj).exception);

        // verify no notification is received after unregister (verify() still sees only 1
        // notification)
        mImsPhoneUT.unregisterForSuppServiceNotification(mTestHandler);
        mImsPhoneUT.notifySuppSvcNotification(ssn);
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @SmallTest
    public void testDial() throws Exception {
        String dialString = "1234567890";
        int videoState = 0;

        mImsPhoneUT.dial(dialString,
                new ImsPhone.ImsDialArgs.Builder().setVideoState(videoState).build());
        verify(mImsCT).dial(eq(dialString), any(ImsPhone.ImsDialArgs.class));
    }

    @Test
    @SmallTest
    public void testDtmf() {
        // case 1
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        mImsPhoneUT.sendDtmf('-');
        verify(mImsCT, times(0)).sendDtmf(anyChar(), nullable(Message.class));

        // case 2
        mImsPhoneUT.sendDtmf('0');
        verify(mImsCT, times(0)).sendDtmf(eq('0'), nullable(Message.class));

        // case 3
        doReturn(PhoneConstants.State.OFFHOOK).when(mImsCT).getState();
        mImsPhoneUT.sendDtmf('-');
        verify(mImsCT, times(0)).sendDtmf(eq('0'), nullable(Message.class));

        // case 4
        mImsPhoneUT.sendDtmf('0');
        verify(mImsCT, times(1)).sendDtmf(anyChar(), nullable(Message.class));

        mImsPhoneUT.startDtmf('-');
        verify(mImsCT, times(0)).startDtmf(anyChar());

        mImsPhoneUT.startDtmf('0');
        verify(mImsCT, times(1)).startDtmf('0');

        mImsPhoneUT.stopDtmf();
        verify(mImsCT).stopDtmf();
    }

    @Test
    @SmallTest
    public void testIncomingRing() {
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        mImsPhoneUT.notifyIncomingRing();
        processAllMessages();
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler, times(1)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        Message message = messageArgumentCaptor.getValue();
        assertEquals(EVENT_INCOMING_RING, message.what);
    }

    @Test
    @SmallTest
    public void testOutgoingCallerIdDisplay() throws Exception {
        Message msg = mTestHandler.obtainMessage();
        mImsPhoneUT.getOutgoingCallerIdDisplay(msg);

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mImsUtInterface).queryCLIR(messageArgumentCaptor.capture());
        assertNotNull(messageArgumentCaptor.getValue().obj);
        SS ss = (SS) messageArgumentCaptor.getValue().obj;
        assertEquals(msg, ss.mOnComplete);

        mImsPhoneUT.setOutgoingCallerIdDisplay(1234, msg);
        verify(mImsUtInterface).updateCLIR(eq(1234), messageArgumentCaptor.capture());
        assertNotNull(messageArgumentCaptor.getValue().obj);
        ss = (SS) messageArgumentCaptor.getValue().obj;
        assertEquals(msg, ss.mOnComplete);
    }

    @FlakyTest
    @Test
    @Ignore
    public void testCallForwardingOption() throws Exception {
        Message msg = mTestHandler.obtainMessage();
        mImsPhoneUT.getCallForwardingOption(CF_REASON_UNCONDITIONAL, msg);

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mImsUtInterface).queryCallForward(eq(ImsUtInterface.CDIV_CF_UNCONDITIONAL),
                (String) eq(null), messageArgumentCaptor.capture());
        assertEquals(msg, messageArgumentCaptor.getValue().obj);

        mImsPhoneUT.setCallForwardingOption(CF_ACTION_ENABLE, CF_REASON_UNCONDITIONAL, "1234", 0,
                msg);
        verify(mImsUtInterface).updateCallForward(eq(ImsUtInterface.ACTION_ACTIVATION),
                eq(ImsUtInterface.CDIV_CF_UNCONDITIONAL), eq("1234"),
                eq(CommandsInterface.SERVICE_CLASS_VOICE), eq(0), eq(msg));
    }

    @Test
    @SmallTest
    public void testCallWaiting() throws Exception {
        Message msg = mTestHandler.obtainMessage();
        mImsPhoneUT.getCallWaiting(msg);

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mImsUtInterface).queryCallWaiting(messageArgumentCaptor.capture());
        assertNotNull(messageArgumentCaptor.getValue().obj);
        SS ss = (SS) messageArgumentCaptor.getValue().obj;
        assertEquals(msg, ss.mOnComplete);

        mImsPhoneUT.setCallWaiting(true, msg);
        verify(mImsUtInterface).updateCallWaiting(eq(true),
                eq(CommandsInterface.SERVICE_CLASS_VOICE), messageArgumentCaptor.capture());
        assertNotNull(messageArgumentCaptor.getValue().obj);
        ss = (SS) messageArgumentCaptor.getValue().obj;
        assertEquals(msg, ss.mOnComplete);
    }

    @Test
    public void testShouldSendNotificationWhenServiceStateIsChanged() {
        mImsPhoneUT.setServiceState(ServiceState.STATE_IN_SERVICE);
        reset(mSST);

        mImsPhoneUT.setServiceState(ServiceState.STATE_OUT_OF_SERVICE);
        verify(mSST).onImsServiceStateChanged();
    }

    @Test
    public void testShouldNotSendNotificationWhenServiceStateIsNotChanged() {
        mImsPhoneUT.setServiceState(ServiceState.STATE_IN_SERVICE);
        reset(mSST);

        mImsPhoneUT.setServiceState(ServiceState.STATE_IN_SERVICE);
        verify(mSST, never()).onImsServiceStateChanged();
    }

    @Test
    @SmallTest
    public void testCellBarring() throws Exception {
        Message msg = mTestHandler.obtainMessage();
        mImsPhoneUT.getCallBarring(CommandsInterface.CB_FACILITY_BAOC, msg,
                CommandsInterface.SERVICE_CLASS_NONE);

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mImsUtInterface).queryCallBarring(eq(ImsUtImplBase.CALL_BARRING_ALL_OUTGOING),
                messageArgumentCaptor.capture(), eq(CommandsInterface.SERVICE_CLASS_NONE));
        assertNotNull(messageArgumentCaptor.getValue().obj);
        SS ss = (SS) messageArgumentCaptor.getValue().obj;
        assertEquals(msg, ss.mOnComplete);

        mImsPhoneUT.setCallBarring(CommandsInterface.CB_FACILITY_BAOIC, true, "abc", msg,
                CommandsInterface.SERVICE_CLASS_NONE);
        verify(mImsUtInterface).updateCallBarring(eq(ImsUtImplBase.CALL_BARRING_OUTGOING_INTL),
                eq(CommandsInterface.CF_ACTION_ENABLE), messageArgumentCaptor.capture(),
                (String[]) eq(null), eq(CommandsInterface.SERVICE_CLASS_NONE), eq("abc"));
        assertNotNull(messageArgumentCaptor.getValue().obj);
        ss = (SS) messageArgumentCaptor.getValue().obj;
        assertEquals(msg, ss.mOnComplete);

        mImsPhoneUT.setCallBarring(CommandsInterface.CB_FACILITY_BAOICxH, false, "abc", msg,
                CommandsInterface.SERVICE_CLASS_NONE);
        verify(mImsUtInterface).updateCallBarring(
                eq(ImsUtImplBase.CALL_BARRING_OUTGOING_INTL_EXCL_HOME),
                eq(CommandsInterface.CF_ACTION_DISABLE), messageArgumentCaptor.capture(),
                (String[]) eq(null), eq(CommandsInterface.SERVICE_CLASS_NONE), eq("abc"));
        assertNotNull(messageArgumentCaptor.getValue().obj);
        ss = (SS) messageArgumentCaptor.getValue().obj;
        assertEquals(msg, ss.mOnComplete);
    }

    @Test
    @Ignore
    // TODO: Move this to a separate test class for EcbmHandler
    public void testEcbm() throws Exception {
        EcbmHandler.getInstance().setOnEcbModeExitResponse(mTestHandler,
                EVENT_EMERGENCY_CALLBACK_MODE_EXIT, null);

        ImsEcbmStateListener imsEcbmStateListener =
                EcbmHandler.getInstance().getImsEcbmStateListener(mPhone.getPhoneId());
        imsEcbmStateListener.onECBMEntered();
        verify(EcbmHandler.getInstance()).setIsInEcm(true);

        verifyEcbmIntentWasSent(1 /*times*/, true /*inEcm*/);
        // verify that wakeLock is acquired in ECM
        assertTrue(mImsPhoneUT.getWakeLock().isHeld());

        imsEcbmStateListener.onECBMExited();
        verify(EcbmHandler.getInstance()).setIsInEcm(false);

        verifyEcbmIntentWasSent(2/*times*/, false /*inEcm*/);

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        // verify EcmExitRespRegistrant is notified
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        assertEquals(EVENT_EMERGENCY_CALLBACK_MODE_EXIT, messageArgumentCaptor.getValue().what);

        // verify wakeLock released
        assertFalse(mImsPhoneUT.getWakeLock().isHeld());
    }

    private void verifyEcbmIntentWasSent(int times, boolean isInEcm) throws Exception {
        // verify ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeast(times)).sendStickyBroadcastAsUser(intentArgumentCaptor.capture(),
                any());

        Intent intent = intentArgumentCaptor.getValue();
        assertNotNull(intent);
        assertEquals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, intent.getAction());
        assertEquals(isInEcm, intent.getBooleanExtra(
                TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false));
    }

    @Test
    @SmallTest
    public void testProcessDisconnectReason() throws Exception {
        // set up CarrierConfig
        mBundle.putStringArray(CarrierConfigManager.KEY_WFC_OPERATOR_ERROR_CODES_STRING_ARRAY,
                new String[]{"REG09|0"});
        doReturn(true).when(mImsManager).isWfcEnabledByUser();

        // set up overlays
        String title = "title";
        String messageAlert = "Alert!";
        String messageNotification = "Notification!";
        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.wfcOperatorErrorAlertMessages,
                new String[]{messageAlert});
        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.wfcOperatorErrorNotificationMessages,
                new String[]{messageNotification});
        mContextFixture.putResource(com.android.internal.R.string.wfcRegErrorTitle, title);

        mImsPhoneUT.processDisconnectReason(
                new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR, 0, "REG09"));

        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendOrderedBroadcast(
                intent.capture(), nullable(String.class), any(BroadcastReceiver.class),
                nullable(Handler.class), eq(Activity.RESULT_OK), nullable(String.class),
                nullable(Bundle.class));
        assertEquals(android.telephony.ims.ImsManager.ACTION_WFC_IMS_REGISTRATION_ERROR,
                intent.getValue().getAction());
        assertEquals(title, intent.getValue().getStringExtra(
                android.telephony.ims.ImsManager.EXTRA_WFC_REGISTRATION_FAILURE_TITLE));
        assertEquals(messageAlert, intent.getValue().getStringExtra(
                android.telephony.ims.ImsManager.EXTRA_WFC_REGISTRATION_FAILURE_MESSAGE));
        assertEquals(messageNotification,
                intent.getValue().getStringExtra(Phone.EXTRA_KEY_NOTIFICATION_MESSAGE));
    }

    @Test
    @SmallTest
    public void testImsRegistered() throws Exception {
        mImsPhoneUT.setServiceState(ServiceState.STATE_IN_SERVICE);
        mImsPhoneUT.setImsRegistered(true);
        assertTrue(mImsPhoneUT.isImsRegistered());

        LinkedBlockingQueue<Integer> result = new LinkedBlockingQueue<>(1);
        mImsPhoneUT.getImsRegistrationState(result::offer);
        Integer regResult = result.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(regResult);
        assertEquals(RegistrationManager.REGISTRATION_STATE_REGISTERED, regResult.intValue());
    }

    @Test
    @SmallTest
    public void testImsDeregistered() throws Exception {
        mImsPhoneUT.setServiceState(ServiceState.STATE_OUT_OF_SERVICE);
        mImsPhoneUT.setImsRegistered(false);
        assertFalse(mImsPhoneUT.isImsRegistered());

        LinkedBlockingQueue<Integer> result = new LinkedBlockingQueue<>(1);
        mImsPhoneUT.getImsRegistrationState(result::offer);
        Integer regResult = result.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(regResult);
        assertEquals(RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED, regResult.intValue());
    }

    @Test
    @SmallTest
    public void testGetImsRegistrationTech() throws Exception {
        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(1);
        Consumer<Integer> regTechCallback = queue::offer;
        doAnswer(invocation -> {
            Consumer<Integer> c = (Consumer<Integer>) invocation.getArguments()[0];
            c.accept(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
            return null;
        }).when(mImsCT).getImsRegistrationTech(eq(regTechCallback));
        mImsPhoneUT.getImsRegistrationTech(regTechCallback);
        Integer regTechResult = queue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(regTechResult);
        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN, regTechResult.intValue());
    }

    @Test
    @SmallTest
    public void testRoamingDuplicateMessages() throws Exception {
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        doReturn(true).when(mPhone).isRadioOn();

        //roaming - data registration only on LTE
        Message m = getServiceStateChangedMessage(getServiceStateDataOnly(
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ServiceState.STATE_IN_SERVICE, true));
        // Inject the message synchronously instead of waiting for the thread to do it.
        mImsPhoneUT.handleMessage(m);

        verify(mImsManager, times(1)).setWfcMode(anyInt(), eq(true));

        // Send a duplicate message
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        // setWfcMode should not be called again.
        verify(mImsManager, times(1)).setWfcMode(anyInt(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testRoamingToAirplanModeIwlanInService() throws Exception {
        doReturn(true).when(mAccessNetworksManager).isInLegacyMode();
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        doReturn(true).when(mPhone).isRadioOn();

        //roaming - data registration only on LTE
        Message m = getServiceStateChangedMessage(getServiceStateDataOnly(
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ServiceState.STATE_IN_SERVICE, true));
        // Inject the message synchronously instead of waiting for the thread to do it.
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        verify(mImsManager, times(1)).setWfcMode(anyInt(), eq(true));

        // move to airplane mode + IWLAN in service
        doReturn(false).when(mPhone).isRadioOn();
        m = getServiceStateChangedMessage(getServiceStateDataOnly(
            ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN, ServiceState.STATE_IN_SERVICE, false));
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        // setWfcMode should not be called again, airplane mode should not trigger move out of
        // roaming.
        verify(mImsManager, times(1)).setWfcMode(anyInt(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testRoamingToOutOfService() throws Exception {
        doReturn(true).when(mAccessNetworksManager).isInLegacyMode();
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        doReturn(true).when(mPhone).isRadioOn();

        //roaming - data registration only on LTE
        Message m = getServiceStateChangedMessage(getServiceStateDataOnly(
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ServiceState.STATE_IN_SERVICE, true));
        // Inject the message synchronously instead of waiting for the thread to do it.
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        verify(mImsManager, times(1)).setWfcMode(anyInt(), eq(true));

        // move to out of service
        m = getServiceStateChangedMessage(getServiceStateOutOfService());
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        // setWfcMode should not be called again, out_of_service should not trigger move out of
        // roaming.
        verify(mImsManager, times(1)).setWfcMode(anyInt(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testRoamingChangeForLteInLegacyMode() throws Exception {
        doReturn(true).when(mAccessNetworksManager).isInLegacyMode();
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        doReturn(true).when(mPhone).isRadioOn();

        //roaming - data registration only on LTE
        Message m = getServiceStateChangedMessage(getServiceStateDataOnly(
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ServiceState.STATE_IN_SERVICE, true));
        // Inject the message synchronously instead of waiting for the thread to do it.
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        verify(mImsManager, times(1)).setWfcMode(anyInt(), eq(true));

        // not roaming - data registration on LTE
        m = getServiceStateChangedMessage(getServiceStateDataOnly(
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ServiceState.STATE_IN_SERVICE, false));
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        verify(mImsManager, times(1)).setWfcMode(anyInt(), eq(false));
    }

    @Test
    @SmallTest
    public void testDataOnlyRoamingCellToIWlanInLegacyMode() throws Exception {
        doReturn(true).when(mAccessNetworksManager).isInLegacyMode();
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        doReturn(true).when(mPhone).isRadioOn();

        //roaming - data registration only on LTE
        Message m = getServiceStateChangedMessage(getServiceStateDataOnly(
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ServiceState.STATE_IN_SERVICE, true));
        // Inject the message synchronously instead of waiting for the thread to do it.
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        verify(mImsManager, times(1)).setWfcMode(anyInt(), eq(true));

        // not roaming - data registration onto IWLAN
        m = getServiceStateChangedMessage(getServiceStateDataOnly(
                ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN, ServiceState.STATE_IN_SERVICE, false));
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        // Verify that it hasn't been called again.
        verify(mImsManager, times(1)).setWfcMode(anyInt(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testCellVoiceDataChangeToWlanInLegacyMode() throws Exception {
        doReturn(true).when(mAccessNetworksManager).isInLegacyMode();
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        doReturn(true).when(mPhone).isRadioOn();

        //roaming - voice/data registration on LTE
        ServiceState ss = getServiceStateDataAndVoice(
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ServiceState.STATE_IN_SERVICE, true);
        Message m = getServiceStateChangedMessage(ss);
        // Inject the message synchronously instead of waiting for the thread to do it.
        mImsPhoneUT.handleMessage(m);

        verify(mImsManager, times(1)).setWfcMode(anyInt(), eq(true));

        // roaming - voice LTE, data registration onto IWLAN
        modifyServiceStateData(ss, ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN,
                ServiceState.STATE_IN_SERVICE, false);
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        // Verify that it hasn't been called again.
        verify(mImsManager, times(1)).setWfcMode(anyInt(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testSetWfcModeInRoaming() throws Exception {
        doReturn(true).when(mFeatureFlags).updateRoamingStateToSetWfcMode();
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();
        doReturn(true).when(mPhone).isRadioOn();

        // Set CarrierConfigManager to be invalid
        doReturn(null).when(mContext).getSystemService(Context.CARRIER_CONFIG_SERVICE);

        //Roaming - data registration only on LTE
        Message m = getServiceStateChangedMessage(getServiceStateDataOnly(
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ServiceState.STATE_IN_SERVICE, true));
        // Inject the message synchronously instead of waiting for the thread to do it.
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        verify(mImsManager, never()).setWfcMode(anyInt(), eq(true));

        // Set CarrierConfigManager to be valid
        doReturn(mCarrierConfigManager).when(mContext)
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);

        m = getServiceStateChangedMessage(getServiceStateDataOnly(
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ServiceState.STATE_IN_SERVICE, true));
        mImsPhoneUT.handleMessage(m);
        m.recycle();

        verify(mImsManager, times(1)).setWfcMode(anyInt(), eq(true));
    }

    @Test
    public void testNonNullTrackersInImsPhone() throws Exception {
        assertNotNull(mImsPhoneUT.getEmergencyNumberTracker());
        assertNotNull(mImsPhoneUT.getServiceStateTracker());
    }

    @Test
    @SmallTest
    public void testSendUssdAllowUssdOverImsInOutOfService() throws Exception {
        Resources resources = mContext.getResources();

        mBundle.putInt(CarrierConfigManager.KEY_CARRIER_USSD_METHOD_INT,
                        USSD_OVER_CS_PREFERRED);
        doReturn(true).when(resources).getBoolean(
                com.android.internal.R.bool.config_allow_ussd_over_ims);
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mSST.mSS).getState();

        mImsPhoneUT.dial("*135#", new ImsPhone.ImsDialArgs.Builder().build());
        verify(mImsCT).sendUSSD(eq("*135#"), any());

        mImsPhoneUT.dial("91", new ImsPhone.ImsDialArgs.Builder().build());
        verify(mImsCT).sendUSSD(eq("91"), any());
    }

    @Test
    @SmallTest
    public void testSendUssdAllowUssdOverImsInService() throws Exception {
        String errorCode = "";
        Resources resources = mContext.getResources();

        mBundle.putInt(CarrierConfigManager.KEY_CARRIER_USSD_METHOD_INT,
                        USSD_OVER_CS_PREFERRED);
        doReturn(true).when(resources).getBoolean(
                com.android.internal.R.bool.config_allow_ussd_over_ims);
        doReturn(ServiceState.STATE_IN_SERVICE).when(mSST.mSS).getState();

        try {
            mImsPhoneUT.dial("*135#", new ImsPhone.ImsDialArgs.Builder().build());
        } catch (CallStateException e) {
            errorCode = e.getMessage();
        }
        assertEquals(Phone.CS_FALLBACK, errorCode);

        try {
            mImsPhoneUT.dial("91", new ImsPhone.ImsDialArgs.Builder().build());
        } catch (CallStateException e) {
            errorCode = e.getMessage();
        }
        assertEquals(Phone.CS_FALLBACK, errorCode);
    }

    @Test
    @SmallTest
    public void testSendUssdNotAllowUssdOverIms() throws Exception {
        String errorCode = "";
        Resources resources = mContext.getResources();

        doReturn(false).when(resources).getBoolean(
                com.android.internal.R.bool.config_allow_ussd_over_ims);

        try {
            mImsPhoneUT.dial("*135#", new ImsPhone.ImsDialArgs.Builder().build());
        } catch (CallStateException e) {
            errorCode = e.getMessage();
        }
        assertEquals(Phone.CS_FALLBACK, errorCode);

        try {
            mImsPhoneUT.dial("91", new ImsPhone.ImsDialArgs.Builder().build());
        } catch (CallStateException e) {
            errorCode = e.getMessage();
        }
        assertEquals(Phone.CS_FALLBACK, errorCode);
    }

    @Test
    @SmallTest
    public void testSendUssdAllowUssdOverImsWithImsPreferred() throws Exception {
        Resources resources = mContext.getResources();

        mBundle.putInt(CarrierConfigManager.KEY_CARRIER_USSD_METHOD_INT,
                        USSD_OVER_IMS_PREFERRED);
        doReturn(true).when(resources).getBoolean(
                com.android.internal.R.bool.config_allow_ussd_over_ims);
        doReturn(ServiceState.STATE_IN_SERVICE).when(mSST.mSS).getState();

        mImsPhoneUT.dial("*135#", new ImsPhone.ImsDialArgs.Builder().build());
        verify(mImsCT).sendUSSD(eq("*135#"), any());

        mImsPhoneUT.dial("91", new ImsPhone.ImsDialArgs.Builder().build());
        verify(mImsCT).sendUSSD(eq("91"), any());
    }

    @Test
    @SmallTest
    public void testSendUssdAllowUssdOverImsWithCsOnly() throws Exception {
        String errorCode = "";
        Resources resources = mContext.getResources();

        mBundle.putInt(CarrierConfigManager.KEY_CARRIER_USSD_METHOD_INT,
                        USSD_OVER_CS_ONLY);
        doReturn(true).when(resources).getBoolean(
                com.android.internal.R.bool.config_allow_ussd_over_ims);
        doReturn(ServiceState.STATE_IN_SERVICE).when(mSST.mSS).getState();

        try {
            mImsPhoneUT.dial("*135#", new ImsPhone.ImsDialArgs.Builder().build());
        } catch (CallStateException e) {
            errorCode = e.getMessage();
        }
        assertEquals(Phone.CS_FALLBACK, errorCode);

        try {
            mImsPhoneUT.dial("91", new ImsPhone.ImsDialArgs.Builder().build());
        } catch (CallStateException e) {
            errorCode = e.getMessage();
        }
        assertEquals(Phone.CS_FALLBACK, errorCode);
    }

    @Test
    @SmallTest
    public void testSendUssdAllowUssdOverImsWithImsOnly() throws Exception {
        Resources resources = mContext.getResources();

        mBundle.putInt(CarrierConfigManager.KEY_CARRIER_USSD_METHOD_INT,
                        USSD_OVER_IMS_ONLY);
        doReturn(true).when(resources).getBoolean(
                com.android.internal.R.bool.config_allow_ussd_over_ims);
        doReturn(ServiceState.STATE_IN_SERVICE).when(mSST.mSS).getState();

        mImsPhoneUT.dial("*135#", new ImsPhone.ImsDialArgs.Builder().build());
        verify(mImsCT).sendUSSD(eq("*135#"), any());

        mImsPhoneUT.dial("91", new ImsPhone.ImsDialArgs.Builder().build());
        verify(mImsCT).sendUSSD(eq("91"), any());
    }

    @Test
    @SmallTest
    public void testHandleMessageCallRingContinue() throws Exception {
        Message m = Message.obtain(mImsPhoneUT.getHandler(), EVENT_CALL_RING_CONTINUE);
        mImsPhoneUT.handleMessage(m);
    }

    @Test
    @SmallTest
    public void testSetPhoneNumberForSourceIms() {
        // In reality the method under test runs in phone process so has MODIFY_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(MODIFY_PHONE_STATE);
        int subId = 1;
        doReturn(subId).when(mPhone).getSubId();
        SubscriptionInfo subInfo = mock(SubscriptionInfo.class);
        doReturn("gb").when(subInfo).getCountryIso();
        doReturn(new SubscriptionInfoInternal.Builder().setId(subId).setSimSlotIndex(0)
                .setCountryIso("gb").build()).when(mSubscriptionManagerService)
                .getSubscriptionInfoInternal(subId);

        // 1. Two valid phone number; 1st is set.
        Uri[] associatedUris = new Uri[] {
            Uri.parse("sip:+447539447777@ims.x.com"),
            Uri.parse("tel:+447539446666")
        };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService).setNumberFromIms(subId, "+447539447777");

        // 2. 1st invalid and 2nd valid: 2nd is set.
        associatedUris = new Uri[] {
            Uri.parse("sip:447539447777@ims.x.com"),
            Uri.parse("tel:+447539446666")
        };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService).setNumberFromIms(subId, "+447539446666");

        // 3. 1st sip-uri is not phone number and 2nd valid: 2nd is set.
        associatedUris = new Uri[] {
            Uri.parse("sip:john.doe@ims.x.com"),
            Uri.parse("tel:+447539446677"),
            Uri.parse("sip:+447539447766@ims.x.com")
        };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService).setNumberFromIms(subId, "+447539446677");

        // Clean up
        mContextFixture.addCallingOrSelfPermission("");
    }

    @Test
    @SmallTest
    public void testSetPhoneNumberForSourceImsNegativeCases() {
        // In reality the method under test runs in phone process so has MODIFY_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(MODIFY_PHONE_STATE);
        int subId = 1;
        doReturn(subId).when(mPhone).getSubId();
        SubscriptionInfo subInfo = mock(SubscriptionInfo.class);
        doReturn("gb").when(subInfo).getCountryIso();
        doReturn(new SubscriptionInfoInternal.Builder().setId(subId).setSimSlotIndex(0)
                .setCountryIso("gb").build()).when(mSubscriptionManagerService)
                .getSubscriptionInfoInternal(0);

        // 1. No valid phone number; do not set
        Uri[] associatedUris = new Uri[] {
            Uri.parse("sip:447539447777@ims.x.com"),
            Uri.parse("tel:447539446666")
        };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService, never()).setNumberFromIms(anyInt(), anyString());

        // 2. no URI; do not set
        associatedUris = new Uri[] {};
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService, never()).setNumberFromIms(anyInt(), anyString());

        // 3. null URI; do not set
        associatedUris = new Uri[] { null };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService, never()).setNumberFromIms(anyInt(), anyString());

        // 4. null pointer; do not set
        mImsPhoneUT.setPhoneNumberForSourceIms(null);

        verify(mSubscriptionManagerService, never()).setNumberFromIms(anyInt(), anyString());

        // Clean up
        mContextFixture.addCallingOrSelfPermission("");
    }

    @Test
    @SmallTest
    public void testSetPhoneNumberForSourceIgnoreGlobalPhoneNumberFormat() {
        // In reality the method under test runs in phone process so has MODIFY_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(MODIFY_PHONE_STATE);
        int subId = 1;
        doReturn(subId).when(mPhone).getSubId();
        doReturn(new SubscriptionInfoInternal.Builder().setId(subId).setSimSlotIndex(0)
                .setCountryIso("gb").build()).when(mSubscriptionManagerService)
                .getSubscriptionInfoInternal(subId);
        // Set carrier config to ignore global phone number format
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.Ims.KEY_ALLOW_NON_GLOBAL_PHONE_NUMBER_FORMAT_BOOL, true);
        doReturn(bundle).when(mCarrierConfigManager).getConfigForSubId(eq(subId),
                eq(CarrierConfigManager.Ims.KEY_ALLOW_NON_GLOBAL_PHONE_NUMBER_FORMAT_BOOL));

        // 1. Two non-global phone number; 1st is set.
        Uri[] associatedUris = new Uri[] {
                Uri.parse("sip:01012345678@lte-uplus.co.kr"),
                Uri.parse("tel:01012345678")
        };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService).setNumberFromIms(subId, "01012345678");

        // 2. 1st non-global phone number and 2nd global number; 2nd is set.
        associatedUris = new Uri[] {
                Uri.parse("sip:01012345678@lte-uplus.co.kr"),
                Uri.parse("tel:+821012345678")
        };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService).setNumberFromIms(subId, "+821012345678");

        // 3. 1st sip-uri is not phone number and 2nd valid: 2nd is set.
        associatedUris = new Uri[] {
                Uri.parse("sip:john.doe@ims.x.com"),
                Uri.parse("sip:01022223333@lte-uplus.co.kr"),
                Uri.parse("tel:01022223333")
        };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService).setNumberFromIms(subId, "01022223333");

        clearInvocations(mSubscriptionManagerService);

        // 4. Invalid phone number : not set.
        associatedUris = new Uri[] {
                Uri.parse("sip:john.doe@ims.x.com"),
                Uri.parse("sip:hone3333@lte-uplus.co.kr"),
                Uri.parse("tel:abcd1234555")
        };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService, never()).setNumberFromIms(anyInt(), anyString());

        // Clean up
        mContextFixture.addCallingOrSelfPermission("");
    }

    @Test
    @SmallTest
    public void testClearPhoneNumberForSourceIms() {
        doReturn(true).when(mFeatureFlags)
                .clearCachedImsPhoneNumberWhenDeviceLostImsRegistration();

        // In reality the method under test runs in phone process so has MODIFY_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(MODIFY_PHONE_STATE);
        int subId = 1;
        doReturn(subId).when(mPhone).getSubId();
        doReturn(new SubscriptionInfoInternal.Builder().setId(subId).setSimSlotIndex(0)
                .setCountryIso("gb").build()).when(mSubscriptionManagerService)
                .getSubscriptionInfoInternal(subId);

        // 1. Two valid phone number; 1st is set.
        Uri[] associatedUris = new Uri[] {
                Uri.parse("sip:+447539447777@ims.x.com"),
                Uri.parse("tel:+447539446666")
        };
        mImsPhoneUT.setPhoneNumberForSourceIms(associatedUris);

        verify(mSubscriptionManagerService).setNumberFromIms(subId, "+447539447777");

        mImsPhoneUT.clearPhoneNumberForSourceIms();

        verify(mSubscriptionManagerService).setNumberFromIms(subId, "");

        // Clean up
        mContextFixture.addCallingOrSelfPermission("");
    }

    /**
     * Verifies that valid radio technology is passed to RIL
     * when IMS registration state changes to registered.
     */
    @Test
    @SmallTest
    public void testUpdateImsRegistrationInfoRadioTech() {
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);

        int[] regInfo = mSimulatedCommands.getImsRegistrationInfo();
        assertNotNull(regInfo);
        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        RegistrationManager.RegistrationCallback registrationCallback =
                mImsPhoneUT.getImsMmTelRegistrationCallback();

        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                REGISTRATION_TECH_LTE).build();
        registrationCallback.onRegistered(attr);
        mImsPhoneUT.updateImsRegistrationInfo(IMS_MMTEL_CAPABILITY_VOICE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[3] == IMS_MMTEL_CAPABILITY_VOICE);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // duplicated notification with the same radio technology
        attr = new ImsRegistrationAttributes.Builder(REGISTRATION_TECH_LTE).build();
        registrationCallback.onRegistered(attr);

        // verify that there is no change in SimulatedCommands
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // radio technology changed
        attr = new ImsRegistrationAttributes.Builder(REGISTRATION_TECH_NR).build();
        registrationCallback.onRegistered(attr);

        regInfo = mSimulatedCommands.getImsRegistrationInfo();
        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_NR
                && regInfo[3] == IMS_MMTEL_CAPABILITY_VOICE);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // duplicated notification with the same radio technology
        attr = new ImsRegistrationAttributes.Builder(REGISTRATION_TECH_NR).build();
        registrationCallback.onRegistered(attr);

        // verify that there is no change in SimulatedCommands
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // radio technology changed
        attr = new ImsRegistrationAttributes.Builder(REGISTRATION_TECH_IWLAN).build();
        registrationCallback.onRegistered(attr);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_IWLAN
                && regInfo[3] == IMS_MMTEL_CAPABILITY_VOICE);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // duplicated notification with the same radio technology
        attr = new ImsRegistrationAttributes.Builder(REGISTRATION_TECH_IWLAN).build();
        registrationCallback.onRegistered(attr);

        // verify that there is no change in SimulatedCommands
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // radio technology changed
        attr = new ImsRegistrationAttributes.Builder(REGISTRATION_TECH_3G).build();
        registrationCallback.onRegistered(attr);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_3G
                && regInfo[3] == IMS_MMTEL_CAPABILITY_VOICE);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // duplicated notification with the same radio technology
        attr = new ImsRegistrationAttributes.Builder(REGISTRATION_TECH_3G).build();
        registrationCallback.onRegistered(attr);

        // verify that there is no change in SimulatedCommands
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);
    }

    /**
     * Verifies that valid capabilities is passed to RIL
     * when IMS registration state changes to registered.
     */
    @Test
    @SmallTest
    public void testUpdateImsRegistrationInfoCapabilities() {
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);

        int[] regInfo = mSimulatedCommands.getImsRegistrationInfo();
        assertNotNull(regInfo);
        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        RegistrationManager.RegistrationCallback registrationCallback =
                mImsPhoneUT.getImsMmTelRegistrationCallback();

        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                REGISTRATION_TECH_LTE).build();
        registrationCallback.onRegistered(attr);
        mImsPhoneUT.updateImsRegistrationInfo(IMS_MMTEL_CAPABILITY_VOICE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[3] == IMS_MMTEL_CAPABILITY_VOICE);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // duplicated notification with the same capability
        mImsPhoneUT.updateImsRegistrationInfo(IMS_MMTEL_CAPABILITY_VOICE);

        // verify that there is no change in SimulatedCommands
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // capability changed
        mImsPhoneUT.updateImsRegistrationInfo(IMS_MMTEL_CAPABILITY_VIDEO);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[3] == IMS_MMTEL_CAPABILITY_VIDEO);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // duplicated notification with the same capability
        mImsPhoneUT.updateImsRegistrationInfo(IMS_MMTEL_CAPABILITY_VIDEO);

        // verify that there is no change in SimulatedCommands
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // capability changed
        mImsPhoneUT.updateImsRegistrationInfo(IMS_MMTEL_CAPABILITY_SMS);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[3] == IMS_MMTEL_CAPABILITY_SMS);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // duplicated notification with the same capability
        mImsPhoneUT.updateImsRegistrationInfo(IMS_MMTEL_CAPABILITY_SMS);

        // verify that there is no change in SimulatedCommands
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);

        // capability changed, but no capability
        mImsPhoneUT.updateImsRegistrationInfo(IMS_MMTEL_CAPABILITY_SMS);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        // verify that there is no change in SimulatedCommands.
        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[3] == 0);
    }

    /**
     * Verifies that valid state and reason is passed to RIL
     * when IMS registration state changes to unregistered.
     */
    @Test
    @SmallTest
    public void testUpdateImsRegistrationInfo() {
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);

        int[] regInfo = mSimulatedCommands.getImsRegistrationInfo();
        assertNotNull(regInfo);
        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[2] == 0);

        RegistrationManager.RegistrationCallback registrationCallback =
                mImsPhoneUT.getImsMmTelRegistrationCallback();

        ImsReasonInfo reasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                ImsReasonInfo.CODE_UNSPECIFIED, "");

        // unregistered with fatal error
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK, REGISTRATION_TECH_NR);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_NR
                && regInfo[2] == SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[2] == 0);

        // unregistered with fatal error but rat changed
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK, REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[2] == SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[2] == 0);

        // verifies that duplicated notification with the same suggested action is invoked
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK, REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[2] == SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK);

        // unregistered with repeated error
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK_WITH_TIMEOUT,
                REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[2] == SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK_WITH_TIMEOUT);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[2] == 0);

        // verfies that duplicated notification with the same suggested action is invoked
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK_WITH_TIMEOUT,
                REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[2] == SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK_WITH_TIMEOUT);

        // unregistered with temporary error
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_NONE, REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[2] == SUGGESTED_ACTION_NONE);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[2] == 0);

        // verfies that duplicated notification with temporary error is discarded
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_NONE, REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[2] == 0);

        // verifies that reason codes except ImsReasonInfo.CODE_REGISTRATION_ERROR are discarded.
        reasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NETWORK_NO_SERVICE,
                ImsReasonInfo.CODE_UNSPECIFIED, "");
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK, REGISTRATION_TECH_NR);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_NR
                && regInfo[2] == SUGGESTED_ACTION_NONE);

        // change the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(1, 1, 1, 1, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 1 && regInfo[1] == 1 && regInfo[2] == 1);

        // verifies that duplicated notification with temporary error is discarded
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_NONE, REGISTRATION_TECH_NR);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        // verify that there is no update in the SimulatedCommands
        assertTrue(regInfo[0] == 1 && regInfo[1] == 1 && regInfo[2] == 1);
    }

    /**
     * Verifies that valid state and reason is passed to RIL with RAT suggested actions
     * when IMS registration state changes to unregistered.
     */
    @Test
    @SmallTest
    public void testUpdateImsRegistrationInfoWithRatSuggestedAction() {
        doReturn(true).when(mFeatureFlags)
                .addRatRelatedSuggestedActionToImsRegistration();

        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);

        int[] regInfo = mSimulatedCommands.getImsRegistrationInfo();
        assertNotNull(regInfo);
        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[2] == 0);

        RegistrationManager.RegistrationCallback registrationCallback =
                mImsPhoneUT.getImsMmTelRegistrationCallback();

        ImsReasonInfo reasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                ImsReasonInfo.CODE_UNSPECIFIED, "");

        // unregistered with rat block
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_RAT_BLOCK,
                REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[2] == SUGGESTED_ACTION_TRIGGER_RAT_BLOCK);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[2] == 0);

        // verfies that duplicated notification with the same suggested action is invoked
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_RAT_BLOCK,
                REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[2] == SUGGESTED_ACTION_TRIGGER_RAT_BLOCK);

        // unregistered with rat block clear
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_CLEAR_RAT_BLOCKS,
                REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[2] == SUGGESTED_ACTION_TRIGGER_CLEAR_RAT_BLOCKS);

        // reset the registration info saved in the SimulatedCommands
        mSimulatedCommands.updateImsRegistrationInfo(0, 0, 0, 0, null);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == 0 && regInfo[1] == 0 && regInfo[2] == 0);

        // verfies that duplicated notification with the same suggested action is invoked
        registrationCallback.onUnregistered(reasonInfo,
                SUGGESTED_ACTION_TRIGGER_CLEAR_RAT_BLOCKS,
                REGISTRATION_TECH_LTE);
        regInfo = mSimulatedCommands.getImsRegistrationInfo();

        assertTrue(regInfo[0] == RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED
                && regInfo[1] == REGISTRATION_TECH_LTE
                && regInfo[2] == SUGGESTED_ACTION_TRIGGER_CLEAR_RAT_BLOCKS);
    }

    @Test
    @SmallTest
    public void testImsDialArgsBuilderFromForAlternateService() {
        ImsPhone.ImsDialArgs dialArgs = new ImsPhone.ImsDialArgs.Builder()
                .setIsEmergency(true)
                .setEccCategory(2)
                .build();

        ImsPhone.ImsDialArgs copiedDialArgs =
                ImsPhone.ImsDialArgs.Builder.from(dialArgs).build();

        assertTrue(copiedDialArgs.isEmergency);
        assertEquals(2, copiedDialArgs.eccCategory);
    }

    @Test
    @SmallTest
    public void testCanMakeWifiCall() {
        mImsPhoneUT.setServiceState(ServiceState.STATE_IN_SERVICE);
        mImsPhoneUT.setImsRegistered(true);
        doReturn(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN).when(mImsCT)
                .getImsRegistrationTech();

        assertTrue(mImsPhoneUT.canMakeWifiCall());
    }

    private ServiceState getServiceStateDataAndVoice(int rat, int regState, boolean isRoaming) {
        ServiceState ss = new ServiceState();
        ss.setStateOutOfService();
        ss.setDataRegState(regState);
        ss.setDataRoaming(isRoaming);
        ss.setRilDataRadioTechnology(rat);
        ss.setVoiceRegState(regState);
        ss.setVoiceRoaming(isRoaming);
        ss.setRilVoiceRadioTechnology(rat);
        return ss;
    }

    private ServiceState getServiceStateDataOnly(int rat, int regState, boolean isRoaming) {
        ServiceState ss = new ServiceState();
        ss.setStateOutOfService();
        ss.setDataRegState(regState);
        ss.setDataRoaming(isRoaming);
        ss.setRilDataRadioTechnology(rat);
        return ss;
    }

    private ServiceState modifyServiceStateData(ServiceState ss, int rat, int regState,
            boolean isRoaming) {
        ss.setStateOutOfService();
        ss.setDataRegState(regState);
        ss.setDataRoaming(isRoaming);
        ss.setRilDataRadioTechnology(rat);
        return ss;
    }

    private ServiceState getServiceStateOutOfService() {
        ServiceState ss = new ServiceState();
        ss.setStateOutOfService();
        return ss;
    }

    private Message getServiceStateChangedMessage(ServiceState ss) {
        Message m = Message.obtain(mImsPhoneUT.getHandler(), ImsPhone.EVENT_SERVICE_STATE_CHANGED);
        m.obj = AsyncResult.forMessage(m, ss, null);
        return m;
    }
}

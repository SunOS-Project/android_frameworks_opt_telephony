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

package com.android.internal.telephony.data;

import android.annotation.NonNull;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.net.NetworkScore;
import android.net.TelephonyNetworkSpecifier;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.metrics.NetworkRequestsStats;
import com.android.internal.util.IndentingPrintWriter;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Telephony network factory is responsible for dispatching network requests from the connectivity
 * service to the data network controller.
 */
public class TelephonyNetworkFactory extends NetworkFactory {
    protected String LOG_TAG;
    protected static final boolean DBG = true;

    private static final int REQUEST_LOG_SIZE = 256;

    protected static final int ACTION_NO_OP   = 0;
    protected static final int ACTION_REQUEST = 1;
    protected static final int ACTION_RELEASE = 2;

    private static final int TELEPHONY_NETWORK_SCORE = 50;

    @VisibleForTesting
    public static final int EVENT_ACTIVE_PHONE_SWITCH               = 1;
    @VisibleForTesting
    public static final int EVENT_SUBSCRIPTION_CHANGED              = 2;
    private static final int EVENT_NETWORK_REQUEST                  = 3;
    private static final int EVENT_NETWORK_RELEASE                  = 4;

    protected final PhoneSwitcher mPhoneSwitcher;
    private final LocalLog mLocalLog = new LocalLog(REQUEST_LOG_SIZE);

    // Key: network request. Value: the transport of the network request applies to,
    // AccessNetworkConstants.TRANSPORT_TYPE_INVALID if not applied.
    protected final Map<TelephonyNetworkRequest, Integer> mNetworkRequests = new HashMap<>();

    protected final Phone mPhone;

    private final AccessNetworksManager mAccessNetworksManager;

    protected int mSubscriptionId;

    @VisibleForTesting
    public Handler mInternalHandler;

    @NonNull
    private final FeatureFlags mFlags;

    /**
     * Constructor
     *
     * @param looper The looper for the handler
     * @param phone The phone instance
     * @param featureFlags The feature flags
     */
    public TelephonyNetworkFactory(@NonNull Looper looper, @NonNull Phone phone, PhoneSwitcher phoneSwitcher,
            @NonNull FeatureFlags featureFlags) {
        super(looper, phone.getContext(), "TelephonyNetworkFactory[" + phone.getPhoneId()
                + "]", null);
        mPhone = phone;
        mFlags = featureFlags;
        mInternalHandler = new InternalHandler(looper);

        mAccessNetworksManager = mPhone.getAccessNetworksManager();

        setCapabilityFilter(makeNetworkFilterByPhoneId(mPhone.getPhoneId()));
        setScoreFilter(TELEPHONY_NETWORK_SCORE);

        mPhoneSwitcher = phoneSwitcher;
        LOG_TAG = "TelephonyNetworkFactory[" + mPhone.getPhoneId() + "]";

        mPhoneSwitcher.registerForActivePhoneSwitch(mInternalHandler, EVENT_ACTIVE_PHONE_SWITCH,
                null);

        mSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        SubscriptionManager.OnSubscriptionsChangedListener subscriptionsChangedListener =
                new SubscriptionManager.OnSubscriptionsChangedListener() {
                    @Override
                    public void onSubscriptionsChanged() {
                        mInternalHandler.sendEmptyMessage(EVENT_SUBSCRIPTION_CHANGED);
                    }};

        mPhone.getContext().getSystemService(SubscriptionManager.class)
                .addOnSubscriptionsChangedListener(subscriptionsChangedListener);

        register();
    }

    private NetworkCapabilities makeNetworkFilterByPhoneId(int phoneId) {
        return makeNetworkFilter(SubscriptionManager.getSubscriptionId(phoneId));
    }

    /**
     * Build the network request filter used by this factory.
     * @param subscriptionId the subscription ID to listen to
     * @return the filter to send to the system server
     */
    // This is used by the test to simulate the behavior of the system server, which is to
    // send requests that match the network filter.
    @VisibleForTesting
    public NetworkCapabilities makeNetworkFilter(int subscriptionId) {
        final NetworkCapabilities.Builder builder = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IA)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMTEL)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .addEnterpriseId(NetworkCapabilities.NET_ENTERPRISE_ID_1)
                .addEnterpriseId(NetworkCapabilities.NET_ENTERPRISE_ID_2)
                .addEnterpriseId(NetworkCapabilities.NET_ENTERPRISE_ID_3)
                .addEnterpriseId(NetworkCapabilities.NET_ENTERPRISE_ID_4)
                .addEnterpriseId(NetworkCapabilities.NET_ENTERPRISE_ID_5)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                                .setSubscriptionId(subscriptionId).build());
        TelephonyNetworkRequest.getAllSupportedNetworkCapabilities()
                .forEach(builder::addCapability);

        return builder.build();
    }

    protected class InternalHandler extends Handler {
        protected InternalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_ACTIVE_PHONE_SWITCH: {
                    onActivePhoneSwitch();
                    break;
                }
                case EVENT_SUBSCRIPTION_CHANGED: {
                    onSubIdChange();
                    break;
                }
                case EVENT_NETWORK_REQUEST: {
                    onNeedNetworkFor(msg);
                    break;
                }
                case EVENT_NETWORK_RELEASE: {
                    onReleaseNetworkFor(msg);
                    break;
                }
            }
        }
    }

    protected int getTransportTypeFromNetworkRequest(TelephonyNetworkRequest networkRequest) {
        int transport = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
        int capability = networkRequest.getHighestPriorityApnTypeNetworkCapability();
        if (capability >= 0) {
            transport = mAccessNetworksManager
                    .getPreferredTransportByNetworkCapability(capability);
        }
        return transport;
    }

    protected static int getAction(boolean wasActive, boolean isActive) {
        if (!wasActive && isActive) {
            return ACTION_REQUEST;
        } else if (wasActive && !isActive) {
            return ACTION_RELEASE;
        } else {
            return ACTION_NO_OP;
        }
    }

    // apply or revoke requests if our active-ness changes
    private void onActivePhoneSwitch() {
        logl("onActivePhoneSwitch");

        if (mSubscriptionId == SubscriptionManager.from(mPhone.getContext())
                .getActiveDataSubscriptionId()) {
            logl("onActivePhoneSwitch: set primary flag for phoneId: " + mPhone.getPhoneId());
            setScoreFilter(new NetworkScore.Builder().setLegacyInt(TELEPHONY_NETWORK_SCORE)
                    .setTransportPrimary(true).build());
        } else {
            setScoreFilter(new NetworkScore.Builder().setLegacyInt(TELEPHONY_NETWORK_SCORE)
                    .setTransportPrimary(false).build());
        }

        for (Map.Entry<TelephonyNetworkRequest, Integer> entry : mNetworkRequests.entrySet()) {
            TelephonyNetworkRequest networkRequest = entry.getKey();
            boolean applied = entry.getValue() != AccessNetworkConstants.TRANSPORT_TYPE_INVALID;

            boolean shouldApply = mPhoneSwitcher.shouldApplyNetworkRequest(
                    networkRequest, mPhone.getPhoneId());

            int action = getAction(applied, shouldApply);
            if (action == ACTION_NO_OP) continue;

            logl("onActivePhoneSwitch: " + ((action == ACTION_REQUEST)
                    ? "Requesting" : "Releasing") + " network request " + networkRequest);
            int transportType = getTransportTypeFromNetworkRequest(networkRequest);
            if (action == ACTION_REQUEST) {
                NetworkRequestsStats.addNetworkRequest(networkRequest.getNativeNetworkRequest(),
                        mSubscriptionId);
                mPhone.getDataNetworkController().addNetworkRequest(networkRequest);
            } else if (action == ACTION_RELEASE) {
                mPhone.getDataNetworkController().removeNetworkRequest(networkRequest);
            }

            mNetworkRequests.put(networkRequest,
                    shouldApply ? transportType : AccessNetworkConstants.TRANSPORT_TYPE_INVALID);
        }
    }

    // watch for phone->subId changes, reapply new filter and let
    // that flow through to apply/revoke of requests
    private void onSubIdChange() {
        int newSubscriptionId = SubscriptionManager.getSubscriptionId(mPhone.getPhoneId());
        if (mSubscriptionId != newSubscriptionId) {
            if (DBG) logl("onSubIdChange " + mSubscriptionId + "->" + newSubscriptionId);
            mSubscriptionId = newSubscriptionId;
            setCapabilityFilter(makeNetworkFilter(mSubscriptionId));
        }
    }

    @Override
    public void needNetworkFor(@NonNull NetworkRequest networkRequest) {
        Message msg = mInternalHandler.obtainMessage(EVENT_NETWORK_REQUEST);
        msg.obj = networkRequest;
        msg.sendToTarget();
    }

    private void onNeedNetworkFor(@NonNull Message msg) {
        TelephonyNetworkRequest networkRequest =
                new TelephonyNetworkRequest((NetworkRequest) msg.obj, mPhone, mFlags);
        boolean shouldApply = mPhoneSwitcher.shouldApplyNetworkRequest(
                networkRequest, mPhone.getPhoneId());

        mNetworkRequests.put(networkRequest, shouldApply
                ? getTransportTypeFromNetworkRequest(networkRequest)
                : AccessNetworkConstants.TRANSPORT_TYPE_INVALID);

        logl("onNeedNetworkFor " + networkRequest + " shouldApply " + shouldApply);

        if (shouldApply) {
            NetworkRequestsStats.addNetworkRequest(networkRequest.getNativeNetworkRequest(),
                    mSubscriptionId);
            mPhone.getDataNetworkController().addNetworkRequest(networkRequest);
        }
    }

    @Override
    public void releaseNetworkFor(@NonNull NetworkRequest networkRequest) {
        Message msg = mInternalHandler.obtainMessage(EVENT_NETWORK_RELEASE);
        msg.obj = networkRequest;
        msg.sendToTarget();
    }

    private void onReleaseNetworkFor(@NonNull Message msg) {
        TelephonyNetworkRequest networkRequest =
                new TelephonyNetworkRequest((NetworkRequest) msg.obj, mPhone, mFlags);
        if (!mNetworkRequests.containsKey(networkRequest)) {
            return;
        }
        boolean applied = mNetworkRequests.get(networkRequest)
                != AccessNetworkConstants.TRANSPORT_TYPE_INVALID;

        mNetworkRequests.remove(networkRequest);

        logl("onReleaseNetworkFor " + networkRequest + " applied " + applied);

        if (applied) {
            mPhone.getDataNetworkController().removeNetworkRequest(networkRequest);
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    protected void logl(String s) {
        log(s);
        mLocalLog.log(s);
    }

    /**
     * Dump the state of telephony network factory
     *
     * @param fd File descriptor
     * @param writer Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        pw.println("TelephonyNetworkFactory-" + mPhone.getPhoneId());
        pw.increaseIndent();
        pw.println("Network Requests:");
        pw.increaseIndent();
        for (Map.Entry<TelephonyNetworkRequest, Integer> entry : mNetworkRequests.entrySet()) {
            TelephonyNetworkRequest nr = entry.getKey();
            int transport = entry.getValue();
            pw.println(nr + (transport != AccessNetworkConstants.TRANSPORT_TYPE_INVALID
                    ? (" applied on " + transport) : " not applied"));
        }
        pw.decreaseIndent();
        pw.print("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}

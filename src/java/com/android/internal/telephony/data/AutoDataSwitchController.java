/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.telephony.SubscriptionManager.DEFAULT_PHONE_INDEX;
import static android.telephony.SubscriptionManager.INVALID_PHONE_INDEX;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkCapabilities;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkRegistrationInfo.RegistrationState;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyDisplayInfo;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.data.DataConfigManager.DataConfigManagerCallback;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.flags.FeatureFlagsImpl;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.internal.telephony.util.NotificationChannelController;
import com.android.telephony.Rlog;


import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Recommend a data phone to use based on its availability.
 */
public class AutoDataSwitchController extends Handler {
    /** Registration state changed. */
    public static final int EVALUATION_REASON_REGISTRATION_STATE_CHANGED = 1;
    /** Telephony Display Info changed. */
    public static final int EVALUATION_REASON_DISPLAY_INFO_CHANGED = 2;
    /** Signal Strength changed. */
    public static final int EVALUATION_REASON_SIGNAL_STRENGTH_CHANGED = 3;
    /** Default network capabilities changed or lost. */
    public static final int EVALUATION_REASON_DEFAULT_NETWORK_CHANGED = 4;
    /** Data enabled settings changed. */
    public static final int EVALUATION_REASON_DATA_SETTINGS_CHANGED = 5;
    /** Retry due to previous validation failed. */
    public static final int EVALUATION_REASON_RETRY_VALIDATION = 6;
    /** Sim loaded which means slot mapping became available. */
    public static final int EVALUATION_REASON_SIM_LOADED = 7;
    /** Voice call ended. */
    public static final int EVALUATION_REASON_VOICE_CALL_END = 8;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "EVALUATION_REASON_",
            value = {EVALUATION_REASON_REGISTRATION_STATE_CHANGED,
                    EVALUATION_REASON_DISPLAY_INFO_CHANGED,
                    EVALUATION_REASON_SIGNAL_STRENGTH_CHANGED,
                    EVALUATION_REASON_DEFAULT_NETWORK_CHANGED,
                    EVALUATION_REASON_DATA_SETTINGS_CHANGED,
                    EVALUATION_REASON_RETRY_VALIDATION,
                    EVALUATION_REASON_SIM_LOADED,
                    EVALUATION_REASON_VOICE_CALL_END})
    public @interface AutoDataSwitchEvaluationReason {}

    private static final String LOG_TAG = "ADSC";

    /** Event for service state changed. */
    private static final int EVENT_SERVICE_STATE_CHANGED = 1;
    /** Event for display info changed. This is for getting 5G NSA or mmwave information. */
    private static final int EVENT_DISPLAY_INFO_CHANGED = 2;
    /** Event for evaluate auto data switch opportunity. */
    private static final int EVENT_EVALUATE_AUTO_SWITCH = 3;
    /** Event for signal strength changed. */
    private static final int EVENT_SIGNAL_STRENGTH_CHANGED = 4;
    /** Event indicates the switch state is stable, proceed to validation as the next step. */
    private static final int EVENT_STABILITY_CHECK_PASSED = 5;
    /** Event when subscriptions changed. */
    private static final int EVENT_SUBSCRIPTIONS_CHANGED = 6;

    /** Fragment "key" argument passed thru {@link #SETTINGS_EXTRA_SHOW_FRAGMENT_ARGUMENTS} */
    private static final String SETTINGS_EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    /**
     * When starting this activity, this extra can also be specified to supply a Bundle of arguments
     * to pass to that fragment when it is instantiated during the initial creation of the activity.
     */
    private static final String SETTINGS_EXTRA_SHOW_FRAGMENT_ARGUMENTS =
            ":settings:show_fragment_args";
    /** The resource ID of the auto data switch fragment in settings. **/
    private static final String AUTO_DATA_SWITCH_SETTING_R_ID = "auto_data_switch";
    /** Notification tag **/
    private static final String AUTO_DATA_SWITCH_NOTIFICATION_TAG = "auto_data_switch";
    /** Notification ID **/
    private static final int AUTO_DATA_SWITCH_NOTIFICATION_ID = 1;

    /**
     * The threshold of long timer, longer than or equal to which we use alarm manager to schedule
     * instead of handler.
     */
    private static final long RETRY_LONG_DELAY_TIMER_THRESHOLD_MILLIS = TimeUnit
            .MINUTES.toMillis(1);

    @NonNull
    private final LocalLog mLocalLog = new LocalLog(128);
    @NonNull
    private final Context mContext;
    @NonNull
    private static FeatureFlags sFeatureFlags = new FeatureFlagsImpl();
    @NonNull
    private final SubscriptionManagerService mSubscriptionManagerService;
    @NonNull
    private final PhoneSwitcher mPhoneSwitcher;
    @NonNull
    private final AutoDataSwitchControllerCallback mPhoneSwitcherCallback;
    @NonNull
    private final AlarmManager mAlarmManager;
    /** A map of a scheduled event to its associated extra for action when the event fires off. */
    @NonNull
    private final Map<Integer, Object> mScheduledEventsToExtras;
    /** A map of an event to its associated alarm listener callback for when the event fires off. */
    @NonNull
    private final Map<Integer, AlarmManager.OnAlarmListener> mEventsToAlarmListener;
    /**
     * Event extras for checking environment stability.
     * @param targetPhoneId The target phone Id to switch to when the stability check pass.
     * @param isForPerformance Whether the switch is due to RAT/signal strength performance.
     * @param needValidation Whether ping test needs to pass.
     */
    private record StabilityEventExtra(int targetPhoneId, boolean isForPerformance,
                               boolean needValidation) {}

    /**
     * Event extras for evaluating switch environment.
     * @param evaluateReason The reason that triggers the evaluation.
     */
    private record EvaluateEventExtra(@AutoDataSwitchEvaluationReason int evaluateReason) {}
    private boolean mDefaultNetworkIsOnNonCellular = false;
    /** {@code true} if we've displayed the notification the first time auto switch occurs **/
    private boolean mDisplayedNotification = false;
    /**
     * Configurable time threshold in ms to define an internet connection status to be stable(e.g.
     * out of service, in service, wifi is the default active network.etc), while -1 indicates auto
     * switch feature disabled.
     */
    private long mAutoDataSwitchAvailabilityStabilityTimeThreshold = -1;
    /**
     * Configurable time threshold in ms to define an internet connection performance status to be
     * stable (e.g. LTE + 4 signal strength, UMTS + 2 signal strength), while -1 indicates
     * auto switch feature based on RAT/SS is disabled.
     */
    private long mAutoDataSwitchPerformanceStabilityTimeThreshold = -1;
    /**
     * The tolerated gap of score for auto data switch decision, larger than which the device will
     * switch to the SIM with higher score. If 0, the device will always switch to the higher score
     * SIM. If < 0, the network type and signal strength based auto switch is disabled.
     */
    private int mScoreTolerance = -1;
    /**
     * {@code true} if requires ping test before switching preferred data modem; otherwise, switch
     * even if ping test fails.
     */
    private boolean mRequirePingTestBeforeSwitch = true;
    /**
     * TODO: remove after V.
     * To indicate whether allow using roaming nDDS if user enabled its roaming when the DDS is not
     * usable(OOS or disabled roaming)
     */
    private boolean mAllowNddsRoaming = true;
    /** The count of consecutive auto switch validation failure **/
    private int mAutoSwitchValidationFailedCount = 0;
    /**
     * The maximum number of retries when a validation for switching failed.
     */
    private int mAutoDataSwitchValidationMaxRetry;

    /** The signal status of phones, where index corresponds to phone Id. */
    @NonNull
    private PhoneSignalStatus[] mPhonesSignalStatus;
    /**
     * The phone Id of the pending switching phone. Used for pruning frequent switch evaluation.
     */
    private int mSelectedTargetPhoneId = INVALID_PHONE_INDEX;

    /**
     * To track the signal status of a phone in order to evaluate whether it's a good candidate to
     * switch to.
     */
    private static class PhoneSignalStatus {
        /**
         * How preferred the current phone is.
         */
        enum UsableState {
            HOME(2),
            ROAMING_ENABLED(1),
            NON_TERRESTRIAL(0),
            NOT_USABLE(-1);
            /**
             * The higher the score, the more preferred.
             * HOME is preferred over ROAMING assuming roaming is metered.
             */
            final int mScore;
            UsableState(int score) {
                this.mScore = score;
            }
        }
        /** The phone */
        @NonNull private final Phone mPhone;
        /** Data registration state of the phone */
        @RegistrationState private int mDataRegState;
        /** Current Telephony display info of the phone */
        @NonNull private TelephonyDisplayInfo mDisplayInfo;
        /** Signal strength of the phone */
        @NonNull private SignalStrength mSignalStrength;
        /** {@code true} if this slot is listening for events. */
        private boolean mListeningForEvents;
        private PhoneSignalStatus(@NonNull Phone phone) {
            this.mPhone = phone;
            this.mDataRegState = phone.getServiceState().getNetworkRegistrationInfo(
                            NetworkRegistrationInfo.DOMAIN_PS,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                    .getRegistrationState();
            this.mDisplayInfo = phone.getDisplayInfoController().getTelephonyDisplayInfo();
            this.mSignalStrength = phone.getSignalStrength();
        }

        /**
         * @return the current score of this phone. 0 indicates out of service and it will never be
         * selected as the secondary data candidate.
         */
        private int getRatSignalScore() {
            return isInService(mDataRegState)
                    ? mPhone.getDataNetworkController().getDataConfigManager()
                            .getAutoDataSwitchScore(mDisplayInfo, mSignalStrength) : 0;
        }

        /**
         * @return The current usable state of the phone.
         */
        private UsableState getUsableState() {
            ServiceState serviceState = mPhone.getServiceState();
            boolean isUsingNonTerrestrialNetwork = sFeatureFlags.carrierEnabledSatelliteFlag()
                    && (serviceState != null) && serviceState.isUsingNonTerrestrialNetwork();

            return switch (mDataRegState) {
                case NetworkRegistrationInfo.REGISTRATION_STATE_HOME -> {
                    if (isUsingNonTerrestrialNetwork) {
                        yield UsableState.NON_TERRESTRIAL;
                    }
                    yield UsableState.HOME;
                }
                case NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING -> {
                    if (mPhone.getDataRoamingEnabled()) {
                        if (isUsingNonTerrestrialNetwork) {
                            yield UsableState.NON_TERRESTRIAL;
                        }
                        yield UsableState.ROAMING_ENABLED;
                    }
                    yield UsableState.NOT_USABLE;
                }
                default -> UsableState.NOT_USABLE;
            };
        }

        @Override
        public String toString() {
            return "{phone " + mPhone.getPhoneId()
                    + " score=" + getRatSignalScore() + " dataRegState="
                    + NetworkRegistrationInfo.registrationStateToString(mDataRegState)
                    + " " + getUsableState() + " " + mDisplayInfo
                    + " signalStrength=" + mSignalStrength.getLevel()
                    + " listeningForEvents=" + mListeningForEvents
                    + "}";

        }
    }

    /**
     * This is the callback used for listening events from {@link AutoDataSwitchController}.
     */
    public abstract static class AutoDataSwitchControllerCallback {
        /**
         * Called when a target data phone is recommended by the controller.
         * @param targetPhoneId The target phone Id.
         * @param needValidation {@code true} if need a ping test to pass before switching.
         */
        public abstract void onRequireValidation(int targetPhoneId, boolean needValidation);

        /**
         * Called when a target data phone is demanded by the controller.
         * @param targetPhoneId The target phone Id.
         * @param reason The reason for the demand.
         */
        public abstract void onRequireImmediatelySwitchToPhone(int targetPhoneId,
                @AutoDataSwitchEvaluationReason int reason);

        /**
         * Called when the controller asks to cancel any pending validation attempts because the
         * environment is no longer suited for switching.
         */
        public abstract void onRequireCancelAnyPendingAutoSwitchValidation();
    }

    /**
     * @param context Context.
     * @param looper Main looper.
     * @param phoneSwitcher Phone switcher.
     * @param phoneSwitcherCallback Callback for phone switcher to execute.
     */
    public AutoDataSwitchController(@NonNull Context context, @NonNull Looper looper,
            @NonNull PhoneSwitcher phoneSwitcher, @NonNull FeatureFlags featureFlags,
            @NonNull AutoDataSwitchControllerCallback phoneSwitcherCallback) {
        super(looper);
        mContext = context;
        sFeatureFlags = featureFlags;
        mPhoneSwitcherCallback = phoneSwitcherCallback;
        mAlarmManager = context.getSystemService(AlarmManager.class);
        mScheduledEventsToExtras = new HashMap<>();
        mEventsToAlarmListener = new HashMap<>();
        mSubscriptionManagerService = SubscriptionManagerService.getInstance();
        mPhoneSwitcher = phoneSwitcher;
        readDeviceResourceConfig();
        int numActiveModems = PhoneFactory.getPhones().length;
        mPhonesSignalStatus = new PhoneSignalStatus[numActiveModems];
        // Listening on all slots on boot up to make sure nothing missed. Later the tracking is
        // pruned upon subscriptions changed.
        for (int phoneId = 0; phoneId < numActiveModems; phoneId++) {
            registerAllEventsForPhone(phoneId);
        }
        registerForCarrierConfigChanged();
    }

    /**
     * Called when active modem count changed, update all tracking events.
     * @param numActiveModems The current number of active modems.
     */
    public synchronized void onMultiSimConfigChanged(int numActiveModems) {
        int oldActiveModems = mPhonesSignalStatus.length;
        if (oldActiveModems == numActiveModems) return;
        // Dual -> Single
        for (int phoneId = numActiveModems; phoneId < oldActiveModems; phoneId++) {
            unregisterAllEventsForPhone(phoneId);
        }
        mPhonesSignalStatus = Arrays.copyOf(mPhonesSignalStatus, numActiveModems);
        // Signal -> Dual
        for (int phoneId = oldActiveModems; phoneId < numActiveModems; phoneId++) {
            registerAllEventsForPhone(phoneId);
        }
        logl("onMultiSimConfigChanged: " + Arrays.toString(mPhonesSignalStatus));
    }

    /** Notify subscriptions changed. */
    public void notifySubscriptionsMappingChanged() {
        sendEmptyMessage(EVENT_SUBSCRIPTIONS_CHANGED);
    }

    /**
     * On subscription changed, register/unregister events on phone Id slot that has active/inactive
     * sub to reduce unnecessary tracking.
     */
    private void onSubscriptionsChanged() {
        Set<Integer> activePhoneIds = Arrays.stream(mSubscriptionManagerService
                .getActiveSubIdList(true /*visibleOnly*/))
                .map(mSubscriptionManagerService::getPhoneId)
                .boxed()
                .collect(Collectors.toSet());
        // Track events only if there are at least two active visible subscriptions.
        if (activePhoneIds.size() < 2) activePhoneIds.clear();
        boolean changed = false;
        for (int phoneId = 0; phoneId < mPhonesSignalStatus.length; phoneId++) {
            if (activePhoneIds.contains(phoneId)
                    && !mPhonesSignalStatus[phoneId].mListeningForEvents) {
                registerAllEventsForPhone(phoneId);
                changed = true;
            } else if (!activePhoneIds.contains(phoneId)
                    && mPhonesSignalStatus[phoneId].mListeningForEvents) {
                unregisterAllEventsForPhone(phoneId);
                changed = true;
            }
        }
        if (changed) logl("onSubscriptionChanged: " + Arrays.toString(mPhonesSignalStatus));
    }

    private void registerForCarrierConfigChanged() {
        Phone phone = PhoneFactory.getDefaultPhone();
        DataConfigManager dataConfigManager =
                phone.getDataNetworkController().getDataConfigManager();
        dataConfigManager.registerCallback(new DataConfigManagerCallback(this::post) {
                @Override
                public void onCarrierConfigChanged() {
                    readDeviceResourceConfig();
                }
            });
    }

    /**
     * Register all tracking events for a phone.
     * @param phoneId The phone to register for all events.
     */
    private void registerAllEventsForPhone(int phoneId) {
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null && isActiveModemPhone(phoneId)) {
            mPhonesSignalStatus[phoneId] = new PhoneSignalStatus(phone);
            phone.getDisplayInfoController().registerForTelephonyDisplayInfoChanged(
                    this, EVENT_DISPLAY_INFO_CHANGED, phoneId);
            phone.getSignalStrengthController().registerForSignalStrengthChanged(
                    this, EVENT_SIGNAL_STRENGTH_CHANGED, phoneId);
            phone.getServiceStateTracker().registerForServiceStateChanged(this,
                    EVENT_SERVICE_STATE_CHANGED, phoneId);
            mPhonesSignalStatus[phoneId].mListeningForEvents = true;
        } else {
            loge("Unexpected null phone " + phoneId + " when register all events");
        }
    }

    /**
     * Unregister all tracking events for a phone.
     * @param phoneId The phone to unregister for all events.
     */
    private void unregisterAllEventsForPhone(int phoneId) {
        if (isActiveModemPhone(phoneId)) {
            Phone phone = mPhonesSignalStatus[phoneId].mPhone;
            phone.getDisplayInfoController().unregisterForTelephonyDisplayInfoChanged(this);
            phone.getSignalStrengthController().unregisterForSignalStrengthChanged(this);
            phone.getServiceStateTracker().unregisterForServiceStateChanged(this);
            mPhonesSignalStatus[phoneId].mListeningForEvents = false;
        } else {
            loge("Unexpected out of bound phone " + phoneId + " when unregister all events");
        }
    }

    /**
     * Read the default device config from any default phone because the resource config are per
     * device. No need to register callback for the same reason.
     */
    private void readDeviceResourceConfig() {
        Phone phone = PhoneFactory.getDefaultPhone();
        DataConfigManager dataConfig = phone.getDataNetworkController().getDataConfigManager();
        mScoreTolerance =  dataConfig.getAutoDataSwitchScoreTolerance();
        mRequirePingTestBeforeSwitch = dataConfig.isPingTestBeforeAutoDataSwitchRequired();
        mAllowNddsRoaming = dataConfig.doesAutoDataSwitchAllowRoaming();
        mAutoDataSwitchAvailabilityStabilityTimeThreshold =
                dataConfig.getAutoDataSwitchAvailabilityStabilityTimeThreshold();
        mAutoDataSwitchPerformanceStabilityTimeThreshold =
                dataConfig.getAutoDataSwitchPerformanceStabilityTimeThreshold();
        mAutoDataSwitchValidationMaxRetry =
                dataConfig.getAutoDataSwitchValidationMaxRetry();
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        AsyncResult ar;
        Object obj;
        int phoneId;
        switch (msg.what) {
            case EVENT_SERVICE_STATE_CHANGED:
                ar = (AsyncResult) msg.obj;
                phoneId = (int) ar.userObj;
                onServiceStateChanged(phoneId);
                break;
            case EVENT_DISPLAY_INFO_CHANGED:
                ar = (AsyncResult) msg.obj;
                phoneId = (int) ar.userObj;
                onDisplayInfoChanged(phoneId);
                break;
            case EVENT_SIGNAL_STRENGTH_CHANGED:
                ar = (AsyncResult) msg.obj;
                phoneId = (int) ar.userObj;
                onSignalStrengthChanged(phoneId);
                break;
            case EVENT_EVALUATE_AUTO_SWITCH:
                obj = mScheduledEventsToExtras.get(EVENT_EVALUATE_AUTO_SWITCH);
                if (obj instanceof EvaluateEventExtra extra) {
                    mScheduledEventsToExtras.remove(EVENT_EVALUATE_AUTO_SWITCH);
                    onEvaluateAutoDataSwitch(extra.evaluateReason);
                }
                break;
            case EVENT_STABILITY_CHECK_PASSED:
                obj = mScheduledEventsToExtras.get(EVENT_STABILITY_CHECK_PASSED);
                if (obj instanceof StabilityEventExtra extra) {
                    int targetPhoneId = extra.targetPhoneId;
                    boolean needValidation = extra.needValidation;
                    log("require validation on phone " + targetPhoneId
                            + (needValidation ? "" : " no") + " need to pass");
                    mScheduledEventsToExtras.remove(EVENT_STABILITY_CHECK_PASSED);
                    mPhoneSwitcherCallback.onRequireValidation(targetPhoneId, needValidation);
                }
                break;
            case EVENT_SUBSCRIPTIONS_CHANGED:
                onSubscriptionsChanged();
                break;
            default:
                loge("Unexpected event " + msg.what);
        }
    }

    /**
     * Called when registration state changed.
     */
    private void onServiceStateChanged(int phoneId) {
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null && isActiveModemPhone(phoneId)) {
            int oldRegState = mPhonesSignalStatus[phoneId].mDataRegState;
            int newRegState = phone.getServiceState()
                    .getNetworkRegistrationInfo(
                            NetworkRegistrationInfo.DOMAIN_PS,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                    .getRegistrationState();
            if (newRegState != oldRegState) {
                mPhonesSignalStatus[phoneId].mDataRegState = newRegState;
                if (isInService(oldRegState) != isInService(newRegState)
                        || isHomeService(oldRegState) != isHomeService(newRegState)) {
                    log("onServiceStateChanged: phone " + phoneId + " "
                            + NetworkRegistrationInfo.registrationStateToString(oldRegState)
                            + " -> "
                            + NetworkRegistrationInfo.registrationStateToString(newRegState));
                    evaluateAutoDataSwitch(EVALUATION_REASON_REGISTRATION_STATE_CHANGED);
                }
            }
        } else {
            loge("Unexpected null phone " + phoneId + " upon its registration state changed");
        }
    }

    /** @return {@code true} if the phone state is considered in service. */
    private static boolean isInService(@RegistrationState int dataRegState) {
        return dataRegState == NetworkRegistrationInfo.REGISTRATION_STATE_HOME
                || dataRegState == NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING;
    }

    /** @return {@code true} if the phone state is in home service. */
    private static boolean isHomeService(@RegistrationState int dataRegState) {
        return dataRegState == NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
    }

    /**
     * Called when {@link TelephonyDisplayInfo} changed. This can happen when network types or
     * override network types (5G NSA, 5G MMWAVE) change.
     * @param phoneId The phone that changed.
     */
    private void onDisplayInfoChanged(int phoneId) {
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null && isActiveModemPhone(phoneId)) {
            TelephonyDisplayInfo displayInfo = phone.getDisplayInfoController()
                    .getTelephonyDisplayInfo();
            mPhonesSignalStatus[phoneId].mDisplayInfo = displayInfo;
            if (getHigherScoreCandidatePhoneId() != mSelectedTargetPhoneId) {
                log("onDisplayInfoChanged: phone " + phoneId + " " + displayInfo);
                evaluateAutoDataSwitch(EVALUATION_REASON_DISPLAY_INFO_CHANGED);
            }
        } else {
            loge("Unexpected null phone " + phoneId + " upon its display info changed");
        }
    }

    /**
     * Called when {@link SignalStrength} changed.
     * @param phoneId The phone that changed.
     */
    private void onSignalStrengthChanged(int phoneId) {
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null && isActiveModemPhone(phoneId)) {
            SignalStrength newSignalStrength = phone.getSignalStrength();
            SignalStrength oldSignalStrength = mPhonesSignalStatus[phoneId].mSignalStrength;
            if (oldSignalStrength.getLevel() != newSignalStrength.getLevel()) {
                mPhonesSignalStatus[phoneId].mSignalStrength = newSignalStrength;
                if (getHigherScoreCandidatePhoneId() != mSelectedTargetPhoneId) {
                    log("onSignalStrengthChanged: phone " + phoneId + " "
                            + oldSignalStrength.getLevel() + "->" + newSignalStrength.getLevel());
                    evaluateAutoDataSwitch(EVALUATION_REASON_SIGNAL_STRENGTH_CHANGED);
                }
            }
        } else {
            loge("Unexpected null phone " + phoneId + " upon its signal strength changed");
        }
    }

    /**
     * Called as a preliminary check for the frequent signal/display info change.
     * @return The phone Id if found a candidate phone with higher signal score, or the DDS has
     * an equal score.
     */
    private int getHigherScoreCandidatePhoneId() {
        int preferredPhoneId = mPhoneSwitcher.getPreferredDataPhoneId();
        int ddsPhoneId = mSubscriptionManagerService.getPhoneId(
                mSubscriptionManagerService.getDefaultDataSubId());
        if (isActiveModemPhone(preferredPhoneId) && isActiveModemPhone(ddsPhoneId)) {
            int currentScore = mPhonesSignalStatus[preferredPhoneId].getRatSignalScore();
            for (int phoneId = 0; phoneId < mPhonesSignalStatus.length; phoneId++) {
                if (phoneId == preferredPhoneId) continue;
                int candidateScore = mPhonesSignalStatus[phoneId].getRatSignalScore();
                if ((candidateScore - currentScore) > mScoreTolerance
                        // Also reevaluate if DDS has the same score as the current phone.
                        || (candidateScore >= currentScore && phoneId == ddsPhoneId)) {
                    return phoneId;
                }
            }
        }
        return INVALID_PHONE_INDEX;
    }

    /**
     * Schedule for auto data switch evaluation.
     * @param reason The reason for the evaluation.
     */
    public void evaluateAutoDataSwitch(@AutoDataSwitchEvaluationReason int reason) {
        long delayMs = reason == EVALUATION_REASON_RETRY_VALIDATION
                ? mAutoDataSwitchAvailabilityStabilityTimeThreshold
                << mAutoSwitchValidationFailedCount
                : 0;
        if (!mScheduledEventsToExtras.containsKey(EVENT_EVALUATE_AUTO_SWITCH)) {
            scheduleEventWithTimer(EVENT_EVALUATE_AUTO_SWITCH, new EvaluateEventExtra(reason),
                    delayMs);
        }
    }

    /**
     * Evaluate for auto data switch opportunity.
     * If suitable to switch, check that the suitable state is stable(or switch immediately if user
     * turned off settings).
     * @param reason The reason for the evaluation.
     */
    private void onEvaluateAutoDataSwitch(@AutoDataSwitchEvaluationReason int reason) {
        // auto data switch feature is disabled.
        if (mAutoDataSwitchAvailabilityStabilityTimeThreshold < 0) return;
        int defaultDataSubId = mSubscriptionManagerService.getDefaultDataSubId();
        // check is valid DSDS
        if (mSubscriptionManagerService.getActiveSubIdList(true).length < 2) return;
        int defaultDataPhoneId = mSubscriptionManagerService.getPhoneId(
                defaultDataSubId);
        Phone defaultDataPhone = PhoneFactory.getPhone(defaultDataPhoneId);
        if (defaultDataPhone == null) {
            loge("onEvaluateAutoDataSwitch: cannot find the phone associated with default data"
                    + " subscription " + defaultDataSubId);
            return;
        }

        int preferredPhoneId = mPhoneSwitcher.getPreferredDataPhoneId();
        StringBuilder debugMessage = new StringBuilder("onEvaluateAutoDataSwitch:");
        debugMessage.append(" defaultPhoneId: ").append(defaultDataPhoneId)
                .append(" preferredPhoneId: ").append(preferredPhoneId)
                .append(", reason: ").append(evaluationReasonToString(reason));
        if (preferredPhoneId == defaultDataPhoneId) {
            // on default data sub
            StabilityEventExtra res = evaluateAnyCandidateToUse(defaultDataPhoneId, debugMessage);
            log(debugMessage.toString());
            if (res.targetPhoneId != INVALID_PHONE_INDEX) {
                mSelectedTargetPhoneId = res.targetPhoneId;
                startStabilityCheck(res.targetPhoneId, res.isForPerformance, res.needValidation);
            } else {
                cancelAnyPendingSwitch();
            }
        } else {
            // on backup data sub
            Phone backupDataPhone = PhoneFactory.getPhone(preferredPhoneId);
            if (backupDataPhone == null || !isActiveModemPhone(preferredPhoneId)) {
                loge(debugMessage.append(" Unexpected null phone ").append(preferredPhoneId)
                        .append(" as the current active data phone").toString());
                return;
            }

            DataEvaluation internetEvaluation;
            if (sFeatureFlags.autoDataSwitchUsesDataEnabled()) {
                if (!defaultDataPhone.isUserDataEnabled()) {
                    mPhoneSwitcherCallback.onRequireImmediatelySwitchToPhone(DEFAULT_PHONE_INDEX,
                            EVALUATION_REASON_DATA_SETTINGS_CHANGED);
                    log(debugMessage.append(
                            ", immediately back to default as user turns off default").toString());
                    return;
                } else if (!(internetEvaluation = backupDataPhone.getDataNetworkController()
                        .getInternetEvaluation(false/*ignoreExistingNetworks*/))
                        .isSubsetOf(DataEvaluation.DataDisallowedReason.NOT_IN_SERVICE)) {
                    mPhoneSwitcherCallback.onRequireImmediatelySwitchToPhone(
                            DEFAULT_PHONE_INDEX, EVALUATION_REASON_DATA_SETTINGS_CHANGED);
                    log(debugMessage.append(
                                    ", immediately back to default because backup ")
                            .append(internetEvaluation).toString());
                    return;
                }
            } else {
                if (!defaultDataPhone.isUserDataEnabled() || !backupDataPhone.isDataAllowed()) {
                    mPhoneSwitcherCallback.onRequireImmediatelySwitchToPhone(DEFAULT_PHONE_INDEX,
                            EVALUATION_REASON_DATA_SETTINGS_CHANGED);
                    log(debugMessage.append(
                            ", immediately back to default as user turns off settings").toString());
                    return;
                }
            }

            boolean backToDefault = false;
            boolean isForPerformance = false;
            boolean needValidation = true;

            if (isNddsRoamingEnabled()) {
                if (mDefaultNetworkIsOnNonCellular) {
                    debugMessage.append(", back to default as default network")
                            .append(" is active on nonCellular transport");
                    backToDefault = true;
                    needValidation = false;
                } else {
                    PhoneSignalStatus.UsableState defaultUsableState =
                            mPhonesSignalStatus[defaultDataPhoneId].getUsableState();
                    PhoneSignalStatus.UsableState currentUsableState =
                            mPhonesSignalStatus[preferredPhoneId].getUsableState();

                    boolean isCurrentUsable = currentUsableState.mScore
                            > PhoneSignalStatus.UsableState.NOT_USABLE.mScore;

                    if (currentUsableState.mScore < defaultUsableState.mScore) {
                        debugMessage.append(", back to default phone ").append(preferredPhoneId)
                                .append(" : ").append(defaultUsableState)
                                .append(" , backup phone: ").append(currentUsableState);

                        backToDefault = true;
                        // Require validation if the current preferred phone is usable.
                        needValidation = isCurrentUsable && mRequirePingTestBeforeSwitch;
                    } else if (defaultUsableState.mScore == currentUsableState.mScore) {
                        debugMessage.append(", default phone ").append(preferredPhoneId)
                                .append(" : ").append(defaultUsableState)
                                .append(" , backup phone: ").append(currentUsableState);

                        if (isCurrentUsable) {
                            // Both phones are usable.
                            if (isRatSignalStrengthBasedSwitchEnabled()) {
                                int defaultScore = mPhonesSignalStatus[defaultDataPhoneId]
                                        .getRatSignalScore();
                                int currentScore = mPhonesSignalStatus[preferredPhoneId]
                                        .getRatSignalScore();
                                if (defaultScore >= currentScore) {
                                    debugMessage
                                            .append(", back to default for higher or equal score ")
                                            .append(defaultScore).append(" versus current ")
                                            .append(currentScore);
                                    backToDefault = true;
                                    isForPerformance = true;
                                    needValidation = mRequirePingTestBeforeSwitch;
                                }
                            } else {
                                // Only OOS/in service switch is enabled, switch back.
                                debugMessage.append(", back to default as it's usable. ");
                                backToDefault = true;
                                needValidation = mRequirePingTestBeforeSwitch;
                            }
                        } else {
                            debugMessage.append(", back to default as both phones are unusable.");
                            backToDefault = true;
                            needValidation = false;
                        }
                    }
                }
            } else {
                if (mDefaultNetworkIsOnNonCellular) {
                    debugMessage.append(", back to default as default network")
                            .append(" is active on nonCellular transport");
                    backToDefault = true;
                    needValidation = false;
                } else if (!isHomeService(mPhonesSignalStatus[preferredPhoneId].mDataRegState)) {
                    debugMessage.append(", back to default as backup phone lost HOME registration");
                    backToDefault = true;
                    needValidation = false;
                } else if (isRatSignalStrengthBasedSwitchEnabled()) {
                    int defaultScore = mPhonesSignalStatus[defaultDataPhoneId].getRatSignalScore();
                    int currentScore = mPhonesSignalStatus[preferredPhoneId].getRatSignalScore();
                    if (defaultScore >= currentScore) {
                        debugMessage
                                .append(", back to default as default has higher or equal score ")
                                .append(defaultScore).append(" versus current ")
                                .append(currentScore);
                        backToDefault = true;
                        isForPerformance = true;
                        needValidation = mRequirePingTestBeforeSwitch;
                    }
                } else if (isInService(mPhonesSignalStatus[defaultDataPhoneId].mDataRegState)) {
                    debugMessage.append(", back to default as the default is back to service ");
                    backToDefault = true;
                    needValidation = mRequirePingTestBeforeSwitch;
                }
            }

            if (backToDefault) {
                log(debugMessage.toString());
                mSelectedTargetPhoneId = defaultDataPhoneId;
                startStabilityCheck(DEFAULT_PHONE_INDEX, isForPerformance, needValidation);
            } else {
                // cancel any previous attempts of switching back to default phone
                cancelAnyPendingSwitch();
            }
        }
    }

    /**
     * Called when consider switching from primary default data sub to another data sub.
     * @param defaultPhoneId The default data phone
     * @param debugMessage Debug message.
     * @return StabilityEventExtra As evaluation result.
     */
    @NonNull private StabilityEventExtra evaluateAnyCandidateToUse(int defaultPhoneId,
            @NonNull StringBuilder debugMessage) {
        Phone defaultDataPhone = PhoneFactory.getPhone(defaultPhoneId);
        boolean isForPerformance = false;
        StabilityEventExtra invalidResult = new StabilityEventExtra(INVALID_PHONE_INDEX,
                isForPerformance, mRequirePingTestBeforeSwitch);

        if (defaultDataPhone == null) {
            debugMessage.append(", no candidate as no sim loaded");
            return invalidResult;
        }

        if (!defaultDataPhone.isUserDataEnabled()) {
            debugMessage.append(", no candidate as user disabled mobile data");
            return invalidResult;
        }

        if (mDefaultNetworkIsOnNonCellular) {
            debugMessage.append(", no candidate as default network is active")
                    .append(" on non-cellular transport");
            return invalidResult;
        }

        if (isNddsRoamingEnabled()) {
            // check whether primary and secondary signal status are worth switching
            if (!isRatSignalStrengthBasedSwitchEnabled()
                    && isHomeService(mPhonesSignalStatus[defaultPhoneId].mDataRegState)) {
                debugMessage.append(", no candidate as default phone is in HOME service");
                return invalidResult;
            }
        } else {
            // check whether primary and secondary signal status are worth switching
            if (!isRatSignalStrengthBasedSwitchEnabled()
                    && isInService(mPhonesSignalStatus[defaultPhoneId].mDataRegState)) {
                debugMessage.append(", no candidate as default phone is in service");
                return invalidResult;
            }
        }

        PhoneSignalStatus defaultPhoneStatus = mPhonesSignalStatus[defaultPhoneId];
        for (int phoneId = 0; phoneId < mPhonesSignalStatus.length; phoneId++) {
            if (phoneId == defaultPhoneId) continue;

            Phone secondaryDataPhone = null;
            PhoneSignalStatus candidatePhoneStatus = mPhonesSignalStatus[phoneId];
            if (isNddsRoamingEnabled()) {
                PhoneSignalStatus.UsableState currentUsableState =
                        mPhonesSignalStatus[defaultPhoneId].getUsableState();
                PhoneSignalStatus.UsableState candidateUsableState =
                        mPhonesSignalStatus[phoneId].getUsableState();
                debugMessage.append(", found phone ").append(phoneId).append(" ")
                        .append(candidateUsableState)
                        .append(", default is ").append(currentUsableState);
                if (candidateUsableState.mScore > currentUsableState.mScore) {
                    secondaryDataPhone = PhoneFactory.getPhone(phoneId);
                } else if (isRatSignalStrengthBasedSwitchEnabled()
                        && currentUsableState.mScore == candidateUsableState.mScore) {
                    // Both phones are home or both roaming enabled, so compare RAT/signal score.

                    int defaultScore = defaultPhoneStatus.getRatSignalScore();
                    int candidateScore = candidatePhoneStatus.getRatSignalScore();
                    if ((candidateScore - defaultScore) > mScoreTolerance) {
                        debugMessage.append(" with ").append(defaultScore)
                                .append(" versus candidate higher score ").append(candidateScore);
                        secondaryDataPhone = PhoneFactory.getPhone(phoneId);
                        isForPerformance = true;
                    } else {
                        debugMessage.append(", candidate's score ").append(candidateScore)
                                .append(" doesn't justify the switch given the current ")
                                .append(defaultScore);
                    }
                }
            } else if (isHomeService(candidatePhoneStatus.mDataRegState)) {
                // the alternative phone must have HOME availability
                debugMessage.append(", found phone ").append(phoneId).append(" in HOME service");

                if (isInService(defaultPhoneStatus.mDataRegState)) {
                    // Use score if RAT/signal strength based switch is enabled and both phone are
                    // in service.
                    if (isRatSignalStrengthBasedSwitchEnabled()) {
                        int defaultScore = mPhonesSignalStatus[defaultPhoneId].getRatSignalScore();
                        int candidateScore = mPhonesSignalStatus[phoneId].getRatSignalScore();
                        if ((candidateScore - defaultScore) > mScoreTolerance) {
                            debugMessage.append(" with higher score ").append(candidateScore)
                                    .append(" versus current ").append(defaultScore);
                            secondaryDataPhone = PhoneFactory.getPhone(phoneId);
                            isForPerformance = true;
                        } else {
                            debugMessage.append(", but its score ").append(candidateScore)
                                    .append(" doesn't meet the bar to switch given the current ")
                                    .append(defaultScore);
                        }
                    }
                } else {
                    // Only OOS/in service switch is enabled.
                    secondaryDataPhone = PhoneFactory.getPhone(phoneId);
                }
            }

            if (secondaryDataPhone != null) {
                // check internet data is allowed on the candidate
                DataEvaluation internetEvaluation = secondaryDataPhone.getDataNetworkController()
                        .getInternetEvaluation(false/*ignoreExistingNetworks*/);
                if (!internetEvaluation.containsDisallowedReasons()) {
                    return new StabilityEventExtra(phoneId,
                            isForPerformance, mRequirePingTestBeforeSwitch);
                } else {
                    debugMessage.append(", but candidate's data is not allowed ")
                            .append(internetEvaluation);
                }
            }
        }
        debugMessage.append(", found no qualified candidate.");
        return invalidResult;
    }

    /**
     * @return {@code true} If the feature of switching base on RAT and signal strength is enabled.
     */
    private boolean isRatSignalStrengthBasedSwitchEnabled() {
        return mScoreTolerance >= 0 && mAutoDataSwitchPerformanceStabilityTimeThreshold >= 0;
    }

    /**
     * @return {@code true} If the feature of switching to roaming non DDS is enabled.
     */
    private boolean isNddsRoamingEnabled() {
        return sFeatureFlags.autoDataSwitchAllowRoaming() && mAllowNddsRoaming;
    }

    /**
     * Called when the current environment suits auto data switch.
     * Start pre-switch validation if the current environment suits auto data switch for
     * {@link #mAutoDataSwitchAvailabilityStabilityTimeThreshold} MS.
     * @param targetPhoneId the target phone Id.
     * @param isForPerformance {@code true} entails longer stability check.
     * @param needValidation {@code true} if validation is needed.
     */
    private void startStabilityCheck(int targetPhoneId, boolean isForPerformance,
            boolean needValidation) {
        StabilityEventExtra eventExtras = (StabilityEventExtra)
                mScheduledEventsToExtras.getOrDefault(EVENT_STABILITY_CHECK_PASSED,
                        new StabilityEventExtra(INVALID_PHONE_INDEX, false /*need validation*/,
                                false /*isForPerformance*/));
        long delayMs = -1;
        // Check if already scheduled one with that combination of extras.
        if (eventExtras.targetPhoneId != targetPhoneId
                || eventExtras.needValidation != needValidation
                || eventExtras.isForPerformance != isForPerformance) {
            eventExtras =
                    new StabilityEventExtra(targetPhoneId, isForPerformance, needValidation);

            // Reset with new timer.
            delayMs = isForPerformance
                    ? mAutoDataSwitchPerformanceStabilityTimeThreshold
                    : mAutoDataSwitchAvailabilityStabilityTimeThreshold;
            scheduleEventWithTimer(EVENT_STABILITY_CHECK_PASSED, eventExtras, delayMs);
        }
        log("startStabilityCheck: "
                + (delayMs != -1 ? "scheduling " : "already scheduled ")
                + eventExtras);
    }

    /**
     * Use when need to schedule with timer. Short timer uses handler, while the longer timer uses
     * alarm manager to account for real time elapse.
     *
     * @param event The event.
     * @param extras Any extra data associated with the event.
     * @param delayMs The delayed interval in ms.
     */
    private void scheduleEventWithTimer(int event, @NonNull Object extras, long delayMs) {
        // Get singleton alarm listener.
        mEventsToAlarmListener.putIfAbsent(event, () -> sendEmptyMessage(event));
        AlarmManager.OnAlarmListener listener = mEventsToAlarmListener.get(event);

        // Cancel any existing.
        removeMessages(event);
        mAlarmManager.cancel(listener);
        // Override with new extras.
        mScheduledEventsToExtras.put(event, extras);
        // Reset timer.
        if (delayMs <= RETRY_LONG_DELAY_TIMER_THRESHOLD_MILLIS) {
            // Use handler for short timer.
            sendEmptyMessageDelayed(event, delayMs);
        } else {
            // Not using setWhileIdle because it can wait util the next time the device wakes up to
            // save power.
            // If another evaluation is processed before the alarm fires,
            // this timer is restarted (AlarmManager using the same listener resets the
            // timer).
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + delayMs,
                    LOG_TAG /*debug tag*/, listener, this);
        }
    }

    /** Auto data switch evaluation reason to string. */
    @NonNull
    public static String evaluationReasonToString(
            @AutoDataSwitchEvaluationReason int reason) {
        return switch (reason) {
            case EVALUATION_REASON_REGISTRATION_STATE_CHANGED -> "REGISTRATION_STATE_CHANGED";
            case EVALUATION_REASON_DISPLAY_INFO_CHANGED -> "DISPLAY_INFO_CHANGED";
            case EVALUATION_REASON_SIGNAL_STRENGTH_CHANGED -> "SIGNAL_STRENGTH_CHANGED";
            case EVALUATION_REASON_DEFAULT_NETWORK_CHANGED -> "DEFAULT_NETWORK_CHANGED";
            case EVALUATION_REASON_DATA_SETTINGS_CHANGED -> "DATA_SETTINGS_CHANGED";
            case EVALUATION_REASON_RETRY_VALIDATION -> "RETRY_VALIDATION";
            case EVALUATION_REASON_SIM_LOADED -> "SIM_LOADED";
            case EVALUATION_REASON_VOICE_CALL_END -> "VOICE_CALL_END";
            default -> "Unknown(" + reason + ")";
        };
    }

    /** @return {@code true} if the sub is active. */
    private boolean isActiveSubId(int subId) {
        SubscriptionInfoInternal subInfo = mSubscriptionManagerService
                .getSubscriptionInfoInternal(subId);
        return subInfo != null && subInfo.isActive();
    }

    /**
     * Called when default network capabilities changed. If default network is active on
     * non-cellular, switch back to the default data phone. If default network is lost, try to find
     * another sub to switch to.
     * @param networkCapabilities {@code null} indicates default network lost.
     */
    public void updateDefaultNetworkCapabilities(
            @Nullable NetworkCapabilities networkCapabilities) {
        if (networkCapabilities != null) {
            // Exists default network
            mDefaultNetworkIsOnNonCellular = !networkCapabilities.hasTransport(TRANSPORT_CELLULAR);
            if (mDefaultNetworkIsOnNonCellular
                    && isActiveSubId(mPhoneSwitcher.getAutoSelectedDataSubId())) {
                log("default network is active on non cellular, switch back to default");
                evaluateAutoDataSwitch(EVALUATION_REASON_DEFAULT_NETWORK_CHANGED);
            }
        } else {
            log("default network is lost, try to find another active sub to switch to");
            mDefaultNetworkIsOnNonCellular = false;
            evaluateAutoDataSwitch(EVALUATION_REASON_DEFAULT_NETWORK_CHANGED);
        }
    }

    /**
     * Cancel any auto switch attempts when the current environment is not suitable for auto switch.
     */
    private void cancelAnyPendingSwitch() {
        mSelectedTargetPhoneId = INVALID_PHONE_INDEX;
        resetFailedCount();
        if (mScheduledEventsToExtras.containsKey(EVENT_STABILITY_CHECK_PASSED)) {
            if (mEventsToAlarmListener.containsKey(EVENT_STABILITY_CHECK_PASSED)) {
                mAlarmManager.cancel(mEventsToAlarmListener.get(EVENT_STABILITY_CHECK_PASSED));
            } else {
                loge("cancelAnyPendingSwitch: EVENT_STABILITY_CHECK_PASSED listener is null");
            }
            removeMessages(EVENT_STABILITY_CHECK_PASSED);
            mScheduledEventsToExtras.remove(EVENT_STABILITY_CHECK_PASSED);
        }
        mPhoneSwitcherCallback.onRequireCancelAnyPendingAutoSwitchValidation();
    }

    /**
     * Display a notification the first time auto data switch occurs.
     * @param phoneId The phone Id of the current preferred phone.
     * @param isDueToAutoSwitch {@code true} if the switch was due to auto data switch feature.
     */
    public void displayAutoDataSwitchNotification(int phoneId, boolean isDueToAutoSwitch) {
        NotificationManager notificationManager = mContext.getSystemService(
                NotificationManager.class);
        if (notificationManager == null) return;
        if (mDisplayedNotification) {
            // cancel posted notification if any exist
            notificationManager.cancel(AUTO_DATA_SWITCH_NOTIFICATION_TAG,
                    AUTO_DATA_SWITCH_NOTIFICATION_ID);
            return;
        }
        // proceed only the first time auto data switch occurs, which includes data during call
        if (!isDueToAutoSwitch) {
            return;
        }
        SubscriptionInfo subInfo = mSubscriptionManagerService
                .getSubscriptionInfo(mSubscriptionManagerService.getSubId(phoneId));
        if (subInfo == null || subInfo.isOpportunistic()) {
            loge("displayAutoDataSwitchNotification: phoneId="
                    + phoneId + " unexpected subInfo " + subInfo);
            return;
        }
        int subId = subInfo.getSubscriptionId();
        logl("displayAutoDataSwitchNotification: display for subId=" + subId);
        // "Mobile network settings" screen / dialog
        Intent intent = new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
        final Bundle fragmentArgs = new Bundle();
        // Special contract for Settings to highlight permission row
        fragmentArgs.putString(SETTINGS_EXTRA_FRAGMENT_ARG_KEY, AUTO_DATA_SWITCH_SETTING_R_ID);
        intent.putExtra(Settings.EXTRA_SUB_ID, subId);
        intent.putExtra(SETTINGS_EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArgs);
        PendingIntent contentIntent = PendingIntent.getActivity(
                mContext, subId, intent, PendingIntent.FLAG_IMMUTABLE);

        CharSequence activeCarrierName = subInfo.getDisplayName();
        CharSequence contentTitle = mContext.getString(
                com.android.internal.R.string.auto_data_switch_title, activeCarrierName);
        CharSequence contentText = mContext.getText(
                com.android.internal.R.string.auto_data_switch_content);

        final Notification notif = new Notification.Builder(mContext)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setColor(mContext.getResources().getColor(
                        com.android.internal.R.color.system_notification_accent_color))
                .setChannelId(NotificationChannelController.CHANNEL_ID_MOBILE_DATA_STATUS)
                .setContentIntent(contentIntent)
                .setStyle(new Notification.BigTextStyle().bigText(contentText))
                .build();
        notificationManager.notify(AUTO_DATA_SWITCH_NOTIFICATION_TAG,
                AUTO_DATA_SWITCH_NOTIFICATION_ID, notif);
        mDisplayedNotification = true;
    }

    /** Enable future switch retry again. Called when switch condition changed. */
    public void resetFailedCount() {
        mAutoSwitchValidationFailedCount = 0;
    }

    /**
     * Called when skipped switch due to validation failed. Schedule retry to switch again.
     */
    public void evaluateRetryOnValidationFailed() {
        if (mAutoSwitchValidationFailedCount < mAutoDataSwitchValidationMaxRetry) {
            evaluateAutoDataSwitch(EVALUATION_REASON_RETRY_VALIDATION);
            mAutoSwitchValidationFailedCount++;
        } else {
            logl("evaluateRetryOnValidationFailed: reached max auto switch retry count "
                    + mAutoDataSwitchValidationMaxRetry);
            mAutoSwitchValidationFailedCount = 0;
        }
    }

    /**
     * @param phoneId The phone Id to check.
     * @return {@code true} if the phone Id is an active modem.
     */
    private boolean isActiveModemPhone(int phoneId) {
        return phoneId >= 0 && phoneId < mPhonesSignalStatus.length;
    }

    /**
     * Log debug messages.
     * @param s debug messages
     */
    private void log(@NonNull String s) {
        Rlog.d(LOG_TAG, s);
    }

    /**
     * Log error messages.
     * @param s error messages
     */
    private void loge(@NonNull String s) {
        Rlog.e(LOG_TAG, s);
    }

    /**
     * Log debug messages and also log into the local log.
     * @param s debug messages
     */
    private void logl(@NonNull String s) {
        log(s);
        mLocalLog.log(s);
    }

    /**
     * Dump the state of DataNetworkController
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("AutoDataSwitchController:");
        pw.increaseIndent();
        pw.println("mScoreTolerance=" + mScoreTolerance);
        pw.println("mAutoDataSwitchValidationMaxRetry=" + mAutoDataSwitchValidationMaxRetry
                + " mAutoSwitchValidationFailedCount=" + mAutoSwitchValidationFailedCount);
        pw.println("mRequirePingTestBeforeDataSwitch=" + mRequirePingTestBeforeSwitch);
        pw.println("mAutoDataSwitchAvailabilityStabilityTimeThreshold="
                + mAutoDataSwitchAvailabilityStabilityTimeThreshold);
        pw.println("mSelectedTargetPhoneId=" + mSelectedTargetPhoneId);
        pw.increaseIndent();
        for (PhoneSignalStatus status: mPhonesSignalStatus) {
            pw.println(status);
        }
        pw.decreaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
    }
}

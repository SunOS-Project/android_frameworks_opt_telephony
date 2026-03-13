package com.android.internal.telephony;

import android.R;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.radio.V1_0.IRadio;
import android.hardware.radio.V1_0.RadioResponseInfo;
import android.hardware.radio.data.IRadioData;
import android.hardware.radio.ims.IRadioIms;
import android.hardware.radio.ims.ImsRegistration;
import android.hardware.radio.messaging.IRadioMessaging;
import android.hardware.radio.modem.IRadioModem;
import android.hardware.radio.modem.ImeiInfo;
import android.hardware.radio.network.IRadioNetwork;
import android.hardware.radio.sim.IRadioSim;
import android.hardware.radio.voice.IRadioVoice;
import android.internal.telephony.sysprop.TelephonyProperties;
import android.net.KeepalivePacketData;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.IHwBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.WorkSource;
import android.provider.Settings;
import android.telephony.AccessNetworkConstants;
import android.telephony.BarringInfo;
import android.telephony.CarrierRestrictionRules;
import android.telephony.ClientRequestStats;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.ModemActivityInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessFamily;
import android.telephony.RadioAccessSpecifier;
import android.telephony.SignalThresholdInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyHistogram;
import android.telephony.TelephonyManager;
import android.telephony.data.DataProfile;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.gsm.SmsMessage;
import android.telephony.ims.feature.ConnectionFailureInfo;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import com.android.internal.hidden_from_bootclasspath.com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.cat.BerTlv;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.emergency.EmergencyConstants;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.imsphone.ImsCallInfo;
import com.android.internal.telephony.imsphone.ImsRttTextHandler;
import com.android.internal.telephony.metrics.ModemRestartStats;
import com.android.internal.telephony.metrics.TelephonyMetrics;
import com.android.internal.telephony.nano.TelephonyProto;
import com.android.internal.telephony.satellite.SatelliteModemInterface;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SimPhonebookRecord;
import com.android.internal.telephony.util.NetworkStackConstants;
import com.android.internal.telephony.util.TelephonyUtils;
import com.android.internal.util.FunctionalUtils;
import com.android.telephony.Rlog;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/* loaded from: classes.dex */
public class RIL extends BaseCommands implements CommandsInterface {
    public static final int FOR_ACK_WAKELOCK = 1;
    public static final int FOR_WAKELOCK = 0;
    public static final int INVALID_WAKELOCK = -1;
    public static final int MAX_SERVICE_IDX = 7;
    public static final int MIN_SERVICE_IDX = 0;
    public final PowerManager.WakeLock mAckWakeLock;
    final int mAckWakeLockTimeout;
    volatile int mAckWlSequenceNum;
    private WorkSource mActiveWakelockWorkSource;
    private boolean mAllRadioServiceReady;
    private final ClientWakelockTracker mClientWakelockTracker;
    private final ConcurrentHashMap<Integer, HalVersion> mCompatOverrides;
    private DataIndication mDataIndication;
    private DataResponse mDataResponse;
    private final SparseArray<BinderServiceDeathRecipient> mDeathRecipients;
    private final SparseArray<Set<Integer>> mDisabledRadioServices;
    private final FeatureFlags mFeatureFlags;
    private final Map<Integer, HalVersion> mHalVersion;
    private ImsIndication mImsIndication;
    private ImsResponse mImsResponse;
    private boolean mIsCellularSupported;
    private boolean mIsFirstInitialized;
    boolean mIsRadioProxyInitialized;
    Boolean mIsRadioVersion20Cached;
    Object[] mLastNITZTimeInfo;
    int mLastRadioPowerResult;
    private MessagingIndication mMessagingIndication;
    private MessagingResponse mMessagingResponse;
    private TelephonyMetrics mMetrics;
    private MockModem mMockModem;
    private ModemIndication mModemIndication;
    private ModemResponse mModemResponse;
    private NetworkIndication mNetworkIndication;
    private NetworkResponse mNetworkResponse;
    protected IOplusRIL mOplusRILImpl;
    final Integer mPhoneId;
    private WorkSource mRILDefaultWorkSource;
    private RadioBugDetector mRadioBugDetector;
    private RadioIndication mRadioIndication;
    private volatile IRadio mRadioProxy;
    private final RadioProxyDeathRecipient mRadioProxyDeathRecipient;
    private RadioResponse mRadioResponse;
    SparseArray<RILRequest> mRequestList;
    final RilHandler mRilHandler;
    private final SparseArray<AtomicLong> mServiceCookies;
    private SparseArray<RadioServiceProxy> mServiceProxies;
    private SimIndication mSimIndication;
    private SimResponse mSimResponse;
    AtomicBoolean mTestingEmergencyCall;
    private VoiceIndication mVoiceIndication;
    private VoiceResponse mVoiceResponse;
    public final PowerManager.WakeLock mWakeLock;
    int mWakeLockCount;
    final int mWakeLockTimeout;
    volatile int mWlSequenceNum;
    public static final HalVersion RADIO_HAL_VERSION_UNSUPPORTED = HalVersion.UNSUPPORTED;
    public static final HalVersion RADIO_HAL_VERSION_UNKNOWN = HalVersion.UNKNOWN;
    public static final HalVersion RADIO_HAL_VERSION_1_1 = new HalVersion(1, 1);
    public static final HalVersion RADIO_HAL_VERSION_1_2 = new HalVersion(1, 2);
    public static final HalVersion RADIO_HAL_VERSION_1_3 = new HalVersion(1, 3);
    public static final HalVersion RADIO_HAL_VERSION_1_4 = new HalVersion(1, 4);
    public static final HalVersion RADIO_HAL_VERSION_1_5 = new HalVersion(1, 5);
    public static final HalVersion RADIO_HAL_VERSION_1_6 = new HalVersion(1, 6);
    public static final HalVersion RADIO_HAL_VERSION_2_0 = new HalVersion(2, 0);
    public static final HalVersion RADIO_HAL_VERSION_2_1 = new HalVersion(2, 1);
    public static final HalVersion RADIO_HAL_VERSION_2_2 = new HalVersion(2, 2);
    public static final HalVersion RADIO_HAL_VERSION_2_3 = new HalVersion(2, 3);
    public static final HalVersion RADIO_HAL_VERSION_2_4 = new HalVersion(2, 4);
    static SparseArray<TelephonyHistogram> sRilTimeHistograms = new SparseArray<>();
    static final String[] HIDL_SERVICE_NAME = {"slot1", "slot2", "slot3"};
    private static final Map<String, Integer> FEATURES_TO_SERVICES = Map.ofEntries(Map.entry("android.hardware.telephony.calling", 6), Map.entry("android.hardware.telephony.data", 1), Map.entry("android.hardware.telephony.messaging", 2), Map.entry("android.hardware.telephony.ims", 7));

    protected boolean isGetHidlServiceSync() {
        return true;
    }

    boolean isLogOrTrace() {
        return true;
    }

    public static List<TelephonyHistogram> getTelephonyRILTimingHistograms() {
        ArrayList arrayList;
        synchronized (sRilTimeHistograms) {
            try {
                arrayList = new ArrayList(sRilTimeHistograms.size());
                for (int i = 0; i < sRilTimeHistograms.size(); i++) {
                    arrayList.add(new TelephonyHistogram(sRilTimeHistograms.valueAt(i)));
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return arrayList;
    }

    public class RilHandler extends Handler {
        public RilHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message message) {
            int i = message.what;
            if (i == 2) {
                synchronized (RIL.this.mRequestList) {
                    try {
                        if (message.arg1 == RIL.this.mWlSequenceNum && RIL.this.clearWakeLock(0)) {
                            if (RIL.this.mRadioBugDetector != null) {
                                RIL.this.mRadioBugDetector.processWakelockTimeout();
                            }
                            int size = RIL.this.mRequestList.size();
                            RIL.this.riljLog("WAKE_LOCK_TIMEOUT mRequestList=" + size);
                            for (int i2 = 0; i2 < size; i2++) {
                                RILRequest rILRequestValueAt = RIL.this.mRequestList.valueAt(i2);
                                RIL.this.riljLog(i2 + ": [" + rILRequestValueAt.mSerial + "] " + RILUtils.requestToString(rILRequestValueAt.mRequest));
                            }
                        }
                    } catch (Throwable th) {
                        throw th;
                    }
                }
                return;
            }
            if (i == 1001) {
                RIL.this.riljLog("handleMessage: EVENT_RADIO_RETRY_GET_SERVICE");
                RIL.this.mRilHandler.removeMessages(1001);
                if (RIL.this.isRildReady()) {
                    RIL.this.getAllRadioServices();
                    return;
                } else {
                    RilHandler rilHandler = RIL.this.mRilHandler;
                    rilHandler.sendMessageDelayed(rilHandler.obtainMessage(1001), 1000L);
                    return;
                }
            }
            if (i == 4) {
                if (message.arg1 == RIL.this.mAckWlSequenceNum) {
                    RIL.this.clearWakeLock(1);
                    return;
                }
                return;
            }
            if (i == 5) {
                RILRequest rILRequestFindAndRemoveRequestFromList = RIL.this.findAndRemoveRequestFromList(((Integer) message.obj).intValue());
                if (rILRequestFindAndRemoveRequestFromList == null) {
                    return;
                }
                if (rILRequestFindAndRemoveRequestFromList.mResult != null) {
                    AsyncResult.forMessage(rILRequestFindAndRemoveRequestFromList.mResult, RIL.getResponseForTimedOutRILRequest(rILRequestFindAndRemoveRequestFromList), (Throwable) null);
                    rILRequestFindAndRemoveRequestFromList.mResult.sendToTarget();
                    RIL.this.mMetrics.writeOnRilTimeoutResponse(RIL.this.mPhoneId.intValue(), rILRequestFindAndRemoveRequestFromList.mSerial, rILRequestFindAndRemoveRequestFromList.mRequest);
                }
                RIL.this.decrementWakeLock(rILRequestFindAndRemoveRequestFromList);
                rILRequestFindAndRemoveRequestFromList.release();
                return;
            }
            if (i == 6) {
                int i3 = message.arg1;
                RIL.this.riljLog("handleMessage: EVENT_RADIO_PROXY_DEAD cookie = " + message.obj + ", service = " + RIL.serviceToString(i3) + ", service cookie = " + RIL.this.mServiceCookies.get(i3));
                if (((Long) message.obj).longValue() == ((AtomicLong) RIL.this.mServiceCookies.get(i3)).get()) {
                    RIL ril = RIL.this;
                    ril.mIsRadioProxyInitialized = false;
                    ril.resetProxyAndRequestList(i3);
                    return;
                }
                return;
            }
            if (i != 7) {
                return;
            }
            int i4 = message.arg1;
            RIL.this.mAllRadioServiceReady = false;
            long jLongValue = ((Long) message.obj).longValue();
            if (jLongValue == ((AtomicLong) RIL.this.mServiceCookies.get(i4)).get()) {
                RIL.this.riljLog("handleMessage: EVENT_AIDL_PROXY_DEAD cookie = " + jLongValue + ", service = " + RIL.serviceToString(i4) + ", cookie = " + RIL.this.mServiceCookies.get(i4));
                RIL ril2 = RIL.this;
                ril2.mIsRadioProxyInitialized = false;
                ril2.resetProxyAndRequestList(i4);
                RIL.this.mRilHandler.removeMessages(7);
                return;
            }
            RIL.this.riljLog("Ignore stale EVENT_AIDL_PROXY_DEAD for service " + RIL.serviceToString(i4));
        }
    }

    public RadioBugDetector getRadioBugDetector() {
        if (this.mRadioBugDetector == null) {
            this.mRadioBugDetector = new RadioBugDetector(this.mContext, this.mPhoneId.intValue());
        }
        return this.mRadioBugDetector;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object getResponseForTimedOutRILRequest(RILRequest rILRequest) {
        if (rILRequest != null && rILRequest.mRequest == 135) {
            return new ModemActivityInfo(0L, 0, 0, new int[ModemActivityInfo.getNumTxPowerLevels()], 0);
        }
        return null;
    }

    final class RadioProxyDeathRecipient implements IHwBinder.DeathRecipient {
        RadioProxyDeathRecipient() {
        }

        public void serviceDied(long j) {
            RIL.this.riljLog("serviceDied");
            RIL.this.mRilHandler.removeMessages(6);
            RilHandler rilHandler = RIL.this.mRilHandler;
            rilHandler.sendMessageDelayed(rilHandler.obtainMessage(6, 0, 0, Long.valueOf(j)), 1000L);
        }
    }

    private final class BinderServiceDeathRecipient implements IBinder.DeathRecipient {
        private IBinder mBinder;
        private long mLinkedFlags = 0;
        private final int mService;

        BinderServiceDeathRecipient(int i) {
            this.mService = i;
        }

        public void linkToDeath(IBinder iBinder) throws RemoteException {
            if (iBinder != null) {
                RIL.this.riljLog("Linked to death for service " + RIL.serviceToString(this.mService));
                this.mBinder = iBinder;
                long jIncrementAndGet = ((AtomicLong) RIL.this.mServiceCookies.get(this.mService)).incrementAndGet();
                this.mLinkedFlags = jIncrementAndGet;
                this.mBinder.linkToDeath(this, (int) jIncrementAndGet);
                return;
            }
            RIL.this.riljLoge("Unable to link to death for service " + RIL.serviceToString(this.mService));
        }

        public synchronized void unlinkToDeath() {
            IBinder iBinder = this.mBinder;
            if (iBinder != null) {
                iBinder.unlinkToDeath(this, (int) this.mLinkedFlags);
                this.mBinder = null;
                this.mLinkedFlags = 0L;
            }
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            RIL.this.riljLog("Service " + RIL.serviceToString(this.mService) + " has died.");
            RilHandler rilHandler = RIL.this.mRilHandler;
            rilHandler.sendMessageAtFrontOfQueue(rilHandler.obtainMessage(7, this.mService, 0, Long.valueOf(this.mLinkedFlags)));
            unlinkToDeath();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void resetProxyAndRequestList(int i) {
        try {
            riljLog("resetProxyAndRequestList, service: " + i + ", mHalVersion: " + this.mHalVersion.get(Integer.valueOf(i)));
            if (Build.isMtkPlatform() && this.mHalVersion.get(Integer.valueOf(i)).less(RADIO_HAL_VERSION_2_0)) {
                for (int i2 = 0; i2 <= 7; i2++) {
                    if (i2 == 0) {
                        this.mRadioProxy = null;
                    } else {
                        this.mServiceProxies.get(i2).clear();
                    }
                }
            }
            if (i == 0) {
                this.mRadioProxy = null;
                this.mServiceCookies.get(i).incrementAndGet();
            } else {
                for (int i3 = 0; i3 <= 7; i3++) {
                    if (i3 != 0) {
                        if (this.mServiceProxies.get(i3) == null) {
                            riljLoge("Null service proxy for service " + serviceToString(i3));
                        } else {
                            this.mServiceProxies.get(i3).clear();
                            this.mServiceCookies.get(i3).incrementAndGet();
                        }
                    }
                }
            }
            if (!this.mIsRadioProxyInitialized) {
                setRadioState(2, true);
                RILRequest.resetSerial();
                clearRequestList(1, false);
            }
            if (i == 0) {
                getRadioProxy();
            } else {
                for (int i4 = 0; i4 <= 7; i4++) {
                    if (i4 != 0) {
                        if (this.mServiceProxies.get(i4) == null) {
                            riljLoge("Null service proxy for service " + serviceToString(i4));
                        } else {
                            getRadioServiceProxy(i4);
                        }
                    }
                }
            }
        } finally {
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public boolean setModemService(String str) throws InterruptedException {
        boolean z;
        IBinder serviceBinder;
        if (str != null) {
            riljLog("Binding to MockModemService");
            this.mMockModem = null;
            this.mMockModem = new MockModem(this.mContext, str, this.mPhoneId.intValue());
            if (this.mRadioProxy != null) {
                riljLog("Disable HIDL service");
                this.mDisabledRadioServices.get(0).add(this.mPhoneId);
            }
            this.mMockModem.bindAllMockModemService();
            int i = 0;
            while (true) {
                if (i > 7) {
                    z = true;
                    break;
                }
                if (i != 0) {
                    int i2 = 0;
                    do {
                        serviceBinder = this.mMockModem.getServiceBinder(i);
                        i2++;
                        if (serviceBinder == null) {
                            riljLog("Retry(" + i2 + ") Service " + serviceToString(i));
                            try {
                                Thread.sleep(300L);
                            } catch (InterruptedException unused) {
                            }
                        }
                        if (serviceBinder != null) {
                            break;
                        }
                    } while (i2 < 10);
                    if (serviceBinder == null) {
                        riljLoge("Service " + serviceToString(i) + " bind fail");
                        z = false;
                        break;
                    }
                }
                i++;
            }
            if (z) {
                this.mIsRadioProxyInitialized = false;
                resetProxyAndRequestList(0);
                resetProxyAndRequestList(1);
            }
        } else {
            z = true;
        }
        if (str == null || !z) {
            if (z) {
                riljLog("Unbinding to MockModemService");
            }
            if (this.mDisabledRadioServices.get(0).contains(this.mPhoneId)) {
                this.mDisabledRadioServices.get(0).clear();
            }
            if (this.mMockModem != null) {
                this.mMockModem = null;
                for (int i3 = 0; i3 <= 7; i3++) {
                    if (i3 == 0) {
                        if (isRadioVersion2_0()) {
                            this.mHalVersion.put(Integer.valueOf(i3), RADIO_HAL_VERSION_2_0);
                        } else {
                            this.mHalVersion.put(Integer.valueOf(i3), RADIO_HAL_VERSION_UNKNOWN);
                        }
                    } else if (isRadioServiceSupported(i3)) {
                        this.mHalVersion.put(Integer.valueOf(i3), RADIO_HAL_VERSION_UNKNOWN);
                    } else {
                        this.mHalVersion.put(Integer.valueOf(i3), RADIO_HAL_VERSION_UNSUPPORTED);
                    }
                }
                resetProxyAndRequestList(0);
                resetProxyAndRequestList(1);
            }
        }
        return z;
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public String getModemService() {
        MockModem mockModem = this.mMockModem;
        if (mockModem != null) {
            return mockModem.getServiceName();
        }
        return "default";
    }

    public void setCompatVersion(int i, HalVersion halVersion) {
        HalVersion compatVersion = getCompatVersion(i);
        if (compatVersion != null && halVersion.greaterOrEqual(compatVersion)) {
            riljLoge("setCompatVersion with equal or greater one, ignored, halVersion=" + halVersion + ", oldVersion=" + compatVersion);
            return;
        }
        this.mCompatOverrides.put(Integer.valueOf(i), halVersion);
    }

    public HalVersion getCompatVersion(int i) {
        return this.mCompatOverrides.getOrDefault(Integer.valueOf(i), null);
    }

    public synchronized IRadio getRadioProxy() {
        if (this.mHalVersion.containsKey(0) && this.mHalVersion.get(0).greaterOrEqual(RADIO_HAL_VERSION_2_0)) {
            return null;
        }
        if (!SubscriptionManager.isValidPhoneId(this.mPhoneId.intValue())) {
            return null;
        }
        if (!this.mIsCellularSupported) {
            return null;
        }
        if (this.mRadioProxy != null) {
            return this.mRadioProxy;
        }
        try {
        } catch (RemoteException e) {
            this.mRadioProxy = null;
            riljLoge("RadioProxy getService/setResponseFunctions: " + e);
        }
        if (this.mDisabledRadioServices.get(0).contains(this.mPhoneId)) {
            riljLoge("getRadioProxy: mRadioProxy for " + HIDL_SERVICE_NAME[this.mPhoneId.intValue()] + " is disabled");
            return null;
        }
        try {
            this.mRadioProxy = android.hardware.radio.V1_6.IRadio.getService(HIDL_SERVICE_NAME[this.mPhoneId.intValue()], true);
            this.mHalVersion.put(0, RADIO_HAL_VERSION_1_6);
        } catch (NoSuchElementException unused) {
        }
        if (this.mRadioProxy == null) {
            try {
                this.mRadioProxy = android.hardware.radio.V1_5.IRadio.getService(HIDL_SERVICE_NAME[this.mPhoneId.intValue()], true);
                this.mHalVersion.put(0, RADIO_HAL_VERSION_1_5);
            } catch (NoSuchElementException unused2) {
            }
        }
        if (this.mRadioProxy == null) {
            try {
                this.mRadioProxy = android.hardware.radio.V1_4.IRadio.getService(HIDL_SERVICE_NAME[this.mPhoneId.intValue()], true);
                this.mHalVersion.put(0, RADIO_HAL_VERSION_1_4);
            } catch (NoSuchElementException unused3) {
            }
        }
        if (this.mRadioProxy == null) {
            riljLoge("IRadio <1.4 is no longer supported.");
        }
        this.mRadioProxy = this.mOplusRILImpl.setVirtualRadio(this.mRadioProxy, this.mHalVersion);
        this.mOplusRILImpl.setResponseFunctions(this.mRadioResponse, this.mRadioIndication);
        if (this.mRadioProxy != null) {
            if (!this.mIsRadioProxyInitialized) {
                this.mIsRadioProxyInitialized = true;
                this.mRadioProxy.linkToDeath(this.mRadioProxyDeathRecipient, this.mServiceCookies.get(0).incrementAndGet());
                this.mRadioProxy.setResponseFunctions(this.mRadioResponse, this.mRadioIndication);
            }
            if (this.mRadioProxy == null) {
                riljLoge("getRadioProxy: mRadioProxy == null");
                if (!this.mDisabledRadioServices.get(0).contains(this.mPhoneId)) {
                    this.mRilHandler.removeMessages(6);
                    RilHandler rilHandler = this.mRilHandler;
                    rilHandler.sendMessageDelayed(rilHandler.obtainMessage(6, 0, 0, Long.valueOf(this.mServiceCookies.get(0).get())), 1000L);
                }
            }
            return this.mRadioProxy;
        }
        riljLoge("getRadioProxy: set mRadioProxy for " + HIDL_SERVICE_NAME[this.mPhoneId.intValue()] + " as disabled");
        return null;
    }

    public <T extends RadioServiceProxy> T getRadioServiceProxy(Class<T> cls) {
        if (cls == RadioDataProxy.class) {
            return (T) getRadioServiceProxy(1);
        }
        if (cls == RadioMessagingProxy.class) {
            return (T) getRadioServiceProxy(2);
        }
        if (cls == RadioModemProxy.class) {
            return (T) getRadioServiceProxy(3);
        }
        if (cls == RadioNetworkProxy.class) {
            return (T) getRadioServiceProxy(4);
        }
        if (cls == RadioSimProxy.class) {
            return (T) getRadioServiceProxy(5);
        }
        if (cls == RadioVoiceProxy.class) {
            return (T) getRadioServiceProxy(6);
        }
        if (cls == RadioImsProxy.class) {
            return (T) getRadioServiceProxy(7);
        }
        riljLoge("getRadioServiceProxy: unrecognized " + cls);
        return null;
    }

    public synchronized RadioServiceProxy getRadioServiceProxy(int i) {
        IBinder serviceBinder;
        IBinder serviceBinder2;
        IBinder serviceBinder3;
        IBinder serviceBinder4;
        IBinder serviceBinder5;
        IBinder serviceBinder6;
        IBinder serviceBinder7;
        if (!SubscriptionManager.isValidPhoneId(this.mPhoneId.intValue())) {
            return this.mServiceProxies.get(i);
        }
        if (i >= 7 && !isRadioServiceSupported(i)) {
            if (i != 7) {
                riljLogw("getRadioServiceProxy: " + serviceToString(i) + " for " + HIDL_SERVICE_NAME[this.mPhoneId.intValue()] + " is not supported\n" + Log.getStackTraceString(new RuntimeException()));
            }
            return this.mServiceProxies.get(i);
        }
        if (!this.mIsCellularSupported) {
            return this.mServiceProxies.get(i);
        }
        RadioServiceProxy radioServiceProxy = this.mServiceProxies.get(i);
        if (!radioServiceProxy.isEmpty()) {
            return radioServiceProxy;
        }
        if (!isRildReady()) {
            riljLog("getRadioExServiceProxy " + serviceToString(i) + " not ready.");
            this.mRilHandler.removeMessages(1001);
            RilHandler rilHandler = this.mRilHandler;
            rilHandler.sendMessageDelayed(rilHandler.obtainMessage(1001), 1000L);
            return radioServiceProxy;
        }
        try {
        } catch (RemoteException e) {
            radioServiceProxy.clear();
            riljLoge("ServiceProxy getService/setResponseFunctions: " + e);
        }
        if (this.mMockModem == null && this.mDisabledRadioServices.get(i).contains(this.mPhoneId)) {
            riljLoge("getRadioServiceProxy: " + serviceToString(i) + " for " + HIDL_SERVICE_NAME[this.mPhoneId.intValue()] + " is disabled\n" + Log.getStackTraceString(new RuntimeException()));
            return null;
        }
        switch (i) {
            case 1:
                MockModem mockModem = this.mMockModem;
                if (mockModem == null) {
                    serviceBinder = ServiceManager.waitForDeclaredService(IRadioData.DESCRIPTOR + "/" + HIDL_SERVICE_NAME[this.mPhoneId.intValue()]);
                } else {
                    serviceBinder = mockModem.getServiceBinder(1);
                }
                if (serviceBinder != null) {
                    this.mHalVersion.put(Integer.valueOf(i), ((RadioDataProxy) radioServiceProxy).setAidl(this.mHalVersion.get(Integer.valueOf(i)), IRadioData.Stub.asInterface(serviceBinder)));
                    break;
                }
                break;
            case 2:
                MockModem mockModem2 = this.mMockModem;
                if (mockModem2 == null) {
                    serviceBinder2 = ServiceManager.waitForDeclaredService(IRadioMessaging.DESCRIPTOR + "/" + HIDL_SERVICE_NAME[this.mPhoneId.intValue()]);
                } else {
                    serviceBinder2 = mockModem2.getServiceBinder(2);
                }
                if (serviceBinder2 != null) {
                    this.mHalVersion.put(Integer.valueOf(i), ((RadioMessagingProxy) radioServiceProxy).setAidl(this.mHalVersion.get(Integer.valueOf(i)), IRadioMessaging.Stub.asInterface(serviceBinder2)));
                    break;
                }
                break;
            case 3:
                MockModem mockModem3 = this.mMockModem;
                if (mockModem3 == null) {
                    serviceBinder3 = ServiceManager.waitForDeclaredService(IRadioModem.DESCRIPTOR + "/" + HIDL_SERVICE_NAME[this.mPhoneId.intValue()]);
                } else {
                    serviceBinder3 = mockModem3.getServiceBinder(3);
                }
                if (serviceBinder3 != null) {
                    this.mHalVersion.put(Integer.valueOf(i), ((RadioModemProxy) radioServiceProxy).setAidl(this.mHalVersion.get(Integer.valueOf(i)), IRadioModem.Stub.asInterface(serviceBinder3)));
                    break;
                }
                break;
            case 4:
                MockModem mockModem4 = this.mMockModem;
                if (mockModem4 == null) {
                    serviceBinder4 = ServiceManager.waitForDeclaredService(IRadioNetwork.DESCRIPTOR + "/" + HIDL_SERVICE_NAME[this.mPhoneId.intValue()]);
                } else {
                    serviceBinder4 = mockModem4.getServiceBinder(4);
                }
                if (serviceBinder4 != null) {
                    this.mHalVersion.put(Integer.valueOf(i), ((RadioNetworkProxy) radioServiceProxy).setAidl(this.mHalVersion.get(Integer.valueOf(i)), IRadioNetwork.Stub.asInterface(serviceBinder4)));
                    break;
                }
                break;
            case 5:
                MockModem mockModem5 = this.mMockModem;
                if (mockModem5 == null) {
                    serviceBinder5 = ServiceManager.waitForDeclaredService(IRadioSim.DESCRIPTOR + "/" + HIDL_SERVICE_NAME[this.mPhoneId.intValue()]);
                } else {
                    serviceBinder5 = mockModem5.getServiceBinder(5);
                }
                if (serviceBinder5 != null) {
                    this.mHalVersion.put(Integer.valueOf(i), ((RadioSimProxy) radioServiceProxy).setAidl(this.mHalVersion.get(Integer.valueOf(i)), IRadioSim.Stub.asInterface(serviceBinder5)));
                    break;
                }
                break;
            case 6:
                MockModem mockModem6 = this.mMockModem;
                if (mockModem6 == null) {
                    serviceBinder6 = ServiceManager.waitForDeclaredService(IRadioVoice.DESCRIPTOR + "/" + HIDL_SERVICE_NAME[this.mPhoneId.intValue()]);
                } else {
                    serviceBinder6 = mockModem6.getServiceBinder(6);
                }
                if (serviceBinder6 != null) {
                    this.mHalVersion.put(Integer.valueOf(i), ((RadioVoiceProxy) radioServiceProxy).setAidl(this.mHalVersion.get(Integer.valueOf(i)), IRadioVoice.Stub.asInterface(serviceBinder6)));
                    break;
                }
                break;
            case 7:
                MockModem mockModem7 = this.mMockModem;
                if (mockModem7 == null) {
                    serviceBinder7 = ServiceManager.waitForDeclaredService(IRadioIms.DESCRIPTOR + "/" + HIDL_SERVICE_NAME[this.mPhoneId.intValue()]);
                } else {
                    serviceBinder7 = mockModem7.getServiceBinder(7);
                }
                if (serviceBinder7 != null) {
                    this.mHalVersion.put(Integer.valueOf(i), ((RadioImsProxy) radioServiceProxy).setAidl(this.mHalVersion.get(Integer.valueOf(i)), IRadioIms.Stub.asInterface(serviceBinder7)));
                    break;
                }
                break;
        }
        if (radioServiceProxy.isEmpty() && this.mHalVersion.get(Integer.valueOf(i)).less(RADIO_HAL_VERSION_2_0)) {
            try {
                this.mHalVersion.put(Integer.valueOf(i), RADIO_HAL_VERSION_1_6);
                radioServiceProxy.setHidl(this.mHalVersion.get(Integer.valueOf(i)), android.hardware.radio.V1_6.IRadio.getService(HIDL_SERVICE_NAME[this.mPhoneId.intValue()], true));
            } catch (NoSuchElementException unused) {
            }
        }
        if (radioServiceProxy.isEmpty() && this.mHalVersion.get(Integer.valueOf(i)).less(RADIO_HAL_VERSION_2_0)) {
            try {
                this.mHalVersion.put(Integer.valueOf(i), RADIO_HAL_VERSION_1_5);
                radioServiceProxy.setHidl(this.mHalVersion.get(Integer.valueOf(i)), android.hardware.radio.V1_5.IRadio.getService(HIDL_SERVICE_NAME[this.mPhoneId.intValue()], true));
            } catch (NoSuchElementException unused2) {
            }
        }
        if (radioServiceProxy.isEmpty() && this.mHalVersion.get(Integer.valueOf(i)).less(RADIO_HAL_VERSION_2_0)) {
            try {
                this.mHalVersion.put(Integer.valueOf(i), RADIO_HAL_VERSION_1_4);
                radioServiceProxy.setHidl(this.mHalVersion.get(Integer.valueOf(i)), android.hardware.radio.V1_4.IRadio.getService(HIDL_SERVICE_NAME[this.mPhoneId.intValue()], true));
            } catch (NoSuchElementException unused3) {
            }
        }
        if (radioServiceProxy.isEmpty() && this.mHalVersion.get(Integer.valueOf(i)).less(RADIO_HAL_VERSION_2_0)) {
            riljLoge("IRadio <1.4 is no longer supported.");
        }
        this.mOplusRILImpl.setVirtualServiceProxy(radioServiceProxy, i, this.mHalVersion);
        this.mOplusRILImpl.setResponseFunctions(this.mRadioResponse, this.mRadioIndication);
        if (!radioServiceProxy.isEmpty()) {
            if (radioServiceProxy.isAidl()) {
                switch (i) {
                    case 1:
                        this.mDeathRecipients.get(i).linkToDeath(((RadioDataProxy) radioServiceProxy).getAidl().asBinder());
                        ((RadioDataProxy) radioServiceProxy).getAidl().setResponseFunctions(this.mDataResponse, this.mDataIndication);
                        break;
                    case 2:
                        this.mDeathRecipients.get(i).linkToDeath(((RadioMessagingProxy) radioServiceProxy).getAidl().asBinder());
                        ((RadioMessagingProxy) radioServiceProxy).getAidl().setResponseFunctions(this.mMessagingResponse, this.mMessagingIndication);
                        break;
                    case 3:
                        this.mDeathRecipients.get(i).linkToDeath(((RadioModemProxy) radioServiceProxy).getAidl().asBinder());
                        ((RadioModemProxy) radioServiceProxy).getAidl().setResponseFunctions(this.mModemResponse, this.mModemIndication);
                        break;
                    case 4:
                        this.mDeathRecipients.get(i).linkToDeath(((RadioNetworkProxy) radioServiceProxy).getAidl().asBinder());
                        ((RadioNetworkProxy) radioServiceProxy).getAidl().setResponseFunctions(this.mNetworkResponse, this.mNetworkIndication);
                        break;
                    case 5:
                        this.mDeathRecipients.get(i).linkToDeath(((RadioSimProxy) radioServiceProxy).getAidl().asBinder());
                        ((RadioSimProxy) radioServiceProxy).getAidl().setResponseFunctions(this.mSimResponse, this.mSimIndication);
                        break;
                    case 6:
                        this.mDeathRecipients.get(i).linkToDeath(((RadioVoiceProxy) radioServiceProxy).getAidl().asBinder());
                        ((RadioVoiceProxy) radioServiceProxy).getAidl().setResponseFunctions(this.mVoiceResponse, this.mVoiceIndication);
                        break;
                    case 7:
                        this.mDeathRecipients.get(i).linkToDeath(((RadioImsProxy) radioServiceProxy).getAidl().asBinder());
                        ((RadioImsProxy) radioServiceProxy).getAidl().setResponseFunctions(this.mImsResponse, this.mImsIndication);
                        break;
                }
            } else {
                if (this.mHalVersion.get(Integer.valueOf(i)).greaterOrEqual(RADIO_HAL_VERSION_2_0)) {
                    throw new AssertionError("serviceProxy shouldn't be HIDL with HAL 2.0");
                }
                if (!this.mIsRadioProxyInitialized) {
                    this.mIsRadioProxyInitialized = true;
                    radioServiceProxy.getHidl().linkToDeath(this.mRadioProxyDeathRecipient, this.mServiceCookies.get(0).incrementAndGet());
                    radioServiceProxy.getHidl().setResponseFunctions(this.mRadioResponse, this.mRadioIndication);
                }
            }
        } else {
            riljLoge("getRadioServiceProxy: set " + serviceToString(i) + " for " + HIDL_SERVICE_NAME[this.mPhoneId.intValue()] + " as disabled\n" + Log.getStackTraceString(new RuntimeException()));
        }
        if (radioServiceProxy.isEmpty()) {
            riljLoge("getRadioServiceProxy: serviceProxy == null");
        }
        return radioServiceProxy;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isRildReady() {
        if (this.mMockModem != null || this.mHalVersion.get(0).less(RADIO_HAL_VERSION_2_0) || this.mAllRadioServiceReady) {
            return true;
        }
        boolean z = this.mIsFirstInitialized;
        String str = IRadioData.DESCRIPTOR;
        if (z) {
            if (ServiceManager.checkService(str + "/" + HIDL_SERVICE_NAME[this.mPhoneId.intValue()]) == null) {
                return false;
            }
            this.mAllRadioServiceReady = true;
            this.mIsFirstInitialized = false;
            return true;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(str);
        sb.append("/");
        String[] strArr = HIDL_SERVICE_NAME;
        sb.append(strArr[this.mPhoneId.intValue()]);
        if (ServiceManager.checkService(sb.toString()) != null) {
            if (ServiceManager.checkService(IRadioMessaging.DESCRIPTOR + "/" + strArr[this.mPhoneId.intValue()]) != null) {
                if (ServiceManager.checkService(IRadioModem.DESCRIPTOR + "/" + strArr[this.mPhoneId.intValue()]) != null) {
                    if (ServiceManager.checkService(IRadioNetwork.DESCRIPTOR + "/" + strArr[this.mPhoneId.intValue()]) != null) {
                        if (ServiceManager.checkService(IRadioSim.DESCRIPTOR + "/" + strArr[this.mPhoneId.intValue()]) != null) {
                            if (ServiceManager.checkService(IRadioVoice.DESCRIPTOR + "/" + strArr[this.mPhoneId.intValue()]) != null) {
                                this.mAllRadioServiceReady = true;
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void getAllRadioServices() {
        for (int i = 0; i <= 7; i++) {
            if (i == 0) {
                getRadioProxy();
            } else {
                getRadioServiceProxy(i);
            }
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public synchronized void onSlotActiveStatusChange(boolean z) {
        try {
            this.mIsRadioProxyInitialized = false;
            if (z) {
                for (int i = 0; i <= 7; i++) {
                    if (i == 0) {
                        getRadioProxy();
                    } else {
                        getRadioServiceProxy(i);
                    }
                }
            } else {
                resetProxyAndRequestList(0);
                resetProxyAndRequestList(1);
            }
        } catch (Throwable th) {
            throw th;
        }
    }

    public RIL(Context context, int i, int i2, Integer num, FeatureFlags featureFlags) {
        this(context, i, i2, num, null, featureFlags);
    }

    public RIL(Context context, int i, int i2, Integer num, SparseArray<RadioServiceProxy> sparseArray, FeatureFlags featureFlags) {
        super(context);
        this.mClientWakelockTracker = new ClientWakelockTracker();
        HashMap map = new HashMap();
        this.mHalVersion = map;
        this.mWlSequenceNum = 0;
        this.mAckWlSequenceNum = 0;
        this.mRequestList = new SparseArray<>();
        this.mLastRadioPowerResult = 0;
        this.mIsRadioProxyInitialized = false;
        this.mIsRadioVersion20Cached = null;
        this.mTestingEmergencyCall = new AtomicBoolean(false);
        this.mDisabledRadioServices = new SparseArray<>();
        this.mMetrics = TelephonyMetrics.getInstance();
        this.mRadioBugDetector = null;
        this.mRadioProxy = null;
        this.mServiceProxies = new SparseArray<>();
        this.mDeathRecipients = new SparseArray<>();
        this.mServiceCookies = new SparseArray<>();
        this.mCompatOverrides = new ConcurrentHashMap<>();
        this.mAllRadioServiceReady = false;
        this.mIsFirstInitialized = true;
        this.mFeatureFlags = featureFlags;
        i2 = featureFlags.cleanupCdma() ? -1 : i2;
        riljLog("RIL: init allowedNetworkTypes=" + i + " cdmaSubscription=" + i2 + ")");
        this.mContext = context;
        this.mCdmaSubscription = i2;
        this.mAllowedNetworkTypesBitmask = i;
        this.mPhoneType = 0;
        Integer numValueOf = Integer.valueOf(num == null ? 0 : num.intValue());
        this.mPhoneId = numValueOf;
        if (isRadioBugDetectionEnabled()) {
            this.mRadioBugDetector = new RadioBugDetector(context, numValueOf.intValue());
        }
        try {
            if (isRadioVersion2_0()) {
                map.put(0, RADIO_HAL_VERSION_2_0);
            } else {
                map.put(0, RADIO_HAL_VERSION_UNKNOWN);
            }
        } catch (SecurityException e) {
            if (sparseArray == null) {
                throw e;
            }
        }
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
        this.mIsCellularSupported = !SystemProperties.getBoolean("ro.radio.noril", false) && (telephonyManager.isVoiceCapable() || telephonyManager.isSmsCapable() || telephonyManager.isDataCapable());
        this.mRadioResponse = new RadioResponse(this);
        this.mRadioIndication = new RadioIndication(this);
        this.mDataResponse = new DataResponse(this);
        this.mDataIndication = new DataIndication(this);
        this.mImsResponse = new ImsResponse(this);
        this.mImsIndication = new ImsIndication(this);
        this.mMessagingResponse = new MessagingResponse(this);
        this.mMessagingIndication = new MessagingIndication(this);
        this.mModemResponse = new ModemResponse(this);
        this.mModemIndication = new ModemIndication(this);
        this.mNetworkResponse = new NetworkResponse(this);
        this.mNetworkIndication = new NetworkIndication(this);
        this.mSimResponse = new SimResponse(this);
        this.mSimIndication = new SimIndication(this);
        this.mVoiceResponse = new VoiceResponse(this);
        this.mVoiceIndication = new VoiceIndication(this);
        this.mRilHandler = new RilHandler();
        this.mRadioProxyDeathRecipient = new RadioProxyDeathRecipient();
        for (int i3 = 0; i3 <= 7; i3++) {
            if (i3 != 0) {
                try {
                    if (isRadioServiceSupported(i3)) {
                        this.mHalVersion.put(Integer.valueOf(i3), RADIO_HAL_VERSION_UNKNOWN);
                    } else {
                        this.mHalVersion.put(Integer.valueOf(i3), RADIO_HAL_VERSION_UNSUPPORTED);
                    }
                } catch (SecurityException e2) {
                    if (sparseArray == null) {
                        throw e2;
                    }
                }
                this.mDeathRecipients.put(i3, new BinderServiceDeathRecipient(i3));
            }
            this.mDisabledRadioServices.put(i3, new HashSet());
            this.mServiceCookies.put(i3, new AtomicLong(0L));
        }
        if (sparseArray == null) {
            this.mServiceProxies.put(1, new RadioDataProxy());
            this.mServiceProxies.put(2, new RadioMessagingProxy());
            this.mServiceProxies.put(3, new RadioModemProxy());
            this.mServiceProxies.put(4, new RadioNetworkProxy());
            this.mServiceProxies.put(5, new RadioSimProxy());
            this.mServiceProxies.put(6, new RadioVoiceProxy());
            this.mServiceProxies.put(7, new RadioImsProxy());
        } else {
            this.mServiceProxies = sparseArray;
        }
        PowerManager powerManager = (PowerManager) context.getSystemService("power");
        PowerManager.WakeLock wakeLockNewWakeLock = powerManager.newWakeLock(1, "*telephony-radio*");
        this.mWakeLock = wakeLockNewWakeLock;
        wakeLockNewWakeLock.setReferenceCounted(false);
        PowerManager.WakeLock wakeLockNewWakeLock2 = powerManager.newWakeLock(1, "RILJ_ACK_WL");
        this.mAckWakeLock = wakeLockNewWakeLock2;
        wakeLockNewWakeLock2.setReferenceCounted(false);
        this.mWakeLockTimeout = TelephonyProperties.wake_lock_timeout().orElse(Integer.valueOf(ServiceStateTracker.DEFAULT_GPRS_CHECK_PERIOD_MILLIS)).intValue();
        this.mAckWakeLockTimeout = TelephonyProperties.wake_lock_timeout().orElse(Integer.valueOf(ImsRttTextHandler.MAX_BUFFERING_DELAY_MILLIS)).intValue();
        this.mWakeLockCount = 0;
        this.mRILDefaultWorkSource = new WorkSource(context.getApplicationInfo().uid, context.getPackageName());
        this.mActiveWakelockWorkSource = new WorkSource();
        this.mOplusRILImpl = (IOplusRIL) OplusTelephonyFactory.getInstance().getFeature(IOplusRIL.DEFAULT, this.mContext, this.mPhoneId, this);
        TelephonyDevController.getInstance();
        if (sparseArray == null) {
            TelephonyDevController.registerRIL(this);
        }
        validateFeatureFlags();
        for (int i4 = 0; i4 <= 7; i4++) {
            if (isRadioVersion2_0() && !isRadioServiceSupported(i4)) {
                riljLog("Not initializing " + serviceToString(i4) + " (not supported)");
            } else {
                if (i4 == 0) {
                    getRadioProxy();
                } else if (sparseArray == null) {
                    getRadioServiceProxy(i4);
                }
                riljLog("HAL version of " + serviceToString(i4) + ": " + this.mHalVersion.get(Integer.valueOf(i4)));
            }
        }
    }

    private boolean isRadioVersion2_0() {
        Boolean bool = this.mIsRadioVersion20Cached;
        if (bool != null) {
            return bool.booleanValue();
        }
        for (int i = 1; i <= 7; i++) {
            if (isRadioServiceSupported(i)) {
                this.mIsRadioVersion20Cached = Boolean.TRUE;
                return true;
            }
        }
        this.mIsRadioVersion20Cached = Boolean.FALSE;
        return false;
    }

    private boolean isRadioServiceSupported(int i) {
        String str;
        if (i == 0) {
            return true;
        }
        switch (i) {
            case 1:
                str = IRadioData.DESCRIPTOR;
                break;
            case 2:
                str = IRadioMessaging.DESCRIPTOR;
                break;
            case 3:
                str = IRadioModem.DESCRIPTOR;
                break;
            case 4:
                str = IRadioNetwork.DESCRIPTOR;
                break;
            case 5:
                str = IRadioSim.DESCRIPTOR;
                break;
            case 6:
                str = IRadioVoice.DESCRIPTOR;
                break;
            case 7:
                str = IRadioIms.DESCRIPTOR;
                break;
            default:
                str = PhoneConfigurationManager.SSSS;
                break;
        }
        if (str.equals(PhoneConfigurationManager.SSSS)) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(str);
        sb.append('/');
        sb.append(HIDL_SERVICE_NAME[this.mPhoneId.intValue()]);
        return ServiceManager.isDeclared(sb.toString());
    }

    private void validateFeatureFlags() {
        PackageManager packageManager = this.mContext.getPackageManager();
        for (Map.Entry<String, Integer> entry : FEATURES_TO_SERVICES.entrySet()) {
            String key = entry.getKey();
            int iIntValue = entry.getValue().intValue();
            boolean zHasSystemFeature = packageManager.hasSystemFeature(key);
            boolean zIsRadioServiceSupported = isRadioServiceSupported(iIntValue);
            if (zHasSystemFeature && !zIsRadioServiceSupported) {
                riljLoge("Feature " + key + " is declared, but service " + serviceToString(iIntValue) + " is missing");
            }
            if (!zHasSystemFeature && zIsRadioServiceSupported) {
                riljLoge("Service " + serviceToString(iIntValue) + " is available, but feature " + key + " is not declared");
            }
        }
    }

    private boolean isRadioBugDetectionEnabled() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "enable_radio_bug_detection", 1) != 0;
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setOnNITZTime(Handler handler, int i, Object obj) {
        super.setOnNITZTime(handler, i, obj);
        if (this.mLastNITZTimeInfo != null) {
            this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult((Object) null, this.mLastNITZTimeInfo, (Throwable) null));
        }
    }

    private void addRequest(RILRequest rILRequest) {
        acquireWakeLock(rILRequest, 0);
        Trace.asyncTraceForTrackBegin(2097152L, "RIL", rILRequest.mSerial + "> " + RILUtils.requestToString(rILRequest.mRequest), rILRequest.mSerial);
        synchronized (this.mRequestList) {
            rILRequest.mStartTimeMs = SystemClock.elapsedRealtime();
            this.mRequestList.append(rILRequest.mSerial, rILRequest);
        }
    }

    private RILRequest obtainRequest(int i, Message message, WorkSource workSource) {
        RILRequest rILRequestObtain = RILRequest.obtain(i, message, workSource);
        addRequest(rILRequestObtain);
        return rILRequestObtain;
    }

    private RILRequest obtainRequest(int i, Message message, WorkSource workSource, Object... objArr) {
        RILRequest rILRequestObtain = RILRequest.obtain(i, message, workSource, objArr);
        addRequest(rILRequestObtain);
        return rILRequestObtain;
    }

    private void handleRadioProxyExceptionForRR(int i, String str, Exception exc) {
        riljLoge(str + ": " + exc);
        exc.printStackTrace();
        this.mIsRadioProxyInitialized = false;
        resetProxyAndRequestList(i);
    }

    private void radioServiceInvokeHelper(int i, RILRequest rILRequest, String str, FunctionalUtils.ThrowingRunnable throwingRunnable) {
        int i2 = rILRequest.mSerial;
        try {
            throwingRunnable.runOrThrow();
        } catch (RuntimeException e) {
            riljLoge(str + " RuntimeException: " + e);
            RILRequest rILRequestProcessResponseInternal = processResponseInternal(i, i2, 39, 0);
            if (rILRequestProcessResponseInternal != null) {
                processResponseDoneInternal(rILRequestProcessResponseInternal, 39, 0, null);
            }
        } catch (Exception e2) {
            handleRadioProxyExceptionForRR(i, str, e2);
        }
    }

    private boolean canMakeRequest(String str, RadioServiceProxy radioServiceProxy, Message message, HalVersion halVersion) {
        return canMakeRequest(str, radioServiceProxy, message, halVersion, null);
    }

    private boolean canMakeRequest(String str, RadioServiceProxy radioServiceProxy, Message message, HalVersion halVersion, HalVersion halVersion2) {
        int i;
        if (radioServiceProxy instanceof RadioDataProxy) {
            i = 1;
        } else if (radioServiceProxy instanceof RadioMessagingProxy) {
            i = 2;
        } else if (radioServiceProxy instanceof RadioModemProxy) {
            i = 3;
        } else if (radioServiceProxy instanceof RadioNetworkProxy) {
            i = 4;
        } else if (radioServiceProxy instanceof RadioSimProxy) {
            i = 5;
        } else if (radioServiceProxy instanceof RadioVoiceProxy) {
            i = 6;
        } else {
            i = radioServiceProxy instanceof RadioImsProxy ? 7 : 0;
        }
        if (radioServiceProxy == null || radioServiceProxy.isEmpty()) {
            riljLoge(String.format("Unable to complete %s because service %s is not available.", str, serviceToString(i)));
            if (message != null) {
                AsyncResult.forMessage(message, (Object) null, CommandException.fromRilErrno(1));
                message.sendToTarget();
            }
            return false;
        }
        if (this.mHalVersion.get(Integer.valueOf(i)).less(halVersion)) {
            riljLoge(String.format("%s not supported on service %s < %s.", str, serviceToString(i), halVersion));
            if (message != null) {
                AsyncResult.forMessage(message, (Object) null, CommandException.fromRilErrno(6));
                message.sendToTarget();
            }
            return false;
        }
        if (halVersion2 == null || !this.mHalVersion.get(Integer.valueOf(i)).greater(halVersion2)) {
            return true;
        }
        riljLoge(String.format("%s not supported on service %s > %s.", str, serviceToString(i), halVersion2));
        if (message != null) {
            AsyncResult.forMessage(message, (Object) null, CommandException.fromRilErrno(6));
            message.sendToTarget();
        }
        return false;
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIccCardStatus(Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("getIccCardStatus", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(1, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "getIccCardStatus", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda73
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.getIccCardStatus(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPin(String str, Message message) {
        supplyIccPinForApp(str, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPinForApp(final String str, final String str2, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("supplyIccPinForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(2, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " aid = " + str2);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "supplyIccPinForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda20
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.supplyIccPinForApp(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), RILUtils.convertNullToEmptyString(str2));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPuk(String str, String str2, Message message) {
        supplyIccPukForApp(str, str2, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPukForApp(String str, final String str2, final String str3, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("supplyIccPukForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(3, message, this.mRILDefaultWorkSource);
            final String strConvertNullToEmptyString = RILUtils.convertNullToEmptyString(str);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " isPukEmpty = " + strConvertNullToEmptyString.isEmpty() + " aid = " + str3);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "supplyIccPukForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda129
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.supplyIccPukForApp(rILRequestObtainRequest.mSerial, strConvertNullToEmptyString, RILUtils.convertNullToEmptyString(str2), RILUtils.convertNullToEmptyString(str3));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPin2(String str, Message message) {
        supplyIccPin2ForApp(str, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPin2ForApp(final String str, final String str2, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("supplyIccPin2ForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(4, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " aid = " + str2);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "supplyIccPin2ForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda152
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.supplyIccPin2ForApp(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), RILUtils.convertNullToEmptyString(str2));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPuk2(String str, String str2, Message message) {
        supplyIccPuk2ForApp(str, str2, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPuk2ForApp(final String str, final String str2, final String str3, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("supplyIccPuk2ForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(5, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " aid = " + str3);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "supplyIccPuk2ForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda175
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.supplyIccPuk2ForApp(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), RILUtils.convertNullToEmptyString(str2), RILUtils.convertNullToEmptyString(str3));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPin(String str, String str2, Message message) {
        changeIccPinForApp(str, str2, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPinForApp(final String str, final String str2, final String str3, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("changeIccPinForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(6, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " oldPin = " + str + " newPin = " + str2 + " aid = " + str3);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "changeIccPinForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda1
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.changeIccPinForApp(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), RILUtils.convertNullToEmptyString(str2), RILUtils.convertNullToEmptyString(str3));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPin2(String str, String str2, Message message) {
        changeIccPin2ForApp(str, str2, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPin2ForApp(final String str, final String str2, final String str3, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("changeIccPin2ForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(7, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " oldPin = " + str + " newPin = " + str2 + " aid = " + str3);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "changeIccPin2ForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda128
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.changeIccPin2ForApp(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), RILUtils.convertNullToEmptyString(str2), RILUtils.convertNullToEmptyString(str3));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyNetworkDepersonalization(final String str, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("supplyNetworkDepersonalization", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(8, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " netpin = " + str);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "supplyNetworkDepersonalization", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda131
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.supplyNetworkDepersonalization(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplySimDepersonalization(final IccCardApplicationStatus.PersoSubState persoSubState, final String str, Message message) {
        HalVersion halVersion = this.mHalVersion.get(5);
        HalVersion halVersion2 = RADIO_HAL_VERSION_1_5;
        if (halVersion.less(halVersion2) && IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_SIM_NETWORK == persoSubState) {
            supplyNetworkDepersonalization(str, message);
            return;
        }
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("supplySimDepersonalization", radioSimProxy, message, halVersion2)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_APP_TOOLKIT_BUSY, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " controlKey = " + str + " persoType" + persoSubState);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "supplySimDepersonalization", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda167
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.supplySimDepersonalization(rILRequestObtainRequest.mSerial, persoSubState, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCurrentCalls(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("getCurrentCalls", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(9, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "getCurrentCalls", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda137
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.getCurrentCalls(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void dial(String str, boolean z, EmergencyNumber emergencyNumber, boolean z2, int i, Message message) {
        dial(str, z, emergencyNumber, z2, i, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void enableModem(final boolean z, Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("enableModem", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(146, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enable = " + z);
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "enableModem", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda87
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.enableModem(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSystemSelectionChannels(final List<RadioAccessSpecifier> list, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setSystemSelectionChannels", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(210, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " setSystemSelectionChannels= " + list);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setSystemSelectionChannels", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda6
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setSystemSelectionChannels(rILRequestObtainRequest.mSerial, list);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getSystemSelectionChannels(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getSystemSelectionChannels", radioNetworkProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(219, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " getSystemSelectionChannels");
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getSystemSelectionChannels", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda144
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getSystemSelectionChannels(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getModemStatus(Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("getModemStatus", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(147, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "getModemStatus", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda76
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.getModemStackStatus(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void dial(final String str, boolean z, EmergencyNumber emergencyNumber, boolean z2, final int i, final UUSInfo uUSInfo, Message message) {
        if (z && emergencyNumber != null) {
            emergencyDial(str, emergencyNumber, z2, i, uUSInfo, message);
            return;
        }
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("dial", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(10, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "dial", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda58
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.dial(rILRequestObtainRequest.mSerial, str, i, uUSInfo);
                }
            });
        }
    }

    private void emergencyDial(final String str, final EmergencyNumber emergencyNumber, final boolean z, final int i, final UUSInfo uUSInfo, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("emergencyDial", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(205, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "emergencyDial", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda180
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.emergencyDial(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), emergencyNumber, z, i, uUSInfo);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMSI(Message message) {
        getIMSIForApp(null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMSIForApp(final String str, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("getIMSIForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(11, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " aid = " + str);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "getIMSIForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda156
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.getImsiForApp(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void hangupConnection(final int i, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("hangupConnection", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(12, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " gsmIndex = " + i);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "hangupConnection", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda125
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.hangup(rILRequestObtainRequest.mSerial, i);
                }
            });
            this.mOplusRILImpl.hangupConnection(i);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void hangupWaitingOrBackground(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("hangupWaitingOrBackground", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(13, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "hangupWaitingOrBackground", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda38
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.hangupWaitingOrBackground(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void hangupForegroundResumeBackground(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("hangupForegroundResumeBackground", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(14, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "hangupForegroundResumeBackground", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda35
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.hangupForegroundResumeBackground(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void switchWaitingOrHoldingAndActive(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("switchWaitingOrHoldingAndActive", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(15, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "switchWaitingOrHoldingAndActive", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda9
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.switchWaitingOrHoldingAndActive(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void conference(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("conference", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(16, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "conference", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda111
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.conference(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void rejectCall(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("rejectCall", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(17, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "rejectCall", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda30
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.rejectCall(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getLastCallFailCause(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("getLastCallFailCause", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(18, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "getLastCallFailCause", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda89
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.getLastCallFailCause(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getSignalStrength(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getSignalStrength", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(19, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getSignalStrength", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda99
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getSignalStrength(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getVoiceRegistrationState(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getVoiceRegistrationState", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(20, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            final HalVersion compatVersion = getCompatVersion(20);
            riljLog("getVoiceRegistrationState: overrideHalVersion=" + compatVersion);
            if (this.mOplusRILImpl.getVoiceRegistrationState(rILRequestObtainRequest.mSerial)) {
                return;
            }
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getVoiceRegistrationState", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda21
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getVoiceRegistrationState(rILRequestObtainRequest.mSerial, compatVersion);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getDataRegistrationState(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getDataRegistrationState", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(21, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            final HalVersion compatVersion = getCompatVersion(21);
            riljLog("getDataRegistrationState: overrideHalVersion=" + compatVersion);
            if (this.mOplusRILImpl.getDataRegistrationState(rILRequestObtainRequest.mSerial)) {
                return;
            }
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getDataRegistrationState", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda42
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getDataRegistrationState(rILRequestObtainRequest.mSerial, compatVersion);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getOperator(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getOperator", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(22, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            if (this.mOplusRILImpl.getOperator(rILRequestObtainRequest.mSerial)) {
                return;
            }
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getOperator", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda39
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getOperator(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setRadioPower(final boolean z, final boolean z2, final boolean z3, Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("setRadioPower", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(23, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " on = " + z + " forEmergencyCall= " + z2 + " preferredForEmergencyCall=" + z3);
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "setRadioPower", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda118
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.setRadioPower(rILRequestObtainRequest.mSerial, z, z2, z3);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendDtmf(final char c, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("sendDtmf", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(24, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "sendDtmf", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda151
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.sendDtmf(rILRequestObtainRequest.mSerial, c + PhoneConfigurationManager.SSSS);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendSMS(final String str, final String str2, final Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("sendSMS", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(25, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            if (this.mOplusRILImpl.sendSMS(str, str2, rILRequestObtainRequest.mSerial)) {
                return;
            }
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "sendSMS", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda113
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$sendSMS$30(radioMessagingProxy, rILRequestObtainRequest, str, str2, message);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$sendSMS$30(RadioMessagingProxy radioMessagingProxy, RILRequest rILRequest, String str, String str2, Message message) throws Exception {
        radioMessagingProxy.sendSms(rILRequest.mSerial, str, str2);
        this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rILRequest.mSerial, 1, 1, getOutgoingSmsMessageId(message));
    }

    public static long getOutgoingSmsMessageId(Message message) {
        if (message == null) {
            return 0L;
        }
        Object obj = message.obj;
        if (obj instanceof SMSDispatcher.SmsTracker) {
            return ((SMSDispatcher.SmsTracker) obj).mMessageId;
        }
        return 0L;
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendSMSExpectMore(final String str, final String str2, final Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("sendSMSExpectMore", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(26, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            if (this.mOplusRILImpl.sendSMSExpectMore(str, str2, rILRequestObtainRequest.mSerial)) {
                return;
            }
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "sendSMSExpectMore", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda103
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$sendSMSExpectMore$31(radioMessagingProxy, rILRequestObtainRequest, str, str2, message);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$sendSMSExpectMore$31(RadioMessagingProxy radioMessagingProxy, RILRequest rILRequest, String str, String str2, Message message) throws Exception {
        radioMessagingProxy.sendSmsExpectMore(rILRequest.mSerial, str, str2);
        this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rILRequest.mSerial, 1, 1, getOutgoingSmsMessageId(message));
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setupDataCall(final int i, final DataProfile dataProfile, final boolean z, final int i2, final LinkProperties linkProperties, final int i3, final NetworkSliceInfo networkSliceInfo, final TrafficDescriptor trafficDescriptor, final boolean z2, Message message) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("setupDataCall", radioDataProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(27, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + ",reason=" + RILUtils.setupDataReasonToString(i2) + ",accessNetworkType=" + AccessNetworkConstants.AccessNetworkType.toString(i) + ",dataProfile=" + dataProfile + ",allowRoaming=" + z + ",linkProperties=" + linkProperties + ",pduSessionId=" + i3 + ",sliceInfo=" + networkSliceInfo + ",trafficDescriptor=" + trafficDescriptor + ",matchAllRuleAllowed=" + z2);
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "setupDataCall", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda19
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.setupDataCall(rILRequestObtainRequest.mSerial, i, dataProfile, z, i2, linkProperties, i3, networkSliceInfo, trafficDescriptor, z2);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccIO(int i, int i2, String str, int i3, int i4, int i5, String str2, String str3, Message message) {
        iccIOForApp(i, i2, str, i3, i4, i5, str2, str3, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccIOForApp(final int i, final int i2, String str, int i3, int i4, final int i5, String str2, final String str3, String str4, Message message) {
        final String str5;
        final int i6;
        final int i7;
        final String str6;
        final String str7;
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("iccIOForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(28, message, this.mRILDefaultWorkSource);
            if (TelephonyUtils.IS_DEBUGGABLE) {
                StringBuilder sb = new StringBuilder();
                sb.append(rILRequestObtainRequest.serialString());
                sb.append("> iccIO: ");
                sb.append(RILUtils.requestToString(rILRequestObtainRequest.mRequest));
                sb.append(" command = 0x");
                sb.append(Integer.toHexString(i));
                sb.append(" fileId = 0x");
                sb.append(Integer.toHexString(i2));
                sb.append(" path = ");
                str5 = str;
                sb.append(str5);
                sb.append(" p1 = ");
                i6 = i3;
                sb.append(i6);
                sb.append(" p2 = ");
                i7 = i4;
                sb.append(i7);
                sb.append(" p3 =  data = ");
                str6 = str2;
                sb.append(str6);
                sb.append(" aid = ");
                str7 = str4;
                sb.append(str7);
                riljLog(sb.toString());
            } else {
                str5 = str;
                i6 = i3;
                i7 = i4;
                str6 = str2;
                str7 = str4;
                riljLog(rILRequestObtainRequest.serialString() + "> iccIO: " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            }
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "iccIOForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda102
                public final void runOrThrow() throws RemoteException {
                    RadioSimProxy radioSimProxy2 = radioSimProxy;
                    RILRequest rILRequest = rILRequestObtainRequest;
                    radioSimProxy2.iccIoForApp(rILRequest.mSerial, i, i2, RILUtils.convertNullToEmptyString(str5), i6, i7, i5, RILUtils.convertNullToEmptyString(str6), RILUtils.convertNullToEmptyString(str3), RILUtils.convertNullToEmptyString(str7));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendUSSD(final String str, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("sendUSSD", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(29, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " ussd = *******");
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "sendUSSD", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda117
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.sendUssd(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void cancelPendingUssd(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("cancelPendingUssd", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(30, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "cancelPendingUssd", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda166
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.cancelPendingUssd(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCLIR(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("getCLIR", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(31, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "getCLIR", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda16
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.getClir(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCLIR(final int i, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("setCLIR", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(32, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " clirMode = " + i);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "setCLIR", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda53
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.setClir(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCallForwardStatus(final int i, final int i2, final String str, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("queryCallForwardStatus", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(33, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " cfReason = " + i + " serviceClass = " + i2);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "queryCallForwardStatus", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda50
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.getCallForwardStatus(rILRequestObtainRequest.mSerial, i, i2, str);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCallForward(final int i, final int i2, final int i3, final String str, final int i4, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("setCallForward", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(34, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " action = " + i + " cfReason = " + i2 + " serviceClass = " + i3 + " timeSeconds = " + i4);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "setCallForward", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda81
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.setCallForward(rILRequestObtainRequest.mSerial, i, i2, i3, str, i4);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCallWaiting(final int i, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("queryCallWaiting", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(35, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " serviceClass = " + i);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "queryCallWaiting", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda122
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.getCallWaiting(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCallWaiting(final boolean z, final int i, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("setCallWaiting", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(36, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enable = " + z + " serviceClass = " + i);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "setCallWaiting", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda13
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.setCallWaiting(rILRequestObtainRequest.mSerial, z, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acknowledgeLastIncomingGsmSms(final boolean z, final int i, Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("acknowledgeLastIncomingGsmSms", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(37, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " success = " + z + " cause = " + i);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "acknowledgeLastIncomingGsmSms", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda146
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.acknowledgeLastIncomingGsmSms(rILRequestObtainRequest.mSerial, z, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acceptCall(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("acceptCall", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(40, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "acceptCall", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda54
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$acceptCall$43(radioVoiceProxy, rILRequestObtainRequest);
                }
            });
            this.mOplusRILImpl.acceptCall();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$acceptCall$43(RadioVoiceProxy radioVoiceProxy, RILRequest rILRequest) throws Exception {
        radioVoiceProxy.acceptCall(rILRequest.mSerial);
        this.mMetrics.writeRilAnswer(this.mPhoneId.intValue(), rILRequest.mSerial);
    }

    public void removePhoneCallTimeoutMessage() {
        this.mOplusRILImpl.removePhoneCallTimeoutMessage();
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void deactivateDataCall(final int i, final int i2, Message message) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("deactivateDataCall", radioDataProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(41, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " cid = " + i + " reason = " + RILUtils.deactivateDataReasonToString(i2));
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "deactivateDataCall", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda17
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$deactivateDataCall$44(radioDataProxy, rILRequestObtainRequest, i, i2);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$deactivateDataCall$44(RadioDataProxy radioDataProxy, RILRequest rILRequest, int i, int i2) throws Exception {
        radioDataProxy.deactivateDataCall(rILRequest.mSerial, i, i2);
        this.mMetrics.writeRilDeactivateDataCall(this.mPhoneId.intValue(), rILRequest.mSerial, i, i2);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryFacilityLock(String str, String str2, int i, Message message) {
        queryFacilityLockForApp(str, str2, i, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryFacilityLockForApp(final String str, final String str2, final int i, final String str3, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("queryFacilityLockForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(42, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " facility = " + str + " serviceClass = " + i + " appId = " + str3);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "queryFacilityLockForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda55
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.getFacilityLockForApp(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), RILUtils.convertNullToEmptyString(str2), i, RILUtils.convertNullToEmptyString(str3));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setFacilityLock(String str, boolean z, String str2, int i, Message message) {
        setFacilityLockForApp(str, z, str2, i, null, message);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setFacilityLockForApp(final String str, final boolean z, final String str2, final int i, final String str3, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("setFacilityLockForApp", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(43, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " facility = " + str + " lockstate = " + z + " serviceClass = " + i + " appId = " + str3);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "setFacilityLockForApp", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda135
                public final void runOrThrow() throws RemoteException {
                    RadioSimProxy radioSimProxy2 = radioSimProxy;
                    RILRequest rILRequest = rILRequestObtainRequest;
                    String str4 = str;
                    radioSimProxy2.setFacilityLockForApp(rILRequest.mSerial, RILUtils.convertNullToEmptyString(str4), z, RILUtils.convertNullToEmptyString(str2), i, RILUtils.convertNullToEmptyString(str3));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeBarringPassword(final String str, final String str2, final String str3, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("changeBarringPassword", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(44, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + "facility = " + str);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "changeBarringPassword", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda169
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setBarringPassword(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), RILUtils.convertNullToEmptyString(str2), RILUtils.convertNullToEmptyString(str3));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getNetworkSelectionMode(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getNetworkSelectionMode", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(45, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            if (this.mOplusRILImpl.getNetworkSelectionMode(rILRequestObtainRequest.mSerial)) {
                return;
            }
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getNetworkSelectionMode", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda14
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getNetworkSelectionMode(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setNetworkSelectionModeAutomatic(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setNetworkSelectionModeAutomatic", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(46, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setNetworkSelectionModeAutomatic", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda7
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setNetworkSelectionModeAutomatic(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setNetworkSelectionModeManual(final String str, final int i, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setNetworkSelectionModeManual", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(47, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " operatorNumeric = " + str + ", ran = " + i);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setNetworkSelectionModeManual", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda29
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setNetworkSelectionModeManual(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getAvailableNetworks(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getAvailableNetworks", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(48, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getAvailableNetworks", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda149
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getAvailableNetworks(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void startNetworkScan(final NetworkScanRequest networkScanRequest, final Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("startNetworkScan", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final HalVersion compatVersion = getCompatVersion(142);
            riljLog("startNetworkScan: overrideHalVersion=" + compatVersion);
            final RILRequest rILRequestObtainRequest = obtainRequest(142, message, this.mRILDefaultWorkSource, networkScanRequest);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "startNetworkScan", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda97
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.startNetworkScan(rILRequestObtainRequest.mSerial, networkScanRequest, compatVersion, message);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void stopNetworkScan(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("stopNetworkScan", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(143, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "stopNetworkScan", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda101
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.stopNetworkScan(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void startDtmf(final char c, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("startDtmf", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(49, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "startDtmf", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda74
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.startDtmf(rILRequestObtainRequest.mSerial, c + PhoneConfigurationManager.SSSS);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void stopDtmf(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("stopDtmf", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(50, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "stopDtmf", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda136
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.stopDtmf(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void separateConnection(final int i, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("separateConnection", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(52, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " gsmIndex = " + i);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "separateConnection", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda140
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.separateConnection(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getBasebandVersion(Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("getBasebandVersion", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(51, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "getBasebandVersion", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda106
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.getBasebandVersion(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setMute(final boolean z, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("setMute", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(53, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enableMute = " + z);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "setMute", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda90
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.setMute(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getMute(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("getMute", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(54, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "getMute", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda75
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.getMute(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCLIP(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("queryCLIP", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(55, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "queryCLIP", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda63
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.getClip(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getDataCallList(Message message) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("getDataCallList", radioDataProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(57, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "getDataCallList", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda82
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.getDataCallList(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSuppServiceNotifications(final boolean z, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setSuppServiceNotifications", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(62, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enable = " + z);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setSuppServiceNotifications", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda69
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setSuppServiceNotifications(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void writeSmsToSim(final int i, final String str, final String str2, Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("writeSmsToSim", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(63, message, this.mRILDefaultWorkSource);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "writeSmsToSim", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda108
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.writeSmsToSim(rILRequestObtainRequest.mSerial, i, RILUtils.convertNullToEmptyString(str), RILUtils.convertNullToEmptyString(str2));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void deleteSmsOnSim(final int i, Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("deleteSmsOnSim", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(64, message, this.mRILDefaultWorkSource);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "deleteSmsOnSim", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda23
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.deleteSmsOnSim(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setBandMode(final int i, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setBandMode", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(65, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " bandMode = " + i);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setBandMode", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda132
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setBandMode(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryAvailableBandMode(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("queryAvailableBandMode", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(66, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "queryAvailableBandMode", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda68
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getAvailableBandModes(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendEnvelope(final String str, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("sendEnvelope", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(69, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " contents = " + str);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "sendEnvelope", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda71
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.sendEnvelope(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendTerminalResponse(final String str, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("sendTerminalResponse", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(70, message, this.mRILDefaultWorkSource);
            StringBuilder sb = new StringBuilder();
            sb.append(rILRequestObtainRequest.serialString());
            sb.append("> ");
            sb.append(RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            sb.append(" contents = ");
            sb.append(TelephonyUtils.IS_DEBUGGABLE ? str : RILUtils.convertToCensoredTerminalResponse(str));
            riljLog(sb.toString());
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "sendTerminalResponse", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda86
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.sendTerminalResponseToSim(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendEnvelopeWithStatus(final String str, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("sendEnvelopeWithStatus", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(107, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " contents = " + str);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "sendEnvelopeWithStatus", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda64
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.sendEnvelopeWithStatus(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void explicitCallTransfer(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("explicitCallTransfer", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(72, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "explicitCallTransfer", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda52
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.explicitCallTransfer(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setPreferredNetworkType(int i, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setPreferredNetworkType", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(73, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " networkType = " + i);
            this.mAllowedNetworkTypesBitmask = RadioAccessFamily.getRafFromNetworkType(i);
            this.mMetrics.writeSetPreferredNetworkType(this.mPhoneId.intValue(), i);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setPreferredNetworkType", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda104
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$setPreferredNetworkType$71(radioNetworkProxy, rILRequestObtainRequest);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setPreferredNetworkType$71(RadioNetworkProxy radioNetworkProxy, RILRequest rILRequest) throws Exception {
        radioNetworkProxy.setPreferredNetworkTypeBitmap(rILRequest.mSerial, this.mAllowedNetworkTypesBitmask);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getPreferredNetworkType(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getPreferredNetworkType", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(74, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getPreferredNetworkType", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda88
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getAllowedNetworkTypesBitmap(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setAllowedNetworkTypesBitmap(int i, Message message) {
        HalVersion halVersion = this.mHalVersion.get(4);
        HalVersion halVersion2 = RADIO_HAL_VERSION_1_6;
        if (halVersion.less(halVersion2)) {
            setPreferredNetworkType(RadioAccessFamily.getNetworkTypeFromRaf(i), message);
            return;
        }
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setAllowedNetworkTypesBitmap", radioNetworkProxy, message, halVersion2)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(222, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            this.mAllowedNetworkTypesBitmask = i;
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setAllowedNetworkTypesBitmap", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda142
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$setAllowedNetworkTypesBitmap$73(radioNetworkProxy, rILRequestObtainRequest);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setAllowedNetworkTypesBitmap$73(RadioNetworkProxy radioNetworkProxy, RILRequest rILRequest) throws Exception {
        radioNetworkProxy.setAllowedNetworkTypesBitmap(rILRequest.mSerial, this.mAllowedNetworkTypesBitmask);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getAllowedNetworkTypesBitmap(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getAllowedNetworkTypesBitmap", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(223, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getAllowedNetworkTypesBitmap", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda159
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getAllowedNetworkTypesBitmap(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setLocationUpdates(final boolean z, WorkSource workSource, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setLocationUpdates", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(76, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enable = " + z);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setLocationUpdates", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda173
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setLocationUpdates(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void isNrDualConnectivityEnabled(Message message, WorkSource workSource) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("isNrDualConnectivityEnabled", radioNetworkProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(BerTlv.BER_EVENT_DOWNLOAD_TAG, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "isNrDualConnectivityEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda31
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.isNrDualConnectivityEnabled(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setNrDualConnectivityState(final int i, Message message, WorkSource workSource) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setNrDualConnectivityState", radioNetworkProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enable = " + i);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setNrDualConnectivityState", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda80
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setNrDualConnectivityState(rILRequestObtainRequest.mSerial, (byte) i);
                }
            });
        }
    }

    private void setVoNrEnabled(boolean z) {
        SystemProperties.set("persist.radio.is_vonr_enabled_" + this.mPhoneId, String.valueOf(z));
    }

    private boolean isVoNrEnabled() {
        return SystemProperties.getBoolean("persist.radio.is_vonr_enabled_" + this.mPhoneId, true);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void isVoNrEnabled(Message message, WorkSource workSource) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (!canMakeRequest("isVoNrEnabled", radioVoiceProxy, null, RADIO_HAL_VERSION_2_0)) {
            boolean zIsVoNrEnabled = isVoNrEnabled();
            if (message != null) {
                AsyncResult.forMessage(message, Boolean.valueOf(zIsVoNrEnabled), (Throwable) null);
                message.sendToTarget();
                return;
            }
            return;
        }
        final RILRequest rILRequestObtainRequest = obtainRequest(226, message, getDefaultWorkSourceIfInvalid(workSource));
        riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
        radioServiceInvokeHelper(6, rILRequestObtainRequest, "isVoNrEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda127
            public final void runOrThrow() throws RemoteException {
                radioVoiceProxy.isVoNrEnabled(rILRequestObtainRequest.mSerial);
            }
        });
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setVoNrEnabled(final boolean z, Message message, WorkSource workSource) {
        setVoNrEnabled(z);
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (!canMakeRequest("setVoNrEnabled", radioVoiceProxy, null, RADIO_HAL_VERSION_2_0)) {
            isNrDualConnectivityEnabled(null, workSource);
            if (message != null) {
                AsyncResult.forMessage(message, (Object) null, (Throwable) null);
                message.sendToTarget();
                return;
            }
            return;
        }
        final RILRequest rILRequestObtainRequest = obtainRequest(225, message, getDefaultWorkSourceIfInvalid(workSource));
        riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
        radioServiceInvokeHelper(6, rILRequestObtainRequest, "setVoNrEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda143
            public final void runOrThrow() throws RemoteException {
                radioVoiceProxy.setVoNrEnabled(rILRequestObtainRequest.mSerial, z);
            }
        });
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaSubscriptionSource(final int i, Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("setCdmaSubscriptionSource", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(77, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " cdmaSubscription = " + i);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "setCdmaSubscriptionSource", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda40
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.setCdmaSubscriptionSource(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCdmaRoamingPreference(Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("queryCdmaRoamingPreference", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(79, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "queryCdmaRoamingPreference", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda109
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getCdmaRoamingPreference(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaRoamingPreference(final int i, Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setCdmaRoamingPreference", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(78, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " cdmaRoamingType = " + i);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setCdmaRoamingPreference", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda27
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setCdmaRoamingPreference(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryTTYMode(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("queryTTYMode", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(81, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "queryTTYMode", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda4
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.getTtyMode(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setTTYMode(final int i, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("setTTYMode", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(80, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " ttyMode = " + i);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "setTTYMode", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda124
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.setTtyMode(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setPreferredVoicePrivacy(final boolean z, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("setPreferredVoicePrivacy", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(82, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enable = " + z);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "setPreferredVoicePrivacy", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda49
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.setPreferredVoicePrivacy(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getPreferredVoicePrivacy(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("getPreferredVoicePrivacy", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(83, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "getPreferredVoicePrivacy", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda2
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.getPreferredVoicePrivacy(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendCDMAFeatureCode(final String str, Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("sendCDMAFeatureCode", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(84, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " featureCode = " + Rlog.pii("RILJ", str));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "sendCDMAFeatureCode", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda133
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.sendCdmaFeatureCode(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendBurstDtmf(final String str, final int i, final int i2, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("sendBurstDtmf", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(85, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " dtmfString = " + str + " on = " + i + " off = " + i2);
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "sendBurstDtmf", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda44
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.sendBurstDtmf(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), i, i2);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendCdmaSMSExpectMore(final byte[] bArr, final Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("sendCdmaSMSExpectMore", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(NetworkStackConstants.IPV4_OPTION_TYPE_ROUTER_ALERT, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "sendCdmaSMSExpectMore", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda145
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$sendCdmaSMSExpectMore$89(radioMessagingProxy, rILRequestObtainRequest, bArr, message);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$sendCdmaSMSExpectMore$89(RadioMessagingProxy radioMessagingProxy, RILRequest rILRequest, byte[] bArr, Message message) throws Exception {
        radioMessagingProxy.sendCdmaSmsExpectMore(rILRequest.mSerial, bArr);
        if (this.mHalVersion.get(2).greaterOrEqual(RADIO_HAL_VERSION_1_5)) {
            this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rILRequest.mSerial, 2, 2, getOutgoingSmsMessageId(message));
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendCdmaSms(final byte[] bArr, final Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("sendCdmaSms", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(87, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "sendCdmaSms", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda178
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$sendCdmaSms$90(radioMessagingProxy, rILRequestObtainRequest, bArr, message);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$sendCdmaSms$90(RadioMessagingProxy radioMessagingProxy, RILRequest rILRequest, byte[] bArr, Message message) throws Exception {
        radioMessagingProxy.sendCdmaSms(rILRequest.mSerial, bArr);
        this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rILRequest.mSerial, 2, 2, getOutgoingSmsMessageId(message));
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acknowledgeLastIncomingCdmaSms(final boolean z, final int i, Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("acknowledgeLastIncomingCdmaSms", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(88, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " success = " + z + " cause = " + i);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "acknowledgeLastIncomingCdmaSms", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda77
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.acknowledgeLastIncomingCdmaSms(rILRequestObtainRequest.mSerial, z, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getGsmBroadcastConfig(Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("getGsmBroadcastConfig", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(89, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "getGsmBroadcastConfig", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda126
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.getGsmBroadcastConfig(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setGsmBroadcastConfig(final SmsBroadcastConfigInfo[] smsBroadcastConfigInfoArr, Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("setGsmBroadcastConfig", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(90, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " with " + smsBroadcastConfigInfoArr.length + " configs : ");
            for (SmsBroadcastConfigInfo smsBroadcastConfigInfo : smsBroadcastConfigInfoArr) {
                riljLog(smsBroadcastConfigInfo.toString());
            }
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "setGsmBroadcastConfig", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda177
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.setGsmBroadcastConfig(rILRequestObtainRequest.mSerial, smsBroadcastConfigInfoArr);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setGsmBroadcastActivation(final boolean z, Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("setGsmBroadcastActivation", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(91, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " activate = " + z);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "setGsmBroadcastActivation", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda84
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.setGsmBroadcastActivation(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCdmaBroadcastConfig(Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("getCdmaBroadcastConfig", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(92, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "getCdmaBroadcastConfig", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda157
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.getCdmaBroadcastConfig(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaBroadcastConfig(final CdmaSmsBroadcastConfigInfo[] cdmaSmsBroadcastConfigInfoArr, Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("setCdmaBroadcastConfig", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            if (Build.isMtkPlatform() && cdmaSmsBroadcastConfigInfoArr.length == 0) {
                if (message != null) {
                    riljLog("setCdmaBroadcastConfig with 0 configs");
                    AsyncResult.forMessage(message, (Object) null, (Throwable) null);
                    message.sendToTarget();
                    return;
                }
                return;
            }
            final RILRequest rILRequestObtainRequest = obtainRequest(93, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " with " + cdmaSmsBroadcastConfigInfoArr.length + " configs : ");
            for (CdmaSmsBroadcastConfigInfo cdmaSmsBroadcastConfigInfo : cdmaSmsBroadcastConfigInfoArr) {
                riljLog(cdmaSmsBroadcastConfigInfo.toString());
            }
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "setCdmaBroadcastConfig", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda91
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.setCdmaBroadcastConfig(rILRequestObtainRequest.mSerial, cdmaSmsBroadcastConfigInfoArr);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaBroadcastActivation(final boolean z, Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("setCdmaBroadcastActivation", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(94, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " activate = " + z);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "setCdmaBroadcastActivation", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda116
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.setCdmaBroadcastActivation(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCDMASubscription(Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("getCDMASubscription", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(95, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "getCDMASubscription", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda78
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.getCdmaSubscription(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void writeSmsToRuim(final int i, final byte[] bArr, Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("writeSmsToRuim", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(96, message, this.mRILDefaultWorkSource);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "writeSmsToRuim", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda57
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.writeSmsToRuim(rILRequestObtainRequest.mSerial, i, bArr);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void deleteSmsOnRuim(final int i, Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("deleteSmsOnRuim", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(97, message, this.mRILDefaultWorkSource);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "deleteSmsOnRuim", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda72
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.deleteSmsOnRuim(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getDeviceIdentity(Message message) {
        RIL ril;
        Message message2;
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        IOplusNetworkManager iOplusNetworkManager = (IOplusNetworkManager) OplusTelephonyFactory.getInstance().getFeature(IOplusNetworkManager.DEFAULT, new Object[0]);
        if (iOplusNetworkManager != null && iOplusNetworkManager.hasFeatureRadioHalCompatible(this.mPhoneId.intValue())) {
            ril = this;
            message2 = message;
            if (!ril.canMakeRequest("getDeviceIdentity", radioModemProxy, message2, RADIO_HAL_VERSION_1_4, RADIO_HAL_VERSION_2_3)) {
                return;
            }
        } else {
            ril = this;
            message2 = message;
            if (!ril.canMakeRequest("getDeviceIdentity", radioModemProxy, message2, RADIO_HAL_VERSION_1_4, RADIO_HAL_VERSION_2_2)) {
                return;
            }
        }
        final RILRequest rILRequestObtainRequest = ril.obtainRequest(98, message2, ril.mRILDefaultWorkSource);
        ril.riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
        ril.radioServiceInvokeHelper(3, rILRequestObtainRequest, "getDeviceIdentity", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda18
            public final void runOrThrow() throws RemoteException {
                radioModemProxy.getDeviceIdentity(rILRequestObtainRequest.mSerial);
            }
        });
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getImei(Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("getImei", radioModemProxy, message, RADIO_HAL_VERSION_2_1)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(152, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "getImei", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda130
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.getImei(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void exitEmergencyCallbackMode(Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("exitEmergencyCallbackMode", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(99, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "exitEmergencyCallbackMode", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda172
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.exitEmergencyCallbackMode(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getSmscAddress(Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("getSmscAddress", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(100, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "getSmscAddress", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda92
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.getSmscAddress(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSmscAddress(final String str, Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("setSmscAddress", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(101, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " address = " + str);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "setSmscAddress", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda163
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.setSmscAddress(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void reportSmsMemoryStatus(final boolean z, Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("reportSmsMemoryStatus", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(102, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " available = " + z);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "reportSmsMemoryStatus", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda114
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.reportSmsMemoryStatus(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void reportStkServiceIsRunning(Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("reportStkServiceIsRunning", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(IOplusGsmCdmaCallTracker.EVENT_ACCEPT_COMPLETE, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "reportStkServiceIsRunning", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda98
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.reportStkServiceIsRunning(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCdmaSubscriptionSource(Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("getCdmaSubscriptionSource", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(104, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "getCdmaSubscriptionSource", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda60
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.getCdmaSubscriptionSource(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acknowledgeIncomingGsmSmsWithPdu(final boolean z, final String str, Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("acknowledgeIncomingGsmSmsWithPdu", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(106, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " success = " + z);
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "acknowledgeIncomingGsmSmsWithPdu", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda3
                public final void runOrThrow() throws RemoteException {
                    radioMessagingProxy.acknowledgeIncomingGsmSmsWithPdu(rILRequestObtainRequest.mSerial, z, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getVoiceRadioTechnology(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getVoiceRadioTechnology", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(108, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getVoiceRadioTechnology", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda83
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getVoiceRadioTechnology(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCellInfoList(Message message, WorkSource workSource) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getCellInfoList", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4) && !this.mOplusRILImpl.getCellInfoList(message)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(109, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            int callingUid = Binder.getCallingUid();
            int callingPid = Binder.getCallingPid();
            String nameForUid = this.mContext.getPackageManager().getNameForUid(callingUid);
            ((IOplusNetworkManager) OplusTelephonyFactory.getInstance().getFeature(IOplusNetworkManager.DEFAULT, new Object[0])).oemCountGetCellInfo(callingUid, callingPid, nameForUid);
            riljLog("[POWERSTATE] getCell Uid: " + callingUid + ", Pid: " + callingPid + ", Package: " + nameForUid);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getCellInfoList", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda48
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getCellInfoList(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCellInfoListRate(final int i, Message message, WorkSource workSource) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setCellInfoListRate", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(110, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " rateInMillis = " + i);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setCellInfoListRate", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda179
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setCellInfoListRate(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setInitialAttachApn(final DataProfile dataProfile, Message message) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("setInitialAttachApn", radioDataProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(111, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + dataProfile);
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "setInitialAttachApn", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda160
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.setInitialAttachApn(rILRequestObtainRequest.mSerial, dataProfile);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getImsRegistrationState(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getImsRegistrationState", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4, RADIO_HAL_VERSION_2_2)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_APN_TYPE_CONFLICT, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getImsRegistrationState", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda22
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getImsRegistrationState(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendImsGsmSms(final String str, final String str2, final int i, final int i2, final Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("sendImsGsmSms", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_INVALID_PCSCF_ADDR, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "sendImsGsmSms", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda148
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$sendImsGsmSms$115(radioMessagingProxy, rILRequestObtainRequest, str, str2, i, i2, message);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$sendImsGsmSms$115(RadioMessagingProxy radioMessagingProxy, RILRequest rILRequest, String str, String str2, int i, int i2, Message message) throws Exception {
        radioMessagingProxy.sendImsSms(rILRequest.mSerial, str, str2, null, i, i2);
        this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rILRequest.mSerial, 3, 1, getOutgoingSmsMessageId(message));
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendImsCdmaSms(final byte[] bArr, final int i, final int i2, final Message message) {
        final RadioMessagingProxy radioMessagingProxy = (RadioMessagingProxy) getRadioServiceProxy(RadioMessagingProxy.class);
        if (canMakeRequest("sendImsCdmaSms", radioMessagingProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_INVALID_PCSCF_ADDR, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(2, rILRequestObtainRequest, "sendImsCdmaSms", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda37
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$sendImsCdmaSms$116(radioMessagingProxy, rILRequestObtainRequest, bArr, i, i2, message);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$sendImsCdmaSms$116(RadioMessagingProxy radioMessagingProxy, RILRequest rILRequest, byte[] bArr, int i, int i2, Message message) throws Exception {
        radioMessagingProxy.sendImsSms(rILRequest.mSerial, null, null, bArr, i, i2);
        this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rILRequest.mSerial, 3, 2, getOutgoingSmsMessageId(message));
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccTransmitApduBasicChannel(final int i, final int i2, final int i3, final int i4, final int i5, String str, Message message) {
        final String str2;
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("iccTransmitApduBasicChannel", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_INTERNAL_CALL_PREEMPT_BY_HIGH_PRIO_APN, message, this.mRILDefaultWorkSource);
            if (TelephonyUtils.IS_DEBUGGABLE) {
                StringBuilder sb = new StringBuilder();
                sb.append(rILRequestObtainRequest.serialString());
                sb.append("> ");
                sb.append(RILUtils.requestToString(rILRequestObtainRequest.mRequest));
                sb.append(String.format(" cla = 0x%02X ins = 0x%02X", Integer.valueOf(i), Integer.valueOf(i2)));
                sb.append(String.format(" p1 = 0x%02X p2 = 0x%02X p3 = 0x%02X", Integer.valueOf(i3), Integer.valueOf(i4), Integer.valueOf(i5)));
                sb.append(" data = ");
                str2 = str;
                sb.append(str2);
                riljLog(sb.toString());
            } else {
                str2 = str;
                riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            }
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "iccTransmitApduBasicChannel", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda11
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.iccTransmitApduBasicChannel(rILRequestObtainRequest.mSerial, i, i2, i3, i4, i5, str2);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccOpenLogicalChannel(final String str, final int i, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("iccOpenLogicalChannel", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_EMM_ACCESS_BARRED, message, this.mRILDefaultWorkSource);
            if (TelephonyUtils.IS_DEBUGGABLE) {
                riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " aid = " + str + " p2 = " + i);
            } else {
                riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            }
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "iccOpenLogicalChannel", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda10
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.iccOpenLogicalChannel(rILRequestObtainRequest.mSerial, RILUtils.convertNullToEmptyString(str), i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccCloseLogicalChannel(final int i, final boolean z, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("iccCloseLogicalChannel", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_EMERGENCY_IFACE_ONLY, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " channel = " + i + " isEs10 = " + z);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "iccCloseLogicalChannel", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda162
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.iccCloseLogicalChannel(rILRequestObtainRequest.mSerial, i, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccTransmitApduLogicalChannel(final int i, final int i2, final int i3, final int i4, final int i5, final int i6, String str, boolean z, Message message) {
        final String str2;
        final boolean z2;
        if (i <= 0) {
            throw new RuntimeException("Invalid channel in iccTransmitApduLogicalChannel: " + i);
        }
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("iccTransmitApduLogicalChannel", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_IFACE_MISMATCH, message, this.mRILDefaultWorkSource);
            if (TelephonyUtils.IS_DEBUGGABLE) {
                StringBuilder sb = new StringBuilder();
                sb.append(rILRequestObtainRequest.serialString());
                sb.append("> ");
                sb.append(RILUtils.requestToString(rILRequestObtainRequest.mRequest));
                sb.append(String.format(" channel = %d", Integer.valueOf(i)));
                sb.append(String.format(" cla = 0x%02X ins = 0x%02X", Integer.valueOf(i2), Integer.valueOf(i3)));
                sb.append(String.format(" p1 = 0x%02X p2 = 0x%02X p3 = 0x%02X", Integer.valueOf(i4), Integer.valueOf(i5), Integer.valueOf(i6)));
                sb.append(" isEs10Command = ");
                z2 = z;
                sb.append(z2);
                sb.append(" data = ");
                str2 = str;
                sb.append(str2);
                riljLog(sb.toString());
            } else {
                str2 = str;
                z2 = z;
                riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            }
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "iccTransmitApduLogicalChannel", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda121
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.iccTransmitApduLogicalChannel(rILRequestObtainRequest.mSerial, i, i2, i3, i4, i5, i6, str2, z2);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void nvReadItem(final int i, Message message, WorkSource workSource) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("nvReadItem", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_COMPANION_IFACE_IN_USE, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " itemId = " + i);
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "nvReadItem", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda138
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.nvReadItem(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void nvWriteItem(final int i, final String str, Message message, WorkSource workSource) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("nvWriteItem", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_IP_ADDRESS_MISMATCH, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " itemId = " + i + " itemValue = " + str);
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "nvWriteItem", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda66
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.nvWriteItem(rILRequestObtainRequest.mSerial, i, RILUtils.convertNullToEmptyString(str));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void nvWriteCdmaPrl(final byte[] bArr, Message message) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("nvWriteCdmaPrl", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_IFACE_AND_POL_FAMILY_MISMATCH, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " PreferredRoamingList = 0x" + IccUtils.bytesToHexString(bArr));
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "nvWriteCdmaPrl", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda168
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.nvWriteCdmaPrl(rILRequestObtainRequest.mSerial, bArr);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void nvResetConfig(final int i, Message message) {
        if (!this.mFeatureFlags.cleanupCdma() || i == 1) {
            final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
            if (canMakeRequest("nvResetConfig", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
                final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_EMM_ACCESS_BARRED_INFINITE_RETRY, message, this.mRILDefaultWorkSource);
                riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " resetType = " + i);
                radioServiceInvokeHelper(3, rILRequestObtainRequest, "nvResetConfig", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda150
                    public final void runOrThrow() throws RemoteException {
                        radioModemProxy.nvResetConfig(rILRequestObtainRequest.mSerial, i);
                    }
                });
            }
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setUiccSubscription(final int i, final int i2, final int i3, final int i4, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("setUiccSubscription", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_AUTH_FAILURE_ON_EMERGENCY_CALL, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " slot = " + i + " appIndex = " + i2 + " subId = " + i3 + " subStatus = " + i4);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "setUiccSubscription", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda8
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.setUiccSubscription(rILRequestObtainRequest.mSerial, i, i2, i3, i4);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setDataAllowed(final boolean z, Message message) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("setDataAllowed", radioDataProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_INVALID_DNS_ADDR, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " allowed = " + z);
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "setDataAllowed", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda95
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.setDataAllowed(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getHardwareConfig(Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("getHardwareConfig", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(TelephonyProto.TelephonyEvent.RilSetupDataCallResponse.RilDataCallFailCause.PDP_FAIL_INVALID_PCSCF_OR_DNS_ADDRESS, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "getHardwareConfig", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda33
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.getHardwareConfig(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void requestIccSimAuthentication(final int i, final String str, final String str2, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("requestIccSimAuthentication", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(125, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "requestIccSimAuthentication", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda36
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.requestIccSimAuthentication(rILRequestObtainRequest.mSerial, i, RILUtils.convertNullToEmptyString(str), RILUtils.convertNullToEmptyString(str2));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setDataProfile(final DataProfile[] dataProfileArr, Message message) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("setDataProfile", radioDataProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(128, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " with data profiles : ");
            for (DataProfile dataProfile : dataProfileArr) {
                riljLog(Objects.toString(dataProfile, "DataProfile is null"));
            }
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "setDataProfile", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda67
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.setDataProfile(rILRequestObtainRequest.mSerial, dataProfileArr);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void requestShutdown(Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("requestShutdown", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(129, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "requestShutdown", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda123
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.requestShutdown(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void getRadioCapability(Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("getRadioCapability", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(130, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "getRadioCapability", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda112
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.getRadioCapability(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setRadioCapability(final RadioCapability radioCapability, Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("setRadioCapability", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(131, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " RadioCapability = " + radioCapability.toString());
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "setRadioCapability", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda100
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.setRadioCapability(rILRequestObtainRequest.mSerial, radioCapability);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setDataThrottling(Message message, WorkSource workSource, final int i, final long j) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("setDataThrottling", radioDataProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(NetworkStackConstants.VENDOR_SPECIFIC_IE_ID, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " dataThrottlingAction = " + i + " completionWindowMillis " + j);
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "setDataThrottling", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda107
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.setDataThrottling(rILRequestObtainRequest.mSerial, (byte) i, j);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getModemActivityInfo(Message message, WorkSource workSource) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("getModemActivityInfo", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(NetworkStackConstants.ICMPV6_NEIGHBOR_SOLICITATION, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "getModemActivityInfo", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda139
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$getModemActivityInfo$134(radioModemProxy, rILRequestObtainRequest);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$getModemActivityInfo$134(RadioModemProxy radioModemProxy, RILRequest rILRequest) throws Exception {
        radioModemProxy.getModemActivityInfo(rILRequest.mSerial);
        this.mRilHandler.sendMessageDelayed(this.mRilHandler.obtainMessage(5, Integer.valueOf(rILRequest.mSerial)), 2000L);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setAllowedCarriers(final CarrierRestrictionRules carrierRestrictionRules, Message message, WorkSource workSource) {
        carrierRestrictionRules.getClass();
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("setAllowedCarriers", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(NetworkStackConstants.ICMPV6_NEIGHBOR_ADVERTISEMENT, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " params: " + carrierRestrictionRules);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "setAllowedCarriers", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda105
                public final void runOrThrow() throws Exception {
                    this.f$0.lambda$setAllowedCarriers$135(radioSimProxy, rILRequestObtainRequest, carrierRestrictionRules);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setAllowedCarriers$135(RadioSimProxy radioSimProxy, RILRequest rILRequest, CarrierRestrictionRules carrierRestrictionRules) throws Exception {
        radioSimProxy.setAllowedCarriers(rILRequest.mSerial, carrierRestrictionRules, getHalVersion(5));
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getAllowedCarriers(Message message, WorkSource workSource) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("getAllowedCarriers", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(137, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "getAllowedCarriers", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda12
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.getAllowedCarriers(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendDeviceState(final int i, final boolean z, Message message) {
        final RadioModemProxy radioModemProxy = (RadioModemProxy) getRadioServiceProxy(RadioModemProxy.class);
        if (canMakeRequest("sendDeviceState", radioModemProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(138, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " " + i + ":" + z);
            radioServiceInvokeHelper(3, rILRequestObtainRequest, "sendDeviceState", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda110
                public final void runOrThrow() throws RemoteException {
                    radioModemProxy.sendDeviceState(rILRequestObtainRequest.mSerial, i, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setUnsolResponseFilter(final int i, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setUnsolResponseFilter", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(139, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " " + i);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setUnsolResponseFilter", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda120
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setIndicationFilter(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSignalStrengthReportingCriteria(final List<SignalThresholdInfo> list, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setSignalStrengthReportingCriteria", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(202, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setSignalStrengthReportingCriteria", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda170
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setSignalStrengthReportingCriteria(rILRequestObtainRequest.mSerial, list);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setLinkCapacityReportingCriteria(final int i, final int i2, final int i3, final int[] iArr, final int[] iArr2, final int i4, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setLinkCapacityReportingCriteria", radioNetworkProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(203, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setLinkCapacityReportingCriteria", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda46
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setLinkCapacityReportingCriteria(rILRequestObtainRequest.mSerial, i, i2, i3, iArr, iArr2, i4);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSimCardPower(final int i, Message message, WorkSource workSource) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("setSimCardPower", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(SmsMessage.MAX_USER_DATA_BYTES, message, getDefaultWorkSourceIfInvalid(workSource));
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " " + i);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "setSimCardPower", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda0
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.setSimCardPower(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCarrierInfoForImsiEncryption(final ImsiEncryptionInfo imsiEncryptionInfo, Message message) {
        imsiEncryptionInfo.getClass();
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("setCarrierInfoForImsiEncryption", radioSimProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(141, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "setCarrierInfoForImsiEncryption", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda79
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.setCarrierInfoForImsiEncryption(rILRequestObtainRequest.mSerial, imsiEncryptionInfo);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void startNattKeepalive(final int i, final KeepalivePacketData keepalivePacketData, final int i2, final Message message) {
        keepalivePacketData.getClass();
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("startNattKeepalive", radioDataProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(144, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "startNattKeepalive", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda32
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.startKeepalive(rILRequestObtainRequest.mSerial, i, keepalivePacketData, i2, message);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void stopNattKeepalive(final int i, Message message) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("stopNattKeepalive", radioDataProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(145, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "stopNattKeepalive", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda47
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.stopKeepalive(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void enableUiccApplications(final boolean z, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("enableUiccApplications", radioSimProxy, message, RADIO_HAL_VERSION_1_5)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(BerTlv.BER_PROACTIVE_COMMAND_TAG, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " " + z);
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "enableUiccApplications", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda147
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.enableUiccApplications(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void areUiccApplicationsEnabled(Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("areUiccApplicationsEnabled", radioSimProxy, message, RADIO_HAL_VERSION_1_5)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(209, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "areUiccApplicationsEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda51
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.areUiccApplicationsEnabled(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public boolean canToggleUiccApplicationsEnablement() {
        return canMakeRequest("canToggleUiccApplicationsEnablement", getRadioServiceProxy(RadioSimProxy.class), null, RADIO_HAL_VERSION_1_5);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void handleCallSetupRequestFromSim(final boolean z, Message message) {
        final RadioVoiceProxy radioVoiceProxy = (RadioVoiceProxy) getRadioServiceProxy(RadioVoiceProxy.class);
        if (canMakeRequest("handleCallSetupRequestFromSim", radioVoiceProxy, message, RADIO_HAL_VERSION_1_4)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(71, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(6, rILRequestObtainRequest, "handleCallSetupRequestFromSim", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda85
                public final void runOrThrow() throws RemoteException {
                    radioVoiceProxy.handleStkCallSetupRequestFromSim(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getBarringInfo(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getBarringInfo", radioNetworkProxy, message, RADIO_HAL_VERSION_1_5)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(211, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getBarringInfo", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda43
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getBarringInfo(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void allocatePduSessionId(Message message) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("allocatePduSessionId", radioDataProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(215, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "allocatePduSessionId", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda134
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.allocatePduSessionId(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void releasePduSessionId(Message message, final int i) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("releasePduSessionId", radioDataProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(216, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "releasePduSessionId", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda93
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.releasePduSessionId(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void startHandover(Message message, final int i) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("startHandover", radioDataProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(217, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "startHandover", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda96
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.startHandover(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void cancelHandover(Message message, final int i) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("cancelHandover", radioDataProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(218, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "cancelHandover", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda174
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.cancelHandover(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getSlicingConfig(Message message) {
        final RadioDataProxy radioDataProxy = (RadioDataProxy) getRadioServiceProxy(RadioDataProxy.class);
        if (canMakeRequest("getSlicingConfig", radioDataProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(224, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(1, rILRequestObtainRequest, "getSlicingConfig", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda153
                public final void runOrThrow() throws RemoteException {
                    radioDataProxy.getSlicingConfig(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void getSimPhonebookRecords(Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("getSimPhonebookRecords", radioSimProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(150, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "getSimPhonebookRecords", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda158
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.getSimPhonebookRecords(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void getSimPhonebookCapacity(Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("getSimPhonebookCapacity", radioSimProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(149, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "getSimPhonebookCapacity", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda176
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.getSimPhonebookCapacity(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void updateSimPhonebookRecord(final SimPhonebookRecord simPhonebookRecord, Message message) {
        final RadioSimProxy radioSimProxy = (RadioSimProxy) getRadioServiceProxy(RadioSimProxy.class);
        if (canMakeRequest("updateSimPhonebookRecord", radioSimProxy, message, RADIO_HAL_VERSION_1_6)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(151, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " with " + simPhonebookRecord.toString());
            radioServiceInvokeHelper(5, rILRequestObtainRequest, "updateSimPhonebookRecord", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda164
                public final void runOrThrow() throws RemoteException {
                    radioSimProxy.updateSimPhonebookRecords(rILRequestObtainRequest.mSerial, simPhonebookRecord);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setUsageSetting(Message message, final int i) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setUsageSetting", radioNetworkProxy, message, RADIO_HAL_VERSION_2_0)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(227, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setUsageSetting", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda61
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setUsageSetting(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getUsageSetting(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("getUsageSetting", radioNetworkProxy, message, RADIO_HAL_VERSION_2_0)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(228, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "getUsageSetting", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda15
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.getUsageSetting(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSrvccCallInfo(final SrvccConnection[] srvccConnectionArr, Message message) {
        final RadioImsProxy radioImsProxy = (RadioImsProxy) getRadioServiceProxy(RadioImsProxy.class);
        if (canMakeRequest("setSrvccCallInfo", radioImsProxy, message, RADIO_HAL_VERSION_2_0)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(233, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(7, rILRequestObtainRequest, "setSrvccCallInfo", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda41
                public final void runOrThrow() throws RemoteException {
                    radioImsProxy.setSrvccCallInfo(rILRequestObtainRequest.mSerial, RILUtils.convertToHalSrvccCall(srvccConnectionArr));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void updateImsRegistrationInfo(int i, int i2, int i3, int i4, Message message) {
        final RadioImsProxy radioImsProxy = (RadioImsProxy) getRadioServiceProxy(RadioImsProxy.class);
        if (canMakeRequest("updateImsRegistrationInfo", radioImsProxy, message, RADIO_HAL_VERSION_2_0)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(234, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " state=" + i + ", radioTech=" + i2 + ", suggested=" + i3 + ", cap=" + i4);
            final ImsRegistration imsRegistration = new ImsRegistration();
            imsRegistration.regState = RILUtils.convertImsRegistrationState(i);
            imsRegistration.accessNetworkType = RILUtils.convertImsRegistrationTech(i2);
            imsRegistration.suggestedAction = i3;
            imsRegistration.capabilities = RILUtils.convertImsCapability(i4);
            radioServiceInvokeHelper(7, rILRequestObtainRequest, "updateImsRegistrationInfo", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda165
                public final void runOrThrow() throws RemoteException {
                    radioImsProxy.updateImsRegistrationInfo(rILRequestObtainRequest.mSerial, imsRegistration);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void startImsTraffic(final int i, final int i2, final int i3, final int i4, Message message) {
        final RadioImsProxy radioImsProxy = (RadioImsProxy) getRadioServiceProxy(RadioImsProxy.class);
        if (canMakeRequest("startImsTraffic", radioImsProxy, message, RADIO_HAL_VERSION_2_0)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(235, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + "{" + i + ", " + i2 + ", " + i3 + ", " + i4 + "}");
            radioServiceInvokeHelper(7, rILRequestObtainRequest, "startImsTraffic", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda155
                public final void runOrThrow() throws RemoteException {
                    RadioImsProxy radioImsProxy2 = radioImsProxy;
                    RILRequest rILRequest = rILRequestObtainRequest;
                    radioImsProxy2.startImsTraffic(rILRequest.mSerial, i, RILUtils.convertImsTrafficType(i2), i3, RILUtils.convertImsTrafficDirection(i4));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void stopImsTraffic(final int i, Message message) {
        final RadioImsProxy radioImsProxy = (RadioImsProxy) getRadioServiceProxy(RadioImsProxy.class);
        if (canMakeRequest("stopImsTraffic", radioImsProxy, message, RADIO_HAL_VERSION_2_0)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(236, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + "{" + i + "}");
            radioServiceInvokeHelper(7, rILRequestObtainRequest, "stopImsTraffic", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda141
                public final void runOrThrow() throws RemoteException {
                    radioImsProxy.stopImsTraffic(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void triggerEpsFallback(final int i, Message message) {
        final RadioImsProxy radioImsProxy = (RadioImsProxy) getRadioServiceProxy(RadioImsProxy.class);
        if (canMakeRequest("triggerEpsFallback", radioImsProxy, message, RADIO_HAL_VERSION_2_0)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(238, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " reason=" + i);
            radioServiceInvokeHelper(7, rILRequestObtainRequest, "triggerEpsFallback", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda94
                public final void runOrThrow() throws RemoteException {
                    radioImsProxy.triggerEpsFallback(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendAnbrQuery(final int i, final int i2, final int i3, Message message) {
        final RadioImsProxy radioImsProxy = (RadioImsProxy) getRadioServiceProxy(RadioImsProxy.class);
        if (canMakeRequest("sendAnbrQuery", radioImsProxy, message, RADIO_HAL_VERSION_2_0)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(237, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(7, rILRequestObtainRequest, "sendAnbrQuery", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda171
                public final void runOrThrow() throws RemoteException {
                    radioImsProxy.sendAnbrQuery(rILRequestObtainRequest.mSerial, i, i2, i3);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setEmergencyMode(final int i, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setEmergencyMode", radioNetworkProxy, message, RADIO_HAL_VERSION_2_1)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(229, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " mode=" + EmergencyConstants.emergencyModeToString(i));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setEmergencyMode", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda34
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setEmergencyMode(rILRequestObtainRequest.mSerial, i);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void triggerEmergencyNetworkScan(final int[] iArr, final int i, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("triggerEmergencyNetworkScan", radioNetworkProxy, message, RADIO_HAL_VERSION_2_1)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(230, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " networkType=" + RILUtils.accessNetworkTypesToString(iArr) + ", scanType=" + RILUtils.scanTypeToString(i));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "triggerEmergencyNetworkScan", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda119
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.triggerEmergencyNetworkScan(rILRequestObtainRequest.mSerial, RILUtils.convertEmergencyNetworkScanTrigger(iArr, i));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void cancelEmergencyNetworkScan(final boolean z, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("cancelEmergencyNetworkScan", radioNetworkProxy, message, RADIO_HAL_VERSION_2_1)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(231, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " resetScan=" + z);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "cancelEmergencyNetworkScan", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda26
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.cancelEmergencyNetworkScan(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void exitEmergencyMode(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("exitEmergencyMode", radioNetworkProxy, message, RADIO_HAL_VERSION_2_1)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(232, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "exitEmergencyMode", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda24
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.exitEmergencyMode(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setNullCipherAndIntegrityEnabled(final boolean z, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setNullCipherAndIntegrityEnabled", radioNetworkProxy, message, RADIO_HAL_VERSION_2_1)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(239, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setNullCipherAndIntegrityEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda115
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setNullCipherAndIntegrityEnabled(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void isNullCipherAndIntegrityEnabled(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("isNullCipherAndIntegrityEnabled", radioNetworkProxy, message, RADIO_HAL_VERSION_2_1)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(CallFailCause.DIAL_MODIFIED_TO_SS, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "isNullCipherAndIntegrityEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda56
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.isNullCipherAndIntegrityEnabled(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void updateImsCallStatus(final List<ImsCallInfo> list, Message message) {
        final RadioImsProxy radioImsProxy = (RadioImsProxy) getRadioServiceProxy(RadioImsProxy.class);
        if (canMakeRequest("updateImsCallStatus", radioImsProxy, message, RADIO_HAL_VERSION_2_0)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(240, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " " + list);
            radioServiceInvokeHelper(7, rILRequestObtainRequest, "updateImsCallStatus", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda59
                public final void runOrThrow() throws RemoteException {
                    radioImsProxy.updateImsCallStatus(rILRequestObtainRequest.mSerial, RILUtils.convertImsCallInfo(list));
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setN1ModeEnabled(final boolean z, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setN1ModeEnabled", radioNetworkProxy, message, RADIO_HAL_VERSION_2_1)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(CallFailCause.FDN_BLOCKED, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enable=" + z);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setN1ModeEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda45
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setN1ModeEnabled(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void isN1ModeEnabled(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("isN1ModeEnabled", radioNetworkProxy, message, RADIO_HAL_VERSION_2_1)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(242, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "isN1ModeEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda28
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.isN1ModeEnabled(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCellularIdentifierTransparencyEnabled(final boolean z, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setCellularIdentifierTransparencyEnabled", radioNetworkProxy, message, RADIO_HAL_VERSION_2_2)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(CallFailCause.RADIO_OFF, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enable=" + z);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setCellularIdentifierTransparencyEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda161
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setCellularIdentifierTransparencyEnabled(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void isCellularIdentifierTransparencyEnabled(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("isCellularIdentifierTransparencyEnabled", radioNetworkProxy, message, RADIO_HAL_VERSION_2_2)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(CallFailCause.DIAL_MODIFIED_TO_DIAL, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "isCellularIdentifierTransparencyEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda25
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.isCellularIdentifierTransparencyEnabled(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSecurityAlgorithmsUpdatedEnabled(final boolean z, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("setSecurityAlgorithmsUpdatedEnabled", radioNetworkProxy, message, RADIO_HAL_VERSION_2_2)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(248, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " enable=" + z);
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "setSecurityAlgorithmsUpdatedEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda65
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.setSecurityAlgorithmsUpdatedEnabled(rILRequestObtainRequest.mSerial, z);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void isSecurityAlgorithmsUpdatedEnabled(Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (canMakeRequest("isSecurityAlgorithmsUpdatedEnabled", radioNetworkProxy, message, RADIO_HAL_VERSION_2_2)) {
            final RILRequest rILRequestObtainRequest = obtainRequest(CallFailCause.NO_VALID_SIM, message, this.mRILDefaultWorkSource);
            riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest));
            radioServiceInvokeHelper(4, rILRequestObtainRequest, "isSecurityAlgorithmsUpdatedEnabled", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda5
                public final void runOrThrow() throws RemoteException {
                    radioNetworkProxy.isSecurityAlgorithmsUpdatedEnabled(rILRequestObtainRequest.mSerial);
                }
            });
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSatellitePlmn(int i, final List<String> list, final List<String> list2, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (getHalVersion(4).less(RADIO_HAL_VERSION_2_3)) {
            riljLog("setSatellitePlmn: SatelliteModemInterface is used.");
            SatelliteModemInterface.getInstance().setSatellitePlmn(i, list, list2, message);
            return;
        }
        final RILRequest rILRequestObtainRequest = obtainRequest(CallFailCause.NETWORK_RESP_TIMEOUT, message, this.mRILDefaultWorkSource);
        riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " simSlot=" + i + " carrierPlmnList=" + list + " allSatellitePlmnList=" + list2);
        radioServiceInvokeHelper(4, rILRequestObtainRequest, "setSatellitePlmn", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda62
            public final void runOrThrow() throws RemoteException {
                radioNetworkProxy.setSatellitePlmn(rILRequestObtainRequest.mSerial, list, list2);
            }
        });
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSatelliteEnabledForCarrier(int i, final boolean z, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (getHalVersion(4).less(RADIO_HAL_VERSION_2_3)) {
            riljLog("setSatelliteEnabledForCarrier: SatelliteModemInterface is used.");
            SatelliteModemInterface.getInstance().requestSetSatelliteEnabledForCarrier(i, z, message);
            return;
        }
        final RILRequest rILRequestObtainRequest = obtainRequest(CallFailCause.NETWORK_REJECT, message, this.mRILDefaultWorkSource);
        riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " simSlot=" + i + " satelliteEnabled=" + z);
        radioServiceInvokeHelper(4, rILRequestObtainRequest, "setSatelliteEnabledForCarrier", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda154
            public final void runOrThrow() throws RemoteException {
                radioNetworkProxy.setSatelliteEnabledForCarrier(rILRequestObtainRequest.mSerial, z);
            }
        });
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void isSatelliteEnabledForCarrier(int i, Message message) {
        final RadioNetworkProxy radioNetworkProxy = (RadioNetworkProxy) getRadioServiceProxy(RadioNetworkProxy.class);
        if (getHalVersion(4).less(RADIO_HAL_VERSION_2_3)) {
            riljLog("isSatelliteEnabledForCarrier: SatelliteModemInterface is used.");
            SatelliteModemInterface.getInstance().requestIsSatelliteEnabledForCarrier(i, message);
            return;
        }
        final RILRequest rILRequestObtainRequest = obtainRequest(CallFailCause.RADIO_ACCESS_FAILURE, message, this.mRILDefaultWorkSource);
        riljLog(rILRequestObtainRequest.serialString() + "> " + RILUtils.requestToString(rILRequestObtainRequest.mRequest) + " simSlot=" + i);
        radioServiceInvokeHelper(4, rILRequestObtainRequest, "isSatelliteEnabledForCarrier", new FunctionalUtils.ThrowingRunnable() { // from class: com.android.internal.telephony.RIL$$ExternalSyntheticLambda70
            public final void runOrThrow() throws RemoteException {
                radioNetworkProxy.isSatelliteEnabledForCarrier(rILRequestObtainRequest.mSerial);
            }
        });
    }

    void processIndication(int i, int i2) {
        if (i2 == 1) {
            sendAck(i);
            riljLog("Unsol response received; Sending ack to ril.cpp");
        }
    }

    void processRequestAck(int i) {
        RILRequest rILRequest;
        synchronized (this.mRequestList) {
            rILRequest = this.mRequestList.get(i);
        }
        if (rILRequest == null) {
            riljLogw("processRequestAck: Unexpected solicited ack response! serial: " + i);
            return;
        }
        decrementWakeLock(rILRequest);
        riljLog(rILRequest.serialString() + " Ack < " + RILUtils.requestToString(rILRequest.mRequest));
    }

    public RILRequest processResponse(RadioResponseInfo radioResponseInfo) {
        return processResponseInternal(0, radioResponseInfo.serial, radioResponseInfo.error, radioResponseInfo.type);
    }

    public RILRequest processResponse_1_6(android.hardware.radio.V1_6.RadioResponseInfo radioResponseInfo) {
        return processResponseInternal(0, radioResponseInfo.serial, radioResponseInfo.error, radioResponseInfo.type);
    }

    public RILRequest processResponse(int i, android.hardware.radio.RadioResponseInfo radioResponseInfo) {
        return processResponseInternal(i, radioResponseInfo.serial, radioResponseInfo.error, radioResponseInfo.type);
    }

    private RILRequest processResponseInternal(int i, int i2, int i3, int i4) {
        RILRequest rILRequest;
        if (i4 == 1) {
            synchronized (this.mRequestList) {
                rILRequest = this.mRequestList.get(i2);
            }
            if (rILRequest == null) {
                riljLogw("Unexpected solicited ack response! sn: " + i2);
                return rILRequest;
            }
            decrementWakeLock(rILRequest);
            RadioBugDetector radioBugDetector = this.mRadioBugDetector;
            if (radioBugDetector != null) {
                radioBugDetector.detectRadioBug(rILRequest.mRequest, i3);
            }
            riljLog(rILRequest.serialString() + " Ack from " + serviceToString(i) + " < " + RILUtils.requestToString(rILRequest.mRequest));
            return rILRequest;
        }
        RILRequest rILRequestFindAndRemoveRequestFromList = findAndRemoveRequestFromList(i2);
        if (rILRequestFindAndRemoveRequestFromList == null) {
            riljLoge("processResponse: Unexpected response! serial: " + i2 + ", error: " + i3);
            return null;
        }
        Trace.asyncTraceForTrackEnd(2097152L, "RIL", rILRequestFindAndRemoveRequestFromList.mSerial);
        addToRilHistogram(rILRequestFindAndRemoveRequestFromList);
        RadioBugDetector radioBugDetector2 = this.mRadioBugDetector;
        if (radioBugDetector2 != null) {
            radioBugDetector2.detectRadioBug(rILRequestFindAndRemoveRequestFromList.mRequest, i3);
        }
        if (i4 == 2) {
            sendAck(i);
            riljLog("Response received from " + serviceToString(i) + " for " + rILRequestFindAndRemoveRequestFromList.serialString() + " " + RILUtils.requestToString(rILRequestFindAndRemoveRequestFromList.mRequest) + " Sending ack to ril.cpp");
        }
        int i5 = rILRequestFindAndRemoveRequestFromList.mRequest;
        if (i5 == 3 || i5 == 5) {
            if (this.mIccStatusChangedRegistrants != null) {
                riljLog("ON enter sim puk fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                this.mIccStatusChangedRegistrants.notifyRegistrants();
            }
        } else if (i5 == 129) {
            setRadioState(2, false);
        }
        if (i3 != 0) {
            int i6 = rILRequestFindAndRemoveRequestFromList.mRequest;
            if ((i6 == 2 || i6 == 4 || i6 == 43 || i6 == 6 || i6 == 7) && this.mIccStatusChangedRegistrants != null) {
                riljLog("ON some errors fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                this.mIccStatusChangedRegistrants.notifyRegistrants();
                return rILRequestFindAndRemoveRequestFromList;
            }
        } else if (rILRequestFindAndRemoveRequestFromList.mRequest == 14 && this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
            riljLog("testing emergency call, notify ECM Registrants");
            this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
        }
        return rILRequestFindAndRemoveRequestFromList;
    }

    public void processResponseDone(RILRequest rILRequest, RadioResponseInfo radioResponseInfo, Object obj) {
        processResponseDoneInternal(rILRequest, radioResponseInfo.error, radioResponseInfo.type, obj);
    }

    public void processResponseDone_1_6(RILRequest rILRequest, android.hardware.radio.V1_6.RadioResponseInfo radioResponseInfo, Object obj) {
        processResponseDoneInternal(rILRequest, radioResponseInfo.error, radioResponseInfo.type, obj);
    }

    public void processResponseDone(RILRequest rILRequest, android.hardware.radio.RadioResponseInfo radioResponseInfo, Object obj) {
        processResponseDoneInternal(rILRequest, radioResponseInfo.error, radioResponseInfo.type, obj);
    }

    private void processResponseDoneInternal(RILRequest rILRequest, int i, int i2, Object obj) {
        if (i == 0) {
            if (isLogOrTrace()) {
                String str = rILRequest.serialString() + "< " + RILUtils.requestToString(rILRequest.mRequest) + " " + retToString(rILRequest.mRequest, obj);
                riljLog(str);
                Trace.instantForTrack(2097152L, "RIL", str);
            }
        } else {
            if (isLogOrTrace()) {
                String str2 = rILRequest.serialString() + "< " + RILUtils.requestToString(rILRequest.mRequest) + " error " + i;
                riljLog(str2);
                Trace.instantForTrack(2097152L, "RIL", str2);
            }
            rILRequest.onError(i, obj);
        }
        processResponseCleanUp(rILRequest, i, i2, obj);
    }

    public void processResponseFallback(RILRequest rILRequest, RadioResponseInfo radioResponseInfo, Object obj) {
        if (radioResponseInfo.error == 6) {
            riljLog(rILRequest.serialString() + "< " + RILUtils.requestToString(rILRequest.mRequest) + " request not supported, falling back");
        }
        processResponseCleanUp(rILRequest, radioResponseInfo.error, radioResponseInfo.type, obj);
    }

    private void processResponseCleanUp(RILRequest rILRequest, int i, int i2, Object obj) {
        if (rILRequest != null) {
            this.mMetrics.writeOnRilSolicitedResponse(this.mPhoneId.intValue(), rILRequest.mSerial, i, rILRequest.mRequest, obj);
            if (i2 == 0) {
                decrementWakeLock(rILRequest);
            }
            rILRequest.release();
        }
    }

    private void sendAck(int i) {
        RILRequest rILRequestObtain = RILRequest.obtain(800, null, this.mRILDefaultWorkSource);
        acquireWakeLock(rILRequestObtain, 1);
        if (i == 0) {
            IRadio radioProxy = getRadioProxy();
            if (radioProxy != null) {
                try {
                    radioProxy.responseAcknowledgement();
                } catch (RemoteException | RuntimeException e) {
                    handleRadioProxyExceptionForRR(0, "sendAck", e);
                    riljLoge("sendAck: " + e);
                }
            } else {
                riljLoge("Error trying to send ack, radioProxy = null");
            }
        } else {
            RadioServiceProxy radioServiceProxy = getRadioServiceProxy(i);
            if (!radioServiceProxy.isEmpty()) {
                try {
                    radioServiceProxy.responseAcknowledgement();
                } catch (RemoteException | RuntimeException e2) {
                    handleRadioProxyExceptionForRR(i, "sendAck", e2);
                    riljLoge("sendAck: " + e2);
                }
            } else {
                riljLoge("Error trying to send ack, serviceProxy is empty");
            }
        }
        rILRequestObtain.release();
    }

    private WorkSource getDefaultWorkSourceIfInvalid(WorkSource workSource) {
        return workSource == null ? this.mRILDefaultWorkSource : workSource;
    }

    private void acquireWakeLock(RILRequest rILRequest, int i) {
        synchronized (rILRequest) {
            try {
                if (rILRequest.mWakeLockType != -1) {
                    riljLog("Failed to acquire wakelock for " + rILRequest.serialString());
                    return;
                }
                if (i == 0) {
                    synchronized (this.mWakeLock) {
                        try {
                            if (this.mWakeLockCount == 0) {
                                this.mWakeLock.acquire();
                            }
                            this.mWakeLockCount++;
                            this.mWlSequenceNum++;
                            if (!this.mClientWakelockTracker.isClientActive(rILRequest.getWorkSourceClientId())) {
                                this.mActiveWakelockWorkSource.add(rILRequest.mWorkSource);
                                this.mWakeLock.setWorkSource(this.mActiveWakelockWorkSource);
                            }
                            this.mClientWakelockTracker.startTracking(rILRequest.mClientId, rILRequest.mRequest, rILRequest.mSerial, this.mWakeLockCount);
                            Message messageObtainMessage = this.mRilHandler.obtainMessage(2);
                            messageObtainMessage.arg1 = this.mWlSequenceNum;
                            this.mRilHandler.sendMessageDelayed(messageObtainMessage, this.mWakeLockTimeout);
                        } finally {
                        }
                    }
                } else if (i == 1) {
                    synchronized (this.mAckWakeLock) {
                        this.mAckWakeLock.acquire();
                        this.mAckWlSequenceNum++;
                        Message messageObtainMessage2 = this.mRilHandler.obtainMessage(4);
                        messageObtainMessage2.arg1 = this.mAckWlSequenceNum;
                        this.mRilHandler.sendMessageDelayed(messageObtainMessage2, this.mAckWakeLockTimeout);
                    }
                } else {
                    riljLogw("Acquiring Invalid Wakelock type " + i);
                    return;
                }
                rILRequest.mWakeLockType = i;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public PowerManager.WakeLock getWakeLock(int i) {
        return i == 0 ? this.mWakeLock : this.mAckWakeLock;
    }

    public RilHandler getRilHandler() {
        return this.mRilHandler;
    }

    public SparseArray<RILRequest> getRilRequestList() {
        return this.mRequestList;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void decrementWakeLock(RILRequest rILRequest) {
        synchronized (rILRequest) {
            try {
                int i = rILRequest.mWakeLockType;
                if (i != -1) {
                    if (i == 0) {
                        synchronized (this.mWakeLock) {
                            try {
                                ClientWakelockTracker clientWakelockTracker = this.mClientWakelockTracker;
                                String str = rILRequest.mClientId;
                                int i2 = rILRequest.mRequest;
                                int i3 = rILRequest.mSerial;
                                int i4 = this.mWakeLockCount;
                                clientWakelockTracker.stopTracking(str, i2, i3, i4 > 1 ? i4 - 1 : 0);
                                if (!this.mClientWakelockTracker.isClientActive(rILRequest.getWorkSourceClientId())) {
                                    this.mActiveWakelockWorkSource.remove(rILRequest.mWorkSource);
                                    this.mWakeLock.setWorkSource(this.mActiveWakelockWorkSource);
                                }
                                int i5 = this.mWakeLockCount;
                                if (i5 > 1) {
                                    this.mWakeLockCount = i5 - 1;
                                } else {
                                    this.mWakeLockCount = 0;
                                    this.mWakeLock.release();
                                }
                            } finally {
                            }
                        }
                    } else if (i != 1) {
                        riljLogw("Decrementing Invalid Wakelock type " + rILRequest.mWakeLockType);
                    }
                }
                rILRequest.mWakeLockType = -1;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean clearWakeLock(int i) {
        if (i == 0) {
            synchronized (this.mWakeLock) {
                try {
                    if (this.mWakeLockCount == 0 && !this.mWakeLock.isHeld()) {
                        return false;
                    }
                    riljLog("NOTE: mWakeLockCount is " + this.mWakeLockCount + " at time of clearing");
                    this.mWakeLockCount = 0;
                    this.mWakeLock.release();
                    this.mClientWakelockTracker.stopTrackingAll();
                    this.mActiveWakelockWorkSource = new WorkSource();
                    return true;
                } finally {
                }
            }
        }
        synchronized (this.mAckWakeLock) {
            try {
                if (!this.mAckWakeLock.isHeld()) {
                    return false;
                }
                this.mAckWakeLock.release();
                return true;
            } finally {
            }
        }
    }

    private void clearRequestList(int i, boolean z) {
        synchronized (this.mRequestList) {
            try {
                int size = this.mRequestList.size();
                if (z) {
                    riljLog("clearRequestList  mWakeLockCount=" + this.mWakeLockCount + " mRequestList=" + size);
                }
                for (int i2 = 0; i2 < size; i2++) {
                    RILRequest rILRequestValueAt = this.mRequestList.valueAt(i2);
                    if (z) {
                        riljLog(i2 + ": [" + rILRequestValueAt.mSerial + "] " + RILUtils.requestToString(rILRequestValueAt.mRequest));
                    }
                    rILRequestValueAt.onError(i, null);
                    decrementWakeLock(rILRequestValueAt);
                    rILRequestValueAt.release();
                }
                this.mRequestList.clear();
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public RILRequest findAndRemoveRequestFromList(int i) {
        RILRequest rILRequest;
        synchronized (this.mRequestList) {
            try {
                rILRequest = this.mRequestList.get(i);
                if (rILRequest != null) {
                    this.mRequestList.remove(i);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return rILRequest;
    }

    private void addToRilHistogram(RILRequest rILRequest) {
        int iElapsedRealtime = (int) (SystemClock.elapsedRealtime() - rILRequest.mStartTimeMs);
        synchronized (sRilTimeHistograms) {
            try {
                TelephonyHistogram telephonyHistogram = sRilTimeHistograms.get(rILRequest.mRequest);
                if (telephonyHistogram == null) {
                    telephonyHistogram = new TelephonyHistogram(1, rILRequest.mRequest, 5);
                    sRilTimeHistograms.put(rILRequest.mRequest, telephonyHistogram);
                }
                telephonyHistogram.addTimeTaken(iElapsedRealtime);
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    RadioCapability makeStaticRadioCapability() throws Resources.NotFoundException {
        String string = this.mContext.getResources().getString(R.string.config_wwan_network_service_class);
        int iRafTypeFromString = !TextUtils.isEmpty(string) ? RadioAccessFamily.rafTypeFromString(string) : 0;
        RadioCapability radioCapability = new RadioCapability(this.mPhoneId.intValue(), 0, 0, iRafTypeFromString, PhoneConfigurationManager.SSSS, 1);
        riljLog("Faking RIL_REQUEST_GET_RADIO_CAPABILITY response using " + iRafTypeFromString);
        return radioCapability;
    }

    static String retToString(int i, Object obj) {
        if (obj == null || i == 11 || i == 100 || i == 115 || i == 117 || i == 38 || i == 39) {
            return PhoneConfigurationManager.SSSS;
        }
        int i2 = 0;
        int i3 = 1;
        if (obj instanceof int[]) {
            int[] iArr = (int[]) obj;
            int length = iArr.length;
            StringBuilder sb = new StringBuilder("{");
            if (length > 0) {
                sb.append(iArr[0]);
                while (i3 < length) {
                    sb.append(", ");
                    sb.append(iArr[i3]);
                    i3++;
                }
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof String[]) {
            String[] strArr = (String[]) obj;
            int length2 = strArr.length;
            StringBuilder sb2 = new StringBuilder("{");
            if (length2 > 0) {
                if (i == 98) {
                    sb2.append(Rlog.pii("RILJ", strArr[0]));
                } else {
                    sb2.append(strArr[0]);
                }
                while (i3 < length2) {
                    sb2.append(", ");
                    sb2.append(strArr[i3]);
                    i3++;
                }
            }
            sb2.append("}");
            return sb2.toString();
        }
        if (i == 152) {
            StringBuilder sb3 = new StringBuilder("{");
            ImeiInfo imeiInfo = (ImeiInfo) obj;
            sb3.append(Rlog.pii("RILJ", imeiInfo.imei));
            sb3.append(", ");
            sb3.append(imeiInfo.type);
            sb3.append(", ");
            sb3.append(imeiInfo.svn);
            sb3.append("}");
            return sb3.toString();
        }
        if (i == 9) {
            ArrayList arrayList = (ArrayList) obj;
            StringBuilder sb4 = new StringBuilder("{");
            int size = arrayList.size();
            while (i2 < size) {
                Object obj2 = arrayList.get(i2);
                i2++;
                sb4.append("[");
                sb4.append((DriverCall) obj2);
                sb4.append("] ");
            }
            sb4.append("}");
            return sb4.toString();
        }
        if (i == 75) {
            ArrayList arrayList2 = (ArrayList) obj;
            StringBuilder sb5 = new StringBuilder("{");
            int size2 = arrayList2.size();
            while (i2 < size2) {
                Object obj3 = arrayList2.get(i2);
                i2++;
                sb5.append("[");
                sb5.append((NeighboringCellInfo) obj3);
                sb5.append("] ");
            }
            sb5.append("}");
            return sb5.toString();
        }
        if (i == 33) {
            CallForwardInfo[] callForwardInfoArr = (CallForwardInfo[]) obj;
            int length3 = callForwardInfoArr.length;
            StringBuilder sb6 = new StringBuilder("{");
            while (i2 < length3) {
                sb6.append("[");
                sb6.append(callForwardInfoArr[i2]);
                sb6.append("] ");
                i2++;
            }
            sb6.append("}");
            return sb6.toString();
        }
        if (i == 124) {
            ArrayList arrayList3 = (ArrayList) obj;
            StringBuilder sb7 = new StringBuilder(" ");
            int size3 = arrayList3.size();
            while (i2 < size3) {
                Object obj4 = arrayList3.get(i2);
                i2++;
                sb7.append("[");
                sb7.append((HardwareConfig) obj4);
                sb7.append("] ");
            }
            return sb7.toString();
        }
        if (i == 235 || i == 1108) {
            StringBuilder sb8 = new StringBuilder("{");
            Object[] objArr = (Object[]) obj;
            sb8.append(((Integer) objArr[0]).intValue());
            sb8.append(", ");
            Object obj5 = objArr[1];
            if (obj5 != null) {
                ConnectionFailureInfo connectionFailureInfo = (ConnectionFailureInfo) obj5;
                sb8.append(connectionFailureInfo.getReason());
                sb8.append(", ");
                sb8.append(connectionFailureInfo.getCauseCode());
                sb8.append(", ");
                sb8.append(connectionFailureInfo.getWaitTimeMillis());
            } else {
                sb8.append("null");
            }
            sb8.append("}");
            return sb8.toString();
        }
        try {
            Class[] clsArr = new Class[0];
            if (obj.getClass().getMethod("toString", null).getDeclaringClass() != Object.class) {
                return obj.toString();
            }
        } catch (NoSuchMethodException e) {
            Rlog.e("RILJ", e.getMessage());
        }
        return RILUtils.convertToString(obj) + " [convertToString]";
    }

    void writeMetricsCallRing(char[] cArr) {
        this.mMetrics.writeRilCallRing(this.mPhoneId.intValue(), cArr);
    }

    void writeMetricsSrvcc(int i) {
        this.mMetrics.writeRilSrvcc(this.mPhoneId.intValue(), i);
        PhoneFactory.getPhone(this.mPhoneId.intValue()).getVoiceCallSessionStats().onRilSrvccStateChanged(i);
    }

    void writeMetricsModemRestartEvent(String str) {
        this.mMetrics.writeModemRestartEvent(this.mPhoneId.intValue(), str);
        if (this.mPhoneId.intValue() == 0) {
            ModemRestartStats.onModemRestart(str);
        }
    }

    void notifyRegistrantsRilConnectionChanged(int i) {
        this.mRilVersion = i;
        RegistrantList registrantList = this.mRilConnectedRegistrants;
        if (registrantList != null) {
            registrantList.notifyRegistrants(new AsyncResult((Object) null, new Integer(i), (Throwable) null));
        }
    }

    void notifyRegistrantsCdmaInfoRec(CdmaInformationRecords cdmaInformationRecords) {
        if (this.mFeatureFlags.cleanupCdma()) {
            return;
        }
        Object obj = cdmaInformationRecords.record;
        if (obj instanceof CdmaInformationRecords.CdmaDisplayInfoRec) {
            if (this.mDisplayInfoRegistrants != null) {
                if (isLogOrTrace()) {
                    unsljLogRet(1027, cdmaInformationRecords.record);
                }
                this.mDisplayInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, cdmaInformationRecords.record, (Throwable) null));
                return;
            }
            return;
        }
        if (obj instanceof CdmaInformationRecords.CdmaSignalInfoRec) {
            if (this.mSignalInfoRegistrants != null) {
                if (isLogOrTrace()) {
                    unsljLogRet(1027, cdmaInformationRecords.record);
                }
                this.mSignalInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, cdmaInformationRecords.record, (Throwable) null));
                return;
            }
            return;
        }
        if (obj instanceof CdmaInformationRecords.CdmaNumberInfoRec) {
            if (this.mNumberInfoRegistrants != null) {
                if (isLogOrTrace()) {
                    unsljLogRet(1027, cdmaInformationRecords.record);
                }
                this.mNumberInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, cdmaInformationRecords.record, (Throwable) null));
                return;
            }
            return;
        }
        if (obj instanceof CdmaInformationRecords.CdmaRedirectingNumberInfoRec) {
            if (this.mRedirNumInfoRegistrants != null) {
                if (isLogOrTrace()) {
                    unsljLogRet(1027, cdmaInformationRecords.record);
                }
                this.mRedirNumInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, cdmaInformationRecords.record, (Throwable) null));
                return;
            }
            return;
        }
        if (obj instanceof CdmaInformationRecords.CdmaLineControlInfoRec) {
            if (this.mLineControlInfoRegistrants != null) {
                if (isLogOrTrace()) {
                    unsljLogRet(1027, cdmaInformationRecords.record);
                }
                this.mLineControlInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, cdmaInformationRecords.record, (Throwable) null));
                return;
            }
            return;
        }
        if (obj instanceof CdmaInformationRecords.CdmaT53ClirInfoRec) {
            if (this.mT53ClirInfoRegistrants != null) {
                if (isLogOrTrace()) {
                    unsljLogRet(1027, cdmaInformationRecords.record);
                }
                this.mT53ClirInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, cdmaInformationRecords.record, (Throwable) null));
                return;
            }
            return;
        }
        if (!(obj instanceof CdmaInformationRecords.CdmaT53AudioControlInfoRec) || this.mT53AudCntrlInfoRegistrants == null) {
            return;
        }
        if (isLogOrTrace()) {
            unsljLogRet(1027, cdmaInformationRecords.record);
        }
        this.mT53AudCntrlInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, cdmaInformationRecords.record, (Throwable) null));
    }

    void notifyRegistrantsImeiMappingChanged(ImeiInfo imeiInfo) {
        RegistrantList registrantList = this.mImeiInfoRegistrants;
        if (registrantList != null) {
            registrantList.notifyRegistrants(new AsyncResult((Object) null, imeiInfo, (Throwable) null));
        }
    }

    void riljLog(String str) {
        Rlog.d("RILJ", str + " [PHONE" + this.mPhoneId + "]");
    }

    void riljLoge(String str) {
        Rlog.e("RILJ", str + " [PHONE" + this.mPhoneId + "]");
    }

    void riljLogw(String str) {
        Rlog.w("RILJ", str + " [PHONE" + this.mPhoneId + "]");
    }

    boolean isLogvOrTrace() {
        return Trace.isTagEnabled(2097152L);
    }

    void unsljLog(int i) {
        String strResponseToString = RILUtils.responseToString(i);
        riljLog("[UNSL]< " + strResponseToString);
        Trace.instantForTrack(2097152L, "RIL", strResponseToString);
        ((IOplusNetworkManager) OplusTelephonyFactory.getInstance().getFeature(IOplusNetworkManager.DEFAULT, new Object[0])).oemCountUnsolMsg(i);
    }

    void unsljLogMore(int i, String str) {
        String str2 = RILUtils.responseToString(i) + " " + str;
        riljLog("[UNSL]< " + str2);
        Trace.instantForTrack(2097152L, "RIL", str2);
        ((IOplusNetworkManager) OplusTelephonyFactory.getInstance().getFeature(IOplusNetworkManager.DEFAULT, new Object[0])).oemCountUnsolMsg(i);
    }

    void unsljLogRet(int i, Object obj) {
        String str = RILUtils.responseToString(i) + " " + retToString(i, obj);
        riljLog("[UNSL]< " + str);
        Trace.instantForTrack(2097152L, "RIL", str);
        ((IOplusNetworkManager) OplusTelephonyFactory.getInstance().getFeature(IOplusNetworkManager.DEFAULT, new Object[0])).oemCountUnsolMsg(i);
    }

    void unsljLogvRet(int i, Object obj) {
        Trace.instantForTrack(2097152L, "RIL", RILUtils.responseToString(i) + " " + retToString(i, obj));
        ((IOplusNetworkManager) OplusTelephonyFactory.getInstance().getFeature(IOplusNetworkManager.DEFAULT, new Object[0])).oemCountUnsolMsg(i);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setPhoneType(int i) {
        riljLog("setPhoneType=" + i + " old value=" + this.mPhoneType);
        this.mPhoneType = i;
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void testingEmergencyCall() {
        riljLog("testingEmergencyCall");
        this.mTestingEmergencyCall.set(true);
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("RIL: " + this);
        printWriter.println(" " + this.mServiceProxies.get(1));
        printWriter.println(" " + this.mServiceProxies.get(2));
        printWriter.println(" " + this.mServiceProxies.get(3));
        printWriter.println(" " + this.mServiceProxies.get(4));
        printWriter.println(" " + this.mServiceProxies.get(5));
        printWriter.println(" " + this.mServiceProxies.get(6));
        printWriter.println(" " + this.mServiceProxies.get(7));
        printWriter.println(" mWakeLock=" + this.mWakeLock);
        printWriter.println(" mWakeLockTimeout=" + this.mWakeLockTimeout);
        synchronized (this.mRequestList) {
            try {
                synchronized (this.mWakeLock) {
                    printWriter.println(" mWakeLockCount=" + this.mWakeLockCount);
                }
                int size = this.mRequestList.size();
                printWriter.println(" mRequestList count=" + size);
                for (int i = 0; i < size; i++) {
                    RILRequest rILRequestValueAt = this.mRequestList.valueAt(i);
                    printWriter.println("  [" + rILRequestValueAt.mSerial + "] " + RILUtils.requestToString(rILRequestValueAt.mRequest));
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        printWriter.println(" mLastNITZTimeInfo=" + Arrays.toString(this.mLastNITZTimeInfo));
        printWriter.println(" mLastRadioPowerResult=" + this.mLastRadioPowerResult);
        printWriter.println(" mTestingEmergencyCall=" + this.mTestingEmergencyCall.get());
        this.mClientWakelockTracker.dumpClientRequestTracker(printWriter);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public List<ClientRequestStats> getClientRequestStats() {
        return this.mClientWakelockTracker.getClientRequestStats();
    }

    void notifyBarringInfoChanged(BarringInfo barringInfo) {
        this.mLastBarringInfo = barringInfo;
        this.mBarringInfoChangedRegistrants.notifyRegistrants(new AsyncResult((Object) null, barringInfo, (Throwable) null));
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public HalVersion getHalVersion(int i) {
        HalVersion halVersion = this.mHalVersion.get(Integer.valueOf(i));
        if (halVersion != null) {
            return halVersion;
        }
        if (isRadioServiceSupported(i)) {
            return RADIO_HAL_VERSION_UNKNOWN;
        }
        return RADIO_HAL_VERSION_UNSUPPORTED;
    }

    public static HalVersion getServiceHalVersion(int i) {
        if (i == 1) {
            return RADIO_HAL_VERSION_2_0;
        }
        if (i == 2) {
            return RADIO_HAL_VERSION_2_1;
        }
        if (i == 3) {
            return RADIO_HAL_VERSION_2_2;
        }
        if (i == 4) {
            return RADIO_HAL_VERSION_2_3;
        }
        if (i == 5) {
            return RADIO_HAL_VERSION_2_4;
        }
        return RADIO_HAL_VERSION_UNKNOWN;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String serviceToString(int i) {
        switch (i) {
            case 0:
                return "RADIO";
            case 1:
                return "DATA";
            case 2:
                return "MESSAGING";
            case 3:
                return "MODEM";
            case 4:
                return "NETWORK";
            case 5:
                return "SIM";
            case 6:
                return "VOICE";
            case 7:
                return "IMS";
            default:
                return "UNKNOWN:" + i;
        }
    }
}

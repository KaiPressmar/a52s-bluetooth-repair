package de.kaipressmar.a52srepair.telecom;

import android.os.Build;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.telecom.InCallService;
import androidx.annotation.RequiresApi;
import de.kaipressmar.a52srepair.core.report.CallReport;
import de.kaipressmar.a52srepair.core.model.CallAudioSnapshot;
import de.kaipressmar.a52srepair.core.repair.ManualRepairStatus;
import de.kaipressmar.a52srepair.data.AppSettings;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticLog;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-UI InCallService. Telecom binds it only while calls exist (after call access was granted
 * through the companion-device link or adb), so the app needs no background service, no polling
 * and no notification. Route changes go through Telecom exactly like the dialer's audio button.
 */
public final class CallAudioService extends InCallService implements CallSession.Host {
    private static WeakReference<CallAudioService> activeService = new WeakReference<>(null);
    private CallSession session;
    private EndpointAudioState endpoints;

    @RequiresApi(34)
    private EndpointAudioState endpoints() {
        if (endpoints == null) endpoints = new EndpointAudioState();
        return endpoints;
    }

    /** Read on the main thread, like the service callbacks and diagnostic export. */
    public static CallReport activeReport() {
        CallAudioService service = activeService.get();
        return service == null || service.session == null ? null : service.session.report();
    }

    public static CallAudioSnapshot checkNow() {
        CallAudioService service = activeService.get();
        return service == null || service.session == null ? null : service.session.inspect();
    }

    public static ManualRepairStatus repairNow() {
        CallAudioService service = activeService.get();
        return service == null || service.session == null ? ManualRepairStatus.NO_ACTIVE_CALL
                : service.session.repairManually();
    }

    private final Call.Callback callCallback =
            new Call.Callback() {
                @Override
                public void onStateChanged(Call call, int state) {
                    if (session != null) session.onEvent();
                }
                @Override public void onDetailsChanged(Call call, Call.Details details) {
                    if (session != null) session.onEvent();
                }
            };

    @Override
    public void onCallAdded(Call call) {
        activeService = new WeakReference<>(this);
        call.registerCallback(callCallback);
        if (session == null) {
            new AppSettings(this).markServiceBound(System.currentTimeMillis());
            session = new CallSession(this, this);
            session.start();
        } else {
            session.onEvent();
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        call.unregisterCallback(callCallback);
        if (session == null) return;
        if (getCalls().isEmpty()) {
            session.finish();
            session = null;
            endpoints = null;
            if (activeService.get() == this) activeService.clear();
        } else {
            session.onEvent();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onCallAudioStateChanged(CallAudioState audioState) {
        if (Build.VERSION.SDK_INT >= 34) audioState = endpoints().merge(audioState);
        if (session != null) session.onAudioStateChanged(audioState);
    }

    @Override
    @RequiresApi(34)
    public void onCallEndpointChanged(CallEndpoint endpoint) {
        endpoints().selected(endpoint);
        if (session != null) session.onAudioStateChanged(audioState());
    }

    @Override
    @RequiresApi(34)
    public void onAvailableCallEndpointsChanged(List<CallEndpoint> available) {
        endpoints().available(available);
        if (session != null) session.onAudioStateChanged(audioState());
    }

    @Override @RequiresApi(34)
    public void onMuteStateChanged(boolean isMuted) {
        endpoints().muted(isMuted);
        if (session != null) session.onAudioStateChanged(audioState());
    }

    @Override
    public void onDestroy() {
        if (activeService.get() == this) activeService.clear();
        if (session != null) {
            session.finish();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public CallAudioState audioState() {
        CallAudioState legacy = getCallAudioState();
        return Build.VERSION.SDK_INT >= 34 ? endpoints().merge(legacy) : legacy;
    }

    @Override
    public List<Integer> callStates() {
        List<Integer> states = new ArrayList<>();
        for (Call call : getCalls()) {
            Call.Details details = call.getDetails();
            states.add(details == null ? Call.STATE_NEW : details.getState());
        }
        return states;
    }

    @Override public boolean bluetoothTargetAmbiguous() {
        return Build.VERSION.SDK_INT >= 34 && endpoints().bluetoothTargetAmbiguous();
    }

    @Override
    public void requestRoute(int telecomRoute) {
        DiagnosticLog.log(this, "CALL request Telecom route=" + CallAudioState.audioRouteToString(telecomRoute));
        if (Build.VERSION.SDK_INT >= 34 && endpoints().hasAvailability()) {
            CallSession targetSession = session;
            endpoints().request(telecomRoute,
                    (endpoint, callback) -> requestCallEndpointChange(endpoint, getMainExecutor(), callback),
                    () -> {
                        if (session == targetSession && session != null) session.onEvent();
                    }, code -> {
                        if (session != targetSession || session == null) return;
                        DiagnosticLog.log(this, "CALL endpoint request failed code=" + code);
                        if (code == android.telecom.CallEndpointException.ERROR_ANOTHER_REQUEST) session.onEvent();
                        else session.onRouteRequestFailed();
                    });
            return;
        }
        setAudioRoute(telecomRoute);
    }
}

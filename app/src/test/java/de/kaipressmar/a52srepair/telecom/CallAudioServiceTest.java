package de.kaipressmar.a52srepair.telecom;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import androidx.preference.PreferenceManager;
import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.core.repair.ManualRepairStatus;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 34, 36})
public class CallAudioServiceTest {
    private static void addCall(CallAudioService service, Call call) {
        // Avoid the overloaded hidden ParcelableCall type absent from the compile SDK.
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(shadowOf(service), "addCall",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(Call.class, call));
    }

    private static Call newCall() {
        Call call = Shadow.newInstanceOf(Call.class);
        org.robolectric.util.ReflectionHelpers.setField(call, "mCallbackRecords", new java.util.concurrent.CopyOnWriteArrayList<>());
        return call;
    }
    @Test public void callLifecycleTracksMultipleCallsAndClearsActiveState() {
        ServiceController<CallAudioService> controller = Robolectric.buildService(CallAudioService.class).create();
        CallAudioService service = controller.get();
        PreferenceManager.getDefaultSharedPreferences(service).edit().putBoolean("protection_enabled", false).commit();
        Call first = newCall();
        Call second = newCall();
        addCall(service, first);
        service.onCallAdded(first);
        assertNotNull(CallAudioService.activeReport());
        assertEquals(1, CallAudioService.checkNow().callCount);
        assertEquals(ManualRepairStatus.NO_ACTIVE_CALL, CallAudioService.repairNow());
        addCall(service, second);
        service.onCallAdded(second);
        assertEquals(2, CallAudioService.checkNow().callCount);
        shadowOf(service).removeCall(first);
        service.onCallRemoved(first);
        assertEquals(1, CallAudioService.checkNow().callCount);
        shadowOf(service).removeCall(second);
        service.onCallRemoved(second);
        assertNull(CallAudioService.activeReport());
        assertNull(CallAudioService.checkNow());
        assertEquals(ManualRepairStatus.NO_ACTIVE_CALL, CallAudioService.repairNow());
        controller.destroy();
    }

    @Test public void destructionFinishesSessionAndModernObservationsOverrideLegacy() {
        ServiceController<CallAudioService> controller = Robolectric.buildService(CallAudioService.class).create();
        CallAudioService service = controller.get();
        Call call = newCall();
        addCall(service, call);
        service.onCallAdded(call);
        if (Build.VERSION.SDK_INT >= 34) {
            CallEndpoint speaker = new CallEndpoint("Speaker", CallEndpoint.TYPE_SPEAKER, android.os.ParcelUuid.fromString("00000000-0000-0000-0000-000000000001"));
            service.onAvailableCallEndpointsChanged(List.of(speaker));
            service.onCallEndpointChanged(speaker);
            service.onMuteStateChanged(true);
            service.onCallAudioStateChanged(new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_BLUETOOTH));
            assertEquals(AudioRoute.SPEAKER, CallAudioService.checkNow().route);
            assertTrue(CallAudioService.checkNow().microphoneMuted);
            assertFalse(CallAudioService.checkNow().bluetoothRouteAvailable);
        } else {
            service.requestRoute(CallAudioState.ROUTE_SPEAKER);
            assertEquals(AudioRoute.SPEAKER, CallAudioService.checkNow().route);
        }
        controller.destroy();
        assertNull(CallAudioService.activeReport());
    }
}

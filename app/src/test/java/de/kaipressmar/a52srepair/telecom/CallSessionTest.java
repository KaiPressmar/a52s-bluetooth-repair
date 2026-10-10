package de.kaipressmar.a52srepair.telecom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.core.report.CallOutcome;
import de.kaipressmar.a52srepair.core.report.CallReport;
import de.kaipressmar.a52srepair.core.repair.ManualRepairStatus;
import de.kaipressmar.a52srepair.data.CallReportRepository;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticLog;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** End-to-end wiring: simulated Telecom + real engine + persistence, no device needed. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 36})
@SuppressWarnings("deprecation")
public class CallSessionTest {
    private static final int ALL_ROUTES =
            CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_SPEAKER;

    /** Telecom stand-in that follows route requests like the real CallAudioRouteStateMachine. */
    private static class FakeTelecom implements CallSession.Host {
        CallAudioState audio = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH, ALL_ROUTES);
        int callState = Call.STATE_ACTIVE;
        final List<Integer> requests = new ArrayList<>();
        CallSession session;

        @Override public CallAudioState audioState() { return audio; }

        @Override public List<Integer> callStates() { return Collections.singletonList(callState); }

        @Override public void requestRoute(int route) {
            requests.add(route);
            int effective = route == CallAudioState.ROUTE_WIRED_OR_EARPIECE ? CallAudioState.ROUTE_EARPIECE : route;
            audio = new CallAudioState(false, effective, ALL_ROUTES);
            session.onEvent();
        }
    }

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        new CallReportRepository(context).clear();
        DiagnosticLog.clear(context);
        // Flavor-neutral: S22 defaults to AFTER_PROBLEMS.
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("preventive_mode", "ALWAYS").commit();
        // Robolectric starts the voice stream at 0, which the engine rightly treats as muted.
        context.getSystemService(AudioManager.class)
                .setStreamVolume(AudioManager.STREAM_VOICE_CALL, 5, 0);
    }

    private static void advance(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    @Test public void ownAudioPolicyRequestCannotVerifyItsPredictedRouteAndIsClearedWhenPaused() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("preventive_mode", "OFF").putBoolean("protection_enabled", true).commit();
        AudioManager audio = context.getSystemService(AudioManager.class);
        android.media.AudioDeviceInfo device = org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                .setType(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO).build();
        Object port = org.robolectric.util.ReflectionHelpers.getField(device, "mPort");
        org.robolectric.util.ReflectionHelpers.setField(port, "mRole", 2);
        org.robolectric.util.ReflectionHelpers.setField(port, "mAddress", "00:11:22:33:44:01");
        org.robolectric.util.ReflectionHelpers.setField(org.robolectric.util.ReflectionHelpers.getField(port, "mHandle"), "mId", 1);
        shadowOf(audio).setAvailableCommunicationDevices(List.of(device));
        shadowOf(audio).setAudioDevicesForAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).build(),
                com.google.common.collect.ImmutableList.of(device));
        FakeTelecom telecom = new FakeTelecom() {
            @Override public android.bluetooth.BluetoothDevice bluetoothDevice() { return LegacyBluetoothTargetTest.device(1); }
        };
        telecom.session = new CallSession(context, telecom);
        de.kaipressmar.a52srepair.audio.CommunicationDeviceRecovery recovery =
                org.robolectric.util.ReflectionHelpers.getField(telecom.session, "communication");
        assertTrue(recovery.request(telecom.bluetoothDevice(), 0L));
        assertEquals(null, telecom.session.inspect().voiceOnBluetooth);
        telecom.session.start();
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("protection_enabled", false).commit();
        advance(200L);
        assertFalse(recovery.hasRequest());
        assertEquals(null, audio.getCommunicationDevice());
        assertEquals(Boolean.TRUE, telecom.session.inspect().voiceOnBluetooth);
        assertTrue(recovery.request(telecom.bluetoothDevice(), 200L));
        telecom.session.finish();
        assertFalse(recovery.hasRequest());
        assertEquals(null, audio.getCommunicationDevice());
    }

    @Test public void changingProtectionDuringCallStopsPendingAutomaticReturnAndAllowsManualRepair() {
        FakeTelecom telecom = new FakeTelecom();
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        advance(1_200L);
        assertEquals(List.of(CallAudioState.ROUTE_WIRED_OR_EARPIECE), telecom.requests);
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("protection_enabled", false).commit();
        advance(5_000L);
        assertEquals(List.of(CallAudioState.ROUTE_WIRED_OR_EARPIECE), telecom.requests);
        assertEquals(ManualRepairStatus.STARTED, telecom.session.repairManually());
        advance(5_000L);
        assertEquals(CallAudioState.ROUTE_BLUETOOTH, (int) telecom.requests.get(telecom.requests.size() - 1));
        telecom.session.finish();
        int count = telecom.requests.size();
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("protection_enabled", true).commit();
        advance(20_000L);
        assertEquals(count, telecom.requests.size());
    }

    @Test public void ambiguousAutomaticTargetCannotMoveCallToPhoneBeforeFailingReturn() {
        FakeTelecom telecom = new FakeTelecom() {
            @Override public boolean bluetoothTargetAmbiguous() { return true; }
        };
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        advance(20_000L);
        assertTrue(telecom.requests.isEmpty());
        assertTrue(DiagnosticLog.readAll(context).contains("ambiguous Bluetooth endpoint"));
        telecom.session.finish();
    }

    @Test public void a52sCarCallGetsOnePreventiveRebuildAndIsReported() {
        FakeTelecom telecom = new FakeTelecom();
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        advance(20_000L);

        assertEquals(
                List.of(CallAudioState.ROUTE_WIRED_OR_EARPIECE, CallAudioState.ROUTE_BLUETOOTH),
                telecom.requests);

        telecom.session.finish();
        CallReport report = new CallReportRepository(context).history().latestBluetoothCall();
        assertEquals(CallOutcome.HEALTHY, report.outcome);
        assertTrue(report.preventiveRebuild);
        assertTrue(DiagnosticLog.readAll(context).contains("CALL END"));
    }

    @Test public void callStuckOnEarpieceIsMovedToCar() {
        FakeTelecom telecom = new FakeTelecom();
        telecom.audio = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE, ALL_ROUTES);
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        advance(15_000L);

        assertEquals(CallAudioState.ROUTE_BLUETOOTH, (int) telecom.requests.get(0));
        telecom.session.finish();
        // Robolectric supplies neither SCO nor voice-route evidence: request != proven recovery.
        assertEquals(CallOutcome.UNRESOLVED,
                new CallReportRepository(context).history().latestBluetoothCall().outcome);
    }

    @Test public void shortBluetoothSelectionsAreLoggedWithoutDebounce() {
        FakeTelecom telecom = new FakeTelecom();
        telecom.audio = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE, ALL_ROUTES);
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        advance(1L);
        DiagnosticLog.clear(context);
        // Two route callbacks in the same handler turn: a debounce would lose Bluetooth.
        telecom.session.onAudioStateChanged(
                new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH, ALL_ROUTES));
        telecom.session.onAudioStateChanged(telecom.audio);
        String log = DiagnosticLog.readAll(context);
        assertTrue(log.contains("route=BLUETOOTH"));
        assertTrue(log.contains("route=EARPIECE"));
        assertTrue(telecom.requests.isEmpty());
        telecom.session.finish();
    }

    @Test public void activeReportDoesNotFinishOrPersistTheCall() {
        FakeTelecom telecom = new FakeTelecom();
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        advance(100L);
        CallReport active = telecom.session.report();
        assertTrue(active.startedAt > 0L);
        assertTrue(active.durationMs >= 100L);
        assertTrue(new CallReportRepository(context).history().all().isEmpty());
        advance(20_000L);
        assertFalse(telecom.requests.isEmpty());
        telecom.session.finish();
        assertEquals(1, new CallReportRepository(context).history().all().size());
    }

    @Test public void routeCommandFailureDoesNotKillObservationOrResetBudget() {
        FakeTelecom telecom = new FakeTelecom() {
            @Override public void requestRoute(int route) {
                requests.add(route);
                throw new SecurityException("unavailable call routing");
            }
        };
        telecom.audio = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE, ALL_ROUTES);
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        advance(60_000L);
        assertEquals(3, telecom.session.report().routeAttempts);
        assertTrue(DiagnosticLog.readAll(context).contains("failed: SecurityException"));
        telecom.session.finish();
        assertEquals(CallOutcome.UNRESOLVED,
                new CallReportRepository(context).history().latestBluetoothCall().outcome);
    }

    @Test public void callbacksAfterFinishNeverRequestAnotherRoute() {
        FakeTelecom telecom = new FakeTelecom();
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        telecom.session.finish();
        telecom.session.onEvent();
        telecom.session.onAudioStateChanged(telecom.audio);
        advance(60_000L);
        assertTrue(telecom.requests.isEmpty());
    }

    @Test public void finishIsIdempotent() {
        FakeTelecom telecom = new FakeTelecom();
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        telecom.session.finish();
        telecom.session.finish();
        assertEquals(1, new CallReportRepository(context).history().all().size());
    }

    @Test public void manualCheckIsReadOnlyAndExplicitRepairWorksWhilePaused() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("protection_enabled", false).commit();
        FakeTelecom telecom = new FakeTelecom();
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        advance(100L);
        assertTrue(telecom.session.inspect().bluetoothRouteAvailable);
        assertTrue(telecom.requests.isEmpty());
        assertEquals(ManualRepairStatus.STARTED, telecom.session.repairManually());
        advance(10_000L);
        assertEquals(List.of(CallAudioState.ROUTE_WIRED_OR_EARPIECE, CallAudioState.ROUTE_BLUETOOTH), telecom.requests);
        assertEquals(1, telecom.session.report().manualRepairs);
        assertEquals(0, telecom.session.report().routeAttempts);
        assertEquals(CallOutcome.UNRESOLVED, telecom.session.report().outcome); // Unknown SCO/voice != proof.
        telecom.session.finish();
        assertEquals(1, new CallReportRepository(context).history().latestBluetoothCall().manualRepairs);
        int before = telecom.requests.size();
        assertEquals(ManualRepairStatus.NO_ACTIVE_CALL, telecom.session.repairManually());
        assertEquals(null, telecom.session.inspect());
        advance(60_000L);
        assertEquals(before, telecom.requests.size());
    }

    @Test public void manualCommandExceptionIsReportedAndObservationContinues() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("protection_enabled", false).commit();
        FakeTelecom telecom = new FakeTelecom() {
            @Override public void requestRoute(int route) {
                requests.add(route);
                throw new SecurityException("routing denied");
            }
        };
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        assertEquals(ManualRepairStatus.COMMAND_FAILED, telecom.session.repairManually());
        advance(20_000L);
        assertEquals(1, telecom.requests.size());
        assertEquals(CallOutcome.UNRESOLVED, telecom.session.report().outcome);
        telecom.session.finish();
    }

    @Test public void ambiguousBluetoothTargetIsRejectedBeforeTeardown() {
        FakeTelecom telecom = new FakeTelecom() {
            @Override public boolean bluetoothTargetAmbiguous() { return true; }
        };
        telecom.session = new CallSession(context, telecom);
        assertEquals(ManualRepairStatus.AMBIGUOUS_DEVICE, telecom.session.repairManually());
        assertTrue(telecom.requests.isEmpty());
        assertEquals(0, telecom.session.report().manualRepairs);
        telecom.session.finish();
    }

    @Test public void inspectionSeparatesMicrophoneMuteFromPlaybackMute() {
        FakeTelecom telecom = new FakeTelecom();
        telecom.audio = new CallAudioState(true, CallAudioState.ROUTE_BLUETOOTH, ALL_ROUTES);
        telecom.session = new CallSession(context, telecom);
        assertTrue(telecom.session.inspect().microphoneMuted);
        assertFalse(telecom.session.inspect().voiceMuted);
        assertTrue(telecom.requests.isEmpty());
        telecom.session.finish();
    }

    @Test public void statusButtonsInspectAndRepairTheBoundCall() throws Exception {
        android.app.Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT);
        shadowOf(app.getSystemService(android.bluetooth.BluetoothManager.class).getAdapter()).setEnabled(true);
        android.app.AppOpsManager ops = app.getSystemService(android.app.AppOpsManager.class);
        shadowOf(ops).setMode("android:manage_ongoing_calls", android.os.Process.myUid(), app.getPackageName(),
                android.app.AppOpsManager.MODE_ALLOWED);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("protection_enabled", false).commit();
        FakeTelecom telecom = new FakeTelecom();
        telecom.session = new CallSession(context, telecom);
        org.robolectric.android.controller.ServiceController<CallAudioService> controller =
                org.robolectric.Robolectric.buildService(CallAudioService.class).create();
        CallAudioService service = controller.get();
        java.lang.reflect.Field session = CallAudioService.class.getDeclaredField("session");
        session.setAccessible(true);
        session.set(service, telecom.session);
        java.lang.reflect.Field active = CallAudioService.class.getDeclaredField("activeService");
        active.setAccessible(true);
        active.set(null, new java.lang.ref.WeakReference<>(service));
        telecom.session.start();
        try (androidx.test.core.app.ActivityScenario<de.kaipressmar.a52srepair.ui.MainActivity> scenario =
                androidx.test.core.app.ActivityScenario.launch(de.kaipressmar.a52srepair.ui.MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(de.kaipressmar.a52srepair.R.id.manual_check).performClick();
                assertTrue(telecom.requests.isEmpty());
                activity.findViewById(de.kaipressmar.a52srepair.R.id.manual_repair).performClick();
                assertEquals(List.of(CallAudioState.ROUTE_WIRED_OR_EARPIECE), telecom.requests);
                android.widget.TextView message = activity.findViewById(de.kaipressmar.a52srepair.R.id.manual_result);
                assertEquals(activity.getString(de.kaipressmar.a52srepair.R.string.manual_started), message.getText().toString());
            });
            advance(10_000L);
            assertEquals(2, telecom.requests.size());
        } finally {
            controller.destroy();
        }
        assertEquals(null, CallAudioService.checkNow());
        assertEquals(ManualRepairStatus.NO_ACTIVE_CALL, CallAudioService.repairNow());
    }
}

package de.kaipressmar.a52srepair.telecom;

import static org.junit.Assert.assertEquals;
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
@Config(sdk = 34)
@SuppressWarnings("deprecation")
public class CallSessionTest {
    private static final int ALL_ROUTES =
            CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_SPEAKER;

    /** Telecom stand-in that follows route requests like the real CallAudioRouteStateMachine. */
    private static final class FakeTelecom implements CallSession.Host {
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
        assertEquals(CallOutcome.REPAIRED,
                new CallReportRepository(context).history().latestBluetoothCall().outcome);
    }

    @Test public void finishIsIdempotent() {
        FakeTelecom telecom = new FakeTelecom();
        telecom.session = new CallSession(context, telecom);
        telecom.session.start();
        telecom.session.finish();
        telecom.session.finish();
        assertEquals(1, new CallReportRepository(context).history().all().size());
    }
}

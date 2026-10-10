package de.kaipressmar.a52srepair.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.kaipressmar.a52srepair.core.diagnosis.Fault;
import de.kaipressmar.a52srepair.core.report.CallHistory;
import de.kaipressmar.a52srepair.core.report.CallOutcome;
import de.kaipressmar.a52srepair.core.report.CallReport;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

public class CallReportTest {
    private static final long DAY = 24L * 60L * 60L * 1000L;

    private static CallReport report(long at, CallOutcome outcome) {
        return new CallReport(at, 60_000L, outcome, EnumSet.noneOf(Fault.class), 0, 0, true, false);
    }

    @Test public void encodeDecodeRoundTrip() {
        CallReport original = new CallReport(
                123L, 45_000L, CallOutcome.REPAIRED,
                EnumSet.of(Fault.DOWNLINK_NOT_ON_BLUETOOTH, Fault.VOICE_SILENCED), 2, 1, true, false);
        CallReport decoded = CallReport.decode(original.encode());
        assertEquals(original.toString(), decoded.toString());
        assertEquals(123L, decoded.startedAt);
    }

    @Test public void decodeIsTolerant() {
        assertNull(CallReport.decode(null));
        assertNull(CallReport.decode("garbage"));
        assertNull(CallReport.decode("2;1;2;HEALTHY;;0;0;0;0"));
        CallReport futureFault = CallReport.decode("1;1;2;HEALTHY;FUTURE_FAULT;0;0;0;0");
        assertTrue(futureFault.faults.isEmpty());
    }

    @Test public void manualRepairCountsRoundTripAndOldReportsRemainReadable() {
        CallReport old = CallReport.decode("1;1;2;HEALTHY;;0;0;0;0");
        assertEquals(0, old.manualRepairs);
        CallReport manual = new CallReport(1L, 2L, CallOutcome.UNRESOLVED,
                EnumSet.of(Fault.USER_REPORTED_AUDIO_PROBLEM), 3, 0, false, false, 2);
        assertEquals(manual.toString(), CallReport.decode(manual.encode()).toString());
    }

    @Test public void riskElevatedOnlyForRecentProblems() {
        long now = 100L * DAY;
        CallHistory calm = new CallHistory(List.of(report(now - DAY, CallOutcome.HEALTHY)));
        CallHistory recentProblem = new CallHistory(List.of(
                report(now - DAY, CallOutcome.HEALTHY), report(now - 2 * DAY, CallOutcome.LEFT_BLUETOOTH)));
        CallHistory oldProblem = new CallHistory(List.of(report(now - 30 * DAY, CallOutcome.UNRESOLVED)));
        assertFalse(calm.riskElevated(now));
        assertTrue(recentProblem.riskElevated(now));
        assertFalse(oldProblem.riskElevated(now));
    }

    @Test public void statisticsIgnoreCallsWithoutBluetooth() {
        CallHistory history = new CallHistory(List.of(
                report(3L, CallOutcome.NOT_BLUETOOTH),
                report(2L, CallOutcome.REPAIRED),
                report(1L, CallOutcome.HEALTHY)));
        assertEquals(2, history.bluetoothCallCount(0L));
        assertEquals(CallOutcome.REPAIRED, history.latestBluetoothCall().outcome);
        assertEquals(1, history.count(CallOutcome.REPAIRED, 0L));
    }

    @Test public void historyIsBounded() {
        List<CallReport> list = new ArrayList<>();
        for (int i = 0; i < CallHistory.MAX_ENTRIES + 20; i++) {
            list = CallHistory.prepend(list, report(i, CallOutcome.HEALTHY));
        }
        assertEquals(CallHistory.MAX_ENTRIES, list.size());
        assertEquals(CallHistory.MAX_ENTRIES + 19L, list.get(0).startedAt);
    }
}

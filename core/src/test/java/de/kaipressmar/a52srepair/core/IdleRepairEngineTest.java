package de.kaipressmar.a52srepair.core;

import static org.junit.Assert.*;
import de.kaipressmar.a52srepair.core.repair.IdleRepairEngine;
import de.kaipressmar.a52srepair.core.repair.IdleRepairEngine.Method;
import de.kaipressmar.a52srepair.core.repair.IdleRepairEngine.Result;
import java.util.*;
import org.junit.Test;

public class IdleRepairEngineTest {
    private static final class Port implements IdleRepairEngine.Port {
        String block;
        boolean ready = true, accepted = true, clear = true;
        Boolean sco = false;
        EnumSet<Method> supported = EnumSet.allOf(Method.class);
        List<Method> requests = new ArrayList<>();
        List<String> events = new ArrayList<>();
        int releases;
        boolean blockOnSupport;
        public String blockedReason() { return block; }
        public boolean targetReady() { return ready; }
        public Boolean scoConnected() { return sco; }
        public boolean supports(Method m) { if (blockOnSupport) block = "new call"; return supported.contains(m); }
        public boolean start(Method m) { requests.add(m); return accepted; }
        public boolean release() { releases++; return clear; }
        public void log(String e) { events.add(e); }
    }
    @Test public void acceptedRequestDoesNotProveConnectedAudio() {
        Port p = new Port(); IdleRepairEngine e = new IdleRepairEngine(p, false, 0);
        e.tick(0); assertEquals(List.of(Method.MODERN), p.requests);
        e.tick(9999); assertEquals(Result.RUNNING, e.result());
        assertFalse(p.events.stream().anyMatch(x -> x.startsWith("LINK_CONFIRMED")));
    }
    @Test public void stableOnAndStableOffAreRequiredAndNextCallRemainsUnverified() {
        Port p = new Port(); IdleRepairEngine e = new IdleRepairEngine(p, false, 0);
        e.tick(0); p.sco = true; e.tick(100); e.tick(849);
        assertEquals(0, p.releases); e.tick(850); assertEquals(1, p.releases);
        e.tick(900); assertEquals(Result.RUNNING, e.result());
        p.sco = false; e.tick(1000); e.tick(1749); assertEquals(Result.RUNNING, e.result());
        e.tick(1750); assertEquals(Result.CHANNEL_TESTED, e.result());
        assertTrue(p.events.stream().anyMatch(x -> x.contains("next call unverified")));
        int released = p.releases; e.tick(99999); e.cancel("later"); assertEquals(released, p.releases);
    }
    @Test public void shortScoPulseDoesNotCountAsRecovery() {
        Port p = new Port(); IdleRepairEngine e = new IdleRepairEngine(p, false, 0);
        e.tick(0); p.sco = true; e.tick(100); p.sco = false; e.tick(500);
        p.sco = true; e.tick(1000); e.tick(1749); assertEquals(0, p.releases);
        e.tick(1750); assertEquals(1, p.releases);
    }
    @Test public void teardownMustStayOffBeforeAnotherMethodStarts() {
        Port p = new Port(); IdleRepairEngine e = new IdleRepairEngine(p, false, 0);
        e.tick(0); e.tick(10000); e.tick(10250); p.sco = true; e.tick(10500);
        p.sco = false; e.tick(11000); e.tick(11749); assertEquals(1, p.requests.size());
        e.tick(11750); assertEquals(List.of(Method.MODERN, Method.MODERN), p.requests);
    }
    @Test public void twoModernAttemptsThenLegacyThenOptionalVoiceAreBounded() {
        Port p = new Port(); p.accepted = false;
        IdleRepairEngine e = new IdleRepairEngine(p, true, 0);
        e.tick(0);
        for (int n=1; n<=4; n++) { e.tick(n*1000); e.tick(n*1000+750); }
        assertEquals(List.of(Method.MODERN,Method.MODERN,Method.LEGACY_SCO,Method.VOICE_RECOGNITION), p.requests);
        assertEquals(Result.UNRESOLVED, e.result());
    }
    @Test public void disabledVoiceAndUnsupportedMethodsAreSkipped() {
        Port p = new Port(); p.supported.clear();
        IdleRepairEngine e = new IdleRepairEngine(p, false, 0); e.tick(0);
        assertTrue(p.requests.isEmpty()); assertEquals(Result.UNRESOLVED, e.result());
        assertEquals(4, p.events.stream().filter(x->x.startsWith("SKIP")).count());
    }
    @Test public void legacyCanBeUsedWhenModernDeviceIsNotAdvertised() {
        Port p = new Port(); p.supported.remove(Method.MODERN);
        IdleRepairEngine e = new IdleRepairEngine(p, false, 0); e.tick(0);
        assertEquals(List.of(Method.LEGACY_SCO), p.requests);
    }
    @Test public void discoveryWaitsForProxyAndKnownScoOff() {
        Port p = new Port(); p.ready=false; p.sco=null;
        IdleRepairEngine e = new IdleRepairEngine(p, false, 0); e.tick(0); e.tick(4999);
        assertTrue(p.requests.isEmpty()); p.ready=true; e.tick(4999); assertTrue(p.requests.isEmpty());
        p.sco=false; e.tick(4999); assertEquals(1,p.requests.size());
    }
    @Test public void unknownScoOrMissingTargetBlocksRatherThanGuessing() {
        for (boolean unknown : List.of(false,true)) {
            Port p = new Port(); p.ready = unknown; p.sco = unknown ? null : false;
            IdleRepairEngine e = new IdleRepairEngine(p,false,0); e.tick(5000);
            assertEquals(Result.BLOCKED,e.result()); assertTrue(p.requests.isEmpty());
        }
    }
    @Test public void anExistingScoLinkIsNeverClosedAsOurTest() {
        Port p=new Port(); p.sco=true; IdleRepairEngine e=new IdleRepairEngine(p,true,0);e.tick(0);
        assertEquals(Result.BLOCKED,e.result());assertTrue(p.requests.isEmpty());
    }
    @Test public void incomingCallOrNewAlternativeModeStopsAnyStage() {
        for (int stage=0;stage<3;stage++) {
            Port p=new Port();IdleRepairEngine e=new IdleRepairEngine(p,true,0);
            if(stage>0)e.tick(0);if(stage>1)e.tick(10000);
            p.block="new call/other owner";e.tick(10001);
            assertEquals(Result.BLOCKED,e.result());assertEquals(stage==0?0:1,p.requests.size());
        }
    }
    @Test public void rechecksGuardImmediatelyBeforeRequest() {
        Port p=new Port();p.blockOnSupport=true;IdleRepairEngine e=new IdleRepairEngine(p,true,0);e.tick(0);
        assertEquals(Result.BLOCKED,e.result());assertTrue(p.requests.isEmpty());
    }
    @Test public void lossOrAmbiguityOfPinnedTargetStopsOperation() {
        Port p=new Port();IdleRepairEngine e=new IdleRepairEngine(p,true,0);e.tick(0);p.ready=false;e.tick(1);
        assertEquals(Result.BLOCKED,e.result());assertEquals(1,p.requests.size());
    }
    @Test public void stuckScoNeverStartsAnotherAttempt() {
        Port p=new Port();IdleRepairEngine e=new IdleRepairEngine(p,true,0);e.tick(0);
        p.sco=true;e.tick(100);e.tick(850);e.tick(5850);
        assertEquals(Result.CLEANUP_FAILED,e.result());assertEquals(1,p.requests.size());
    }
    @Test public void unknownTeardownNeverCountsAsReleased() {
        Port p=new Port();IdleRepairEngine e=new IdleRepairEngine(p,true,0);e.tick(0);e.tick(10000);
        p.sco=null;e.tick(15000);assertEquals(Result.CLEANUP_FAILED,e.result());
    }
    @Test public void cleanupFailuresRetryWithinDeadlineButBlockFurtherMutations() {
        Port p=new Port();IdleRepairEngine e=new IdleRepairEngine(p,true,0);e.tick(0);
        p.clear=false;e.tick(10000);e.tick(10250);assertEquals(1,p.requests.size());
        p.clear=true;e.tick(11000);e.tick(11750);assertEquals(2,p.requests.size());
    }
    @Test public void persistentCleanupFailureIsReportedEvenOnCancellation() {
        Port p=new Port();IdleRepairEngine e=new IdleRepairEngine(p,true,0);e.tick(0);
        p.clear=false;e.tick(10000);e.tick(15000);assertEquals(Result.CLEANUP_FAILED,e.result());
        Port q=new Port();q.clear=false;IdleRepairEngine f=new IdleRepairEngine(q,true,0);f.cancel("activity paused");
        assertEquals(Result.CLEANUP_FAILED,f.result());
    }
    @Test public void userCancellationAndGlobalTimeoutReleaseOwnedRequests() {
        Port p=new Port();IdleRepairEngine e=new IdleRepairEngine(p,true,0);e.tick(0);e.cancel("stop");
        assertEquals(Result.CANCELLED,e.result());assertEquals(1,p.releases);
        Port q=new Port();IdleRepairEngine f=new IdleRepairEngine(q,true,0);f.tick(70000);
        assertEquals(Result.UNRESOLVED,f.result());assertTrue(q.requests.isEmpty());
    }
}

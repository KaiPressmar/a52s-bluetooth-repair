package de.kaipressmar.a52srepair.core;
import static org.junit.Assert.*;
import org.junit.Test;
import de.kaipressmar.a52srepair.core.repair.RecoveryBudget;
import de.kaipressmar.a52srepair.core.model.Evidence;
public class RecoveryBudgetTest {
    @Test public void everyReservationIncludingFailedRequestsSpendsTheSharedBudget() {
        RecoveryBudget b = new RecoveryBudget();
        assertEquals(15_000L, b.remainingMs(100));
        for (int i=0; i<4; i++) assertTrue(b.reserve(100+i, false));
        assertFalse(b.reserve(105, false));
        assertEquals(4,b.reserved()); assertEquals(0,b.dispatchedCount());
        assertEquals("REQUEST_BUDGET",b.stoppedReason());
        b.stop("OTHER"); assertEquals("REQUEST_BUDGET",b.stoppedReason());
    }
    @Test public void leaveRequiresReturnBudgetAndOnlyOneRebuildIsAllowed() {
        RecoveryBudget b=new RecoveryBudget();
        assertTrue(b.reserve(0,true)); b.dispatched();
        assertTrue(b.reserve(1,false)); b.dispatched();
        assertFalse(b.reserve(2,true)); assertEquals("REBUILD_BUDGET",b.stoppedReason());
        assertEquals(2,b.dispatchedCount());
        b=new RecoveryBudget();
        for(int i=0;i<3;i++) assertTrue(b.reserve(i,false));
        assertFalse(b.reserve(4,true));
    }
    @Test public void deadlineCannotBeExtendedByCallbacksAndClockRollbackFailsClosed() {
        RecoveryBudget b=new RecoveryBudget(); assertTrue(b.reserve(100,false));
        assertTrue(b.reserve(10_000,false));
        assertEquals(1,b.remainingMs(15_099));
        assertFalse(b.reserve(15_100,false)); assertEquals("DEADLINE",b.stoppedReason());
        b=new RecoveryBudget(); assertTrue(b.reserve(100,false));
        assertFalse(b.reserve(99,false));
    }
    @Test public void evidenceNeverTreatsMissingOrStaleDataAsKnown() {
        assertTrue(Evidence.known(false,10).fresh(20,10));
        assertFalse(Evidence.known(true,10).fresh(21,10));
        assertFalse(Evidence.known(true,10).fresh(9,10));
        assertFalse(Evidence.unknown(10).fresh(10,10));
        assertFalse(Evidence.unavailable(10).fresh(10,10));
        assertNull(Evidence.unknown(0).value);
        try { Evidence.known(null,0); fail(); } catch (IllegalArgumentException expected) { }
    }
}

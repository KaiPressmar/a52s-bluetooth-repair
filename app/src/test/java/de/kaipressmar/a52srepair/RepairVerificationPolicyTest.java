package de.kaipressmar.a52srepair;

import org.junit.Test;
import static org.junit.Assert.*;

public class RepairVerificationPolicyTest {
    @Test public void totalVerificationWindowIsThirtySeconds() {
        assertEquals(
                30_000L,
                RepairVerificationPolicy.MAX_ROUTE_SETTLE_MS);
    }

    @Test public void acceptedRequestIsNotAssumedSuccessfulBeforeScoIsSelected() {
        assertTrue(
                RepairVerificationPolicy.needsFinalVerification(
                        false,
                        true,
                        true,
                        false));
    }

    @Test public void successfulScoSelectionStopsFurtherVerification() {
        assertFalse(
                RepairVerificationPolicy.needsFinalVerification(
                        true,
                        true,
                        true,
                        false));
    }

    @Test public void endedCallStopsFurtherVerification() {
        assertFalse(
                RepairVerificationPolicy.needsFinalVerification(
                        false,
                        false,
                        true,
                        false));
    }

    @Test public void nonOwnedRouteIsNeverKeptAliveByVerificationLoop() {
        assertFalse(
                RepairVerificationPolicy.needsFinalVerification(
                        false,
                        true,
                        false,
                        false));
    }

    @Test public void finalVerificationCannotScheduleItselfAgain() {
        assertFalse(
                RepairVerificationPolicy.needsFinalVerification(
                        false,
                        true,
                        true,
                        true));
    }
}

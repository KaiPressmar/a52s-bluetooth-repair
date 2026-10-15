package de.kaipressmar.a52srepair.telecom;
import static org.junit.Assert.*;
import android.telecom.Call;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.List;
@RunWith(RobolectricTestRunner.class) @Config(sdk={31,34,36})
public class CallEligibilityTest {
    @Test public void ordinaryCellularCallIsAllowedButEmergencyAndUnknownHandlesAreNot() {
        Call call=org.robolectric.shadow.api.Shadow.newInstanceOf(Call.class);
        Call.Details details=org.robolectric.shadow.api.Shadow.newInstanceOf(Call.Details.class);
        org.robolectric.util.ReflectionHelpers.setField(details,"mState",Call.STATE_ACTIVE);
        org.robolectric.util.ReflectionHelpers.setField(details,"mHandle",android.net.Uri.parse("tel:+49305550123"));
        org.robolectric.util.ReflectionHelpers.setField(call,"mDetails",details);
        org.robolectric.util.ReflectionHelpers.setField(call,"mChildrenCached",true);
        org.robolectric.util.ReflectionHelpers.setField(call,"mUnmodifiableChildren",List.of());
        android.content.Context context=ApplicationProvider.getApplicationContext();
        assertTrue(CallEligibility.allows(context,List.of(call)));
        org.robolectric.util.ReflectionHelpers.setField(details,"mHandle",android.net.Uri.parse("tel:112"));
        assertFalse(CallEligibility.allows(context,List.of(call)));
        org.robolectric.util.ReflectionHelpers.setField(details,"mHandle",null);
        assertFalse(CallEligibility.allows(context,List.of(call)));
        org.robolectric.util.ReflectionHelpers.setField(details,"mHandle",android.net.Uri.parse("sip:private@example.invalid"));
        assertFalse(CallEligibility.allows(context,List.of(call)));
    }

    @Test public void emergencyExternalConferenceAndAppCallsAreExcluded() {
        assertTrue(CallEligibility.ordinaryProperties(0));
        for(int flag:new int[]{Call.Details.PROPERTY_CONFERENCE,Call.Details.PROPERTY_GENERIC_CONFERENCE,
                Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE,Call.Details.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL,
                Call.Details.PROPERTY_IS_EXTERNAL_CALL,Call.Details.PROPERTY_SELF_MANAGED,Call.Details.PROPERTY_VOIP_AUDIO_MODE})
            assertFalse(CallEligibility.ordinaryProperties(flag));
        assertFalse(CallEligibility.allows(ApplicationProvider.getApplicationContext(),List.of()));
        Call unknown=org.robolectric.shadow.api.Shadow.newInstanceOf(Call.class);
        assertFalse(CallEligibility.allows(ApplicationProvider.getApplicationContext(),List.of(unknown)));
    }
}

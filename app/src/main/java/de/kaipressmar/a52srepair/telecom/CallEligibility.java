package de.kaipressmar.a52srepair.telecom;

import android.content.Context;
import android.net.Uri;
import android.telecom.Call;
import android.telephony.TelephonyManager;
import java.util.List;

/** Fail closed for missing metadata, emergency calls and complex or non-cellular calls. */
final class CallEligibility {
    private CallEligibility() {}
    static boolean ordinaryProperties(int properties) {
        int excluded = Call.Details.PROPERTY_CONFERENCE | Call.Details.PROPERTY_GENERIC_CONFERENCE
                | Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE | Call.Details.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL
                | Call.Details.PROPERTY_IS_EXTERNAL_CALL | Call.Details.PROPERTY_SELF_MANAGED
                | Call.Details.PROPERTY_VOIP_AUDIO_MODE;
        return (properties & excluded) == 0;
    }
    static boolean allows(Context context, List<Call> calls) {
        if (calls.size() != 1) return false;
        Call call = calls.get(0);
        Call.Details details = call.getDetails();
        if (details == null || details.getState() != Call.STATE_ACTIVE
                || call.getParent() != null || !call.getChildren().isEmpty()) return false;
        if (!ordinaryProperties(details.getCallProperties())) return false;
        Uri handle = details.getHandle();
        if (handle == null || !"tel".equals(handle.getScheme())) return false;
        TelephonyManager phone = context.getSystemService(TelephonyManager.class);
        if (phone == null) return false;
        try {
            // The number is used only for this classification; it is never persisted or logged.
            return !phone.isEmergencyNumber(handle.getSchemeSpecificPart());
        } catch (RuntimeException unavailable) { return false; }
    }
}

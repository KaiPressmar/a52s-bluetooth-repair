package de.kaipressmar.a52srepair.ui.common;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.core.diagnosis.Fault;
import de.kaipressmar.a52srepair.core.report.CallOutcome;

/** Presentation of call outcomes and faults. */
public final class OutcomeStyle {
    @StringRes public final int title;
    @DrawableRes public final int icon;
    @ColorRes public final int color;
    @ColorRes public final int container;

    private OutcomeStyle(int title, int icon, int color, int container) {
        this.title = title;
        this.icon = icon;
        this.color = color;
        this.container = container;
    }

    public static OutcomeStyle of(CallOutcome outcome) {
        switch (outcome) {
            case HEALTHY:
                return new OutcomeStyle(R.string.outcome_healthy, R.drawable.ic_check_circle,
                        R.color.status_good, R.color.status_good_container);
            case REPAIRED:
                return new OutcomeStyle(R.string.outcome_repaired, R.drawable.ic_repair,
                        R.color.status_good, R.color.status_good_container);
            case LEFT_BLUETOOTH:
                return new OutcomeStyle(R.string.outcome_left_bluetooth, R.drawable.ic_call,
                        R.color.status_warn, R.color.status_warn_container);
            case UNRESOLVED:
                return new OutcomeStyle(R.string.outcome_unresolved, R.drawable.ic_warning,
                        R.color.status_bad, R.color.status_bad_container);
            case BLUETOOTH_UNAVAILABLE:
                return new OutcomeStyle(R.string.outcome_bluetooth_unavailable, R.drawable.ic_warning,
                        R.color.status_bad, R.color.status_bad_container);
            case NOT_BLUETOOTH:
            default:
                return new OutcomeStyle(R.string.outcome_not_bluetooth, R.drawable.ic_call,
                        R.color.status_neutral, R.color.status_neutral_container);
        }
    }

    @StringRes
    public static int faultLabel(Fault fault) {
        switch (fault) {
            case CALL_NOT_ON_BLUETOOTH:
                return R.string.fault_call_not_on_bluetooth;
            case SCO_DISCONNECTED:
                return R.string.fault_sco_disconnected;
            case DOWNLINK_NOT_ON_BLUETOOTH:
                return R.string.fault_downlink;
            case VOICE_SILENCED:
                return R.string.fault_voice_silenced;
            case BLUETOOTH_ROUTE_MISSING:
            default:
                return R.string.fault_route_missing;
        }
    }
}

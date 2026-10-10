package de.kaipressmar.a52srepair.ui.common;

import android.content.Context;
import android.text.format.DateUtils;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.core.diagnosis.Fault;
import de.kaipressmar.a52srepair.core.report.CallReport;
import java.util.ArrayList;
import java.util.List;

/** Locale-aware formatting for times, durations and report details. */
public final class Formatting {
    private Formatting() {}

    public static String when(Context context, long millis) {
        return DateUtils.getRelativeDateTimeString(
                        context,
                        millis,
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.WEEK_IN_MILLIS,
                        DateUtils.FORMAT_SHOW_TIME)
                .toString();
    }

    public static String duration(long millis) {
        return DateUtils.formatElapsedTime(Math.max(0L, millis / 1000L));
    }

    /** Fault labels plus repair facts, or an empty string for a plain healthy call. */
    public static String details(Context context, CallReport report) {
        List<String> parts = new ArrayList<>();
        for (Fault fault : report.faults) {
            parts.add(context.getString(OutcomeStyle.faultLabel(fault)));
        }
        int steps = report.routeAttempts + report.volumeRestores + report.manualRepairs;
        if (steps > 0) {
            parts.add(context.getResources().getQuantityString(R.plurals.history_actions, steps, steps));
        }
        if (report.preventiveRebuild) {
            parts.add(context.getString(R.string.history_preventive));
        }
        if (report.manualRepairs > 0) parts.add(context.getString(R.string.history_manual, report.manualRepairs));
        return String.join(" · ", parts);
    }
}

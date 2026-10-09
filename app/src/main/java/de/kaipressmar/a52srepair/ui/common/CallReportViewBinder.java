package de.kaipressmar.a52srepair.ui.common;

import android.content.res.ColorStateList;
import android.view.View;
import androidx.core.content.ContextCompat;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.core.report.CallReport;
import de.kaipressmar.a52srepair.databinding.ItemCallReportBinding;

/** Shared rendering of a call report row (history list and "last call" card). */
public final class CallReportViewBinder {
    private CallReportViewBinder() {}

    public static void bind(ItemCallReportBinding row, CallReport report) {
        android.content.Context context = row.getRoot().getContext();
        OutcomeStyle style = OutcomeStyle.of(report.outcome);
        row.reportIcon.setImageResource(style.icon);
        row.reportIcon.setImageTintList(
                ColorStateList.valueOf(ContextCompat.getColor(context, style.color)));
        row.reportIcon.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(context, style.container)));
        row.reportTitle.setText(style.title);
        row.reportMeta.setText(
                context.getString(
                        R.string.history_meta,
                        Formatting.when(context, report.startedAt),
                        Formatting.duration(report.durationMs)));
        String details = Formatting.details(context, report);
        row.reportDetail.setText(details);
        row.reportDetail.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);
    }
}

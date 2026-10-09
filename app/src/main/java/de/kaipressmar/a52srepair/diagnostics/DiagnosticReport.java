package de.kaipressmar.a52srepair.diagnostics;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import de.kaipressmar.a52srepair.BuildConfig;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.core.report.CallReport;
import de.kaipressmar.a52srepair.data.AppSettings;
import de.kaipressmar.a52srepair.data.CallReportRepository;
import de.kaipressmar.a52srepair.data.DeviceInfo;
import de.kaipressmar.a52srepair.setup.CarLinkManager;
import de.kaipressmar.a52srepair.setup.ProtectionStatus;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Builds a shareable plain-text report: environment, setup state, call history and log. */
public final class DiagnosticReport {
    private DiagnosticReport() {}

    public static String build(Context context) {
        AppSettings settings = new AppSettings(context);
        StringBuilder out = new StringBuilder();
        out.append("== A52s Bluetooth Repair diagnostic report ==\n")
                .append("app=").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                .append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" codename=").append(Build.DEVICE)
                .append(" sdk=").append(Build.VERSION.SDK_INT)
                .append(" build=").append(Build.DISPLAY).append('\n')
                .append("family=").append(DeviceInfo.family())
                .append(" defaultPreventive=").append(DeviceInfo.defaultPreventiveMode())
                .append('\n')
                .append("status=").append(ProtectionStatus.evaluate(context))
                .append(" callAccess=").append(ProtectionStatus.hasCallAccess(context))
                .append(" linked=").append(new CarLinkManager(context).linkedDevices().size())
                .append(" protection=").append(settings.protectionEnabled())
                .append(" preventive=").append(settings.preventiveMode())
                .append(" lastServiceBind=").append(settings.lastServiceBindAt())
                .append("\n\n== Calls (newest first) ==\n");

        List<CallReport> calls = new CallReportRepository(context).history().all();
        for (int i = 0; i < Math.min(50, calls.size()); i++) {
            CallReport call = calls.get(i);
            out.append(DiagnosticLog.timestamp(call.startedAt)).append(' ').append(call).append('\n');
        }
        out.append("\n== Log ==\n").append(DiagnosticLog.readAll(context));
        return out.toString();
    }

    /** Writes the report to the cache and returns a share intent, or null on I/O failure. */
    public static Intent shareIntent(Context context) {
        File dir = new File(context.getCacheDir(), "diagnostics");
        if (!dir.exists() && !dir.mkdirs()) return null;
        File file = new File(dir, "bluetooth-repair-report.txt");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(build(context).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent send =
                new Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, "A52s Bluetooth Repair – Diagnose")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(send, context.getString(R.string.share_chooser));
    }
}

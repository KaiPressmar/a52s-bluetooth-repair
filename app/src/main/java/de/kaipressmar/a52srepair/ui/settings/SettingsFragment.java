package de.kaipressmar.a52srepair.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.kaipressmar.a52srepair.BuildConfig;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.core.device.DeviceFamily;
import de.kaipressmar.a52srepair.data.CallReportRepository;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticLog;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticReport;
import de.kaipressmar.a52srepair.setup.CarLinkManager;
import de.kaipressmar.a52srepair.ui.MainActivity;
import de.kaipressmar.a52srepair.ui.common.SetupActions;
import java.util.List;

public final class SettingsFragment extends PreferenceFragmentCompat implements MainActivity.Refreshable {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        onClick("car_link", this::manageCarLink);
        onClick("share_report", () -> {
            Intent share = DiagnosticReport.shareIntent(requireContext());
            if (share != null) startActivity(share);
        });
        onClick("clear_history", () -> {
            new CallReportRepository(requireContext()).clear();
            DiagnosticLog.clear(requireContext());
            Toast.makeText(requireContext(), R.string.pref_clear_done, Toast.LENGTH_SHORT).show();
            actions().refreshStatus();
        });
        onClick("check_update", () -> actions().checkForUpdates());
        onClick("about_project", () -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.project_url)))));

        Preference version = findPreference("about_version");
        if (version != null) {
            version.setSummary(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        }
        Preference device = findPreference("about_device");
        if (device != null) {
            String key = getString(R.string.device_profile_key);
            String summary = getString(
                    R.string.pref_device_summary,
                    getString(R.string.device_profile_name),
                    getString(R.string.device_runtime_name),
                    getString(R.string.device_reference_firmware),
                    Build.MODEL);
            if (!DeviceFamily.matchesProfile(key, Build.MODEL, Build.DEVICE)) {
                summary += "\n" + getString(R.string.pref_device_mismatch,
                        getString(R.string.device_profile_name), Build.MODEL);
            }
            device.setSummary(summary);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void refresh() {
        Preference link = findPreference("car_link");
        if (link == null || !isAdded()) return;
        List<String> linked = new CarLinkManager(requireContext()).linkedDevices();
        link.setSummary(linked.isEmpty()
                ? getString(R.string.pref_car_link_none)
                : String.join(", ", linked));
    }

    private void manageCarLink() {
        CarLinkManager manager = new CarLinkManager(requireContext());
        if (manager.linkedDevices().isEmpty()) {
            actions().startCarLink();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_car_unlink_title)
                .setMessage(R.string.pref_car_unlink_body)
                .setPositiveButton(R.string.pref_car_unlink, (d, w) -> {
                    manager.unlinkAll();
                    actions().refreshStatus();
                })
                .setNeutralButton(R.string.pref_car_add, (d, w) -> actions().startCarLink())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void onClick(String key, Runnable action) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                action.run();
                return true;
            });
        }
    }

    private SetupActions actions() {
        return (SetupActions) requireActivity();
    }
}

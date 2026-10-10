package de.kaipressmar.a52srepair.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.kaipressmar.a52srepair.BuildConfig;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.data.AppSettings;
import de.kaipressmar.a52srepair.data.CallReportRepository;
import de.kaipressmar.a52srepair.data.DeviceInfo;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticLog;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticReport;
import de.kaipressmar.a52srepair.setup.CarLinkManager;
import de.kaipressmar.a52srepair.ui.MainActivity;
import de.kaipressmar.a52srepair.ui.common.SetupActions;
import java.util.List;

public final class SettingsFragment extends PreferenceFragmentCompat implements MainActivity.Refreshable {
    static final String KEY_APP_LANGUAGE = "app_language";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        bindLanguage();
        onClick("recovery_help", () -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.recovery_help_title).setMessage(R.string.recovery_help_body)
                .setPositiveButton(R.string.recovery_bluetooth, (d,w) -> openRecoverySettings(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                .setNeutralButton(R.string.recovery_developer, (d,w) -> openRecoverySettings(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                .setNegativeButton(R.string.action_close, null).show());
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
            device.setSummary(getString(
                    R.string.pref_device_summary, DeviceInfo.deviceName(), DeviceInfo.androidVersion()));
        }
        // Show the device-specific default instead of "not set".
        ListPreference preventive = findPreference(AppSettings.KEY_PREVENTIVE_MODE);
        if (preventive != null && preventive.getValue() == null) {
            preventive.setValue(DeviceInfo.defaultPreventiveMode().name());
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

    /** Per-app language via AppCompat: Android 13+ system setting, stored by AppCompat on 12. */
    private void bindLanguage() {
        ListPreference language = findPreference(KEY_APP_LANGUAGE);
        if (language == null) return;
        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
        language.setValue(current.isEmpty() ? "" : current.get(0).getLanguage());
        language.setOnPreferenceChangeListener((preference, value) -> {
            String tag = String.valueOf(value);
            AppCompatDelegate.setApplicationLocales(tag.isEmpty()
                    ? LocaleListCompat.getEmptyLocaleList()
                    : LocaleListCompat.forLanguageTags(tag));
            return true;
        });
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

    private void openRecoverySettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (RuntimeException e) { Toast.makeText(requireContext(), R.string.recovery_settings_unavailable, Toast.LENGTH_LONG).show(); }
    }

    private SetupActions actions() {
        return (SetupActions) requireActivity();
    }
}

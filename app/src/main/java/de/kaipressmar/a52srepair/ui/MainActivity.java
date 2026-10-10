package de.kaipressmar.a52srepair.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.IntentSender;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.data.AppSettings;
import de.kaipressmar.a52srepair.databinding.ActivityMainBinding;
import de.kaipressmar.a52srepair.setup.CarLinkManager;
import de.kaipressmar.a52srepair.setup.ProtectionStatus;
import de.kaipressmar.a52srepair.ui.common.SetupActions;
import de.kaipressmar.a52srepair.ui.history.HistoryFragment;
import de.kaipressmar.a52srepair.ui.settings.SettingsFragment;
import de.kaipressmar.a52srepair.ui.status.StatusFragment;
import de.kaipressmar.a52srepair.update.UpdateLinks;
import de.kaipressmar.a52srepair.update.UpdateRelease;
import de.kaipressmar.a52srepair.update.UpdateRepository;
import java.util.List;

/** Single activity: status, history and settings behind a bottom navigation bar. */
public final class MainActivity extends AppCompatActivity implements SetupActions {
    private static final String STATE_DESTINATION = "destination";

    private ActivityMainBinding binding;
    private CarLinkManager carLink;
    private UpdateRelease availableUpdate;

    private final ActivityResultLauncher<String> bluetoothPermission =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), granted -> refreshStatus());

    private final ActivityResultLauncher<IntentSenderRequest> linkConfirmation =
            registerForActivityResult(
                    new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> refreshStatus());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.root.setFitsSystemWindows(true);
        carLink = new CarLinkManager(this);

        binding.bottomNav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
        int destination =
                savedInstanceState == null
                        ? R.id.nav_status
                        : savedInstanceState.getInt(STATE_DESTINATION, R.id.nav_status);
        binding.bottomNav.setSelectedItemId(destination);
    }

    @Override
    protected void onStart() {
        super.onStart();
        UpdateRepository updates = new UpdateRepository(this);
        availableUpdate = updates.cachedNewerRelease();
        if (new AppSettings(this).autoUpdateCheck()) {
            updates.check(false, (release, error) -> {
                if (!canUpdateUi()) return;
                availableUpdate = release;
                refreshStatus();
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_DESTINATION, binding.bottomNav.getSelectedItemId());
    }

    private void show(@IdRes int destination) {
        String tag = String.valueOf(destination);
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        for (Fragment fragment : fm.getFragments()) {
            if (!tag.equals(fragment.getTag())) tx.hide(fragment);
        }
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            tx.add(R.id.fragment_container, create(destination), tag);
        } else {
            tx.show(target);
        }
        tx.commitNow();
        binding.toolbar.setTitle(titleFor(destination));
        refreshStatus();
    }

    private static Fragment create(@IdRes int destination) {
        if (destination == R.id.nav_history) return new HistoryFragment();
        if (destination == R.id.nav_settings) return new SettingsFragment();
        return new StatusFragment();
    }

    private static int titleFor(@IdRes int destination) {
        if (destination == R.id.nav_history) return R.string.nav_history;
        if (destination == R.id.nav_settings) return R.string.nav_settings;
        return R.string.app_name;
    }

    // --- SetupActions ---------------------------------------------------------------------

    @Override
    public void requestBluetoothPermission() {
        bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
    }

    @Override
    public void startCarLink() {
        if (!ProtectionStatus.hasBluetoothPermission(this)) {
            requestBluetoothPermission();
            return;
        }
        List<CarLinkManager.PairedDevice> devices = carLink.pairedAudioDevices();
        if (devices.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.setup_choose_device)
                    .setMessage(R.string.setup_no_devices)
                    .setPositiveButton(R.string.action_close, null)
                    .show();
            return;
        }
        String[] names = new String[devices.size()];
        for (int i = 0; i < names.length; i++) names[i] = devices.get(i).name;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setup_choose_device)
                .setItems(names, (dialog, which) -> explainAndLink(devices.get(which)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void explainAndLink(CarLinkManager.PairedDevice device) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setup_link_hint_title)
                .setMessage(R.string.setup_link_hint_body)
                .setPositiveButton(R.string.setup_link_continue, (d, w) -> link(device))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void link(CarLinkManager.PairedDevice device) {
        carLink.link(device, new CarLinkManager.Callback() {
            @Override
            public void onConfirmationRequired(IntentSender sender) {
                if (!canUpdateUi()) return;
                linkConfirmation.launch(new IntentSenderRequest.Builder(sender).build());
            }

            @Override
            public void onLinked(String deviceName) {
                if (!canUpdateUi()) return;
                Toast.makeText(MainActivity.this,
                        getString(R.string.setup_linked, deviceName), Toast.LENGTH_SHORT).show();
                // The companion role (and with it call access) is granted asynchronously.
                binding.getRoot().postDelayed(MainActivity.this::refreshStatus, 1_000L);
                refreshStatus();
            }

            @Override
            public void onFailed(CharSequence reason) {
                if (!canUpdateUi()) return;
                Toast.makeText(MainActivity.this,
                        getString(R.string.setup_link_failed, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void showAdbInstructions() {
        String command = ProtectionStatus.adbGrantCommand(this);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setup_adb_title)
                .setMessage(getString(R.string.setup_adb_body) + "\n\n" + command)
                .setPositiveButton(R.string.setup_adb_copy, (d, w) -> {
                    ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("adb", command));
                        Toast.makeText(this, R.string.setup_adb_copied, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    @Override
    public UpdateRelease availableUpdate() {
        return availableUpdate;
    }

    @Override
    public void installUpdate() {
        if (!canUpdateUi() || availableUpdate == null) return;
        UpdateRelease release = availableUpdate;
        UpdateLinks.Listener listener = (message, error) -> {
            if (canUpdateUi()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_title)
                .setMessage(R.string.update_security_help)
                .setPositiveButton(R.string.update_install, (d, w) -> UpdateLinks.openReleasePage(this, release, listener))
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    @Override
    public void checkForUpdates() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show();
        new UpdateRepository(this).check(true, this::onCheckedUpdateResult);
    }

    void onCheckedUpdateResult(UpdateRelease release, String error) {
        if (!canUpdateUi()) return;
        availableUpdate = release;
        int message = release != null ? R.string.update_title : R.string.update_none;
        if (release == null && error != null && !error.isEmpty()) {
            Toast.makeText(this, getString(R.string.update_failed, error), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
        if (release != null) binding.bottomNav.setSelectedItemId(R.id.nav_status);
        refreshStatus();
    }

    @Override
    public void refreshStatus() {
        if (!canUpdateUi()) return;
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof Refreshable && fragment.isAdded()) ((Refreshable) fragment).refresh();
        }
    }

    private boolean canUpdateUi() {
        return binding != null && !isFinishing() && !isDestroyed()
                && !getSupportFragmentManager().isStateSaved();
    }

    @Override protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    /** Implemented by screens that render app state. */
    public interface Refreshable {
        void refresh();
    }
}

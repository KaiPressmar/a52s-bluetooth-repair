package de.kaipressmar.a52srepair.ui.status;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.core.report.CallHistory;
import de.kaipressmar.a52srepair.core.report.CallOutcome;
import de.kaipressmar.a52srepair.core.report.CallReport;
import de.kaipressmar.a52srepair.data.AppSettings;
import de.kaipressmar.a52srepair.data.CallReportRepository;
import de.kaipressmar.a52srepair.databinding.FragmentStatusBinding;
import de.kaipressmar.a52srepair.databinding.ItemSetupStepBinding;
import de.kaipressmar.a52srepair.setup.ProtectionStatus;
import de.kaipressmar.a52srepair.ui.MainActivity;
import de.kaipressmar.a52srepair.ui.common.CallReportViewBinder;
import de.kaipressmar.a52srepair.ui.common.Formatting;
import de.kaipressmar.a52srepair.ui.common.SetupActions;
import de.kaipressmar.a52srepair.update.UpdateRelease;

/** Home screen: protection state, one-time setup, 30-day summary and the last call. */
public final class StatusFragment extends Fragment implements MainActivity.Refreshable {
    static final long STATS_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L;
    static final long REBOOT_HINT_WINDOW_MS = 24L * 60L * 60L * 1000L;

    private FragmentStatusBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentStatusBinding.inflate(inflater, container, false);
        binding.statCalls.statLabel.setText(R.string.stats_calls);
        binding.statRepaired.statLabel.setText(R.string.stats_repaired);
        binding.statProblems.statLabel.setText(R.string.stats_problems);
        binding.updateInstall.setOnClickListener(v -> actions().installUpdate());
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void refresh() {
        if (binding == null || !isAdded()) return;
        ProtectionStatus status = ProtectionStatus.evaluate(requireContext());
        CallHistory history = new CallReportRepository(requireContext()).history();
        renderHero(status, history);
        renderSetup(status);
        renderUpdate(actions().availableUpdate());
        renderStats(history);
        renderLastCall(history.latestBluetoothCall());
    }

    private void renderHero(ProtectionStatus status, CallHistory history) {
        CallReport last = history.latestBluetoothCall();
        boolean rebootNeeded =
                last != null
                        && last.outcome == CallOutcome.BLUETOOTH_UNAVAILABLE
                        && System.currentTimeMillis() - last.startedAt < REBOOT_HINT_WINDOW_MS;

        int title;
        int body;
        int icon;
        int color;
        int container;
        if (!status.setupComplete()) {
            title = R.string.status_setup_title;
            body = R.string.status_setup_body;
            icon = R.drawable.ic_shield;
            color = R.color.status_warn;
            container = R.color.status_warn_container;
        } else if (status == ProtectionStatus.PAUSED) {
            title = R.string.status_paused_title;
            body = R.string.status_paused_body;
            icon = R.drawable.ic_pause_circle;
            color = R.color.status_neutral;
            container = R.color.status_neutral_container;
        } else if (rebootNeeded) {
            title = R.string.status_reboot_title;
            body = R.string.status_reboot_body;
            icon = R.drawable.ic_warning;
            color = R.color.status_bad;
            container = R.color.status_bad_container;
        } else {
            title = R.string.status_active_title;
            body = status == ProtectionStatus.BLUETOOTH_OFF
                    ? R.string.status_bluetooth_off_body
                    : R.string.status_active_body;
            icon = R.drawable.ic_shield;
            color = R.color.status_good;
            container = R.color.status_good_container;
        }

        binding.heroCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), container));
        binding.heroIcon.setImageResource(icon);
        binding.heroIcon.setImageTintList(
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), color)));
        binding.heroTitle.setText(title);
        binding.heroBody.setText(body);

        long lastBind = new AppSettings(requireContext()).lastServiceBindAt();
        if (last != null) lastBind = Math.max(lastBind, last.startedAt);
        binding.heroMeta.setVisibility(status.setupComplete() ? View.VISIBLE : View.GONE);
        binding.heroMeta.setText(
                lastBind > 0
                        ? getString(R.string.status_last_active, Formatting.when(requireContext(), lastBind))
                        : getString(R.string.status_never_active));
    }

    private void renderSetup(ProtectionStatus status) {
        boolean bluetoothDone = ProtectionStatus.hasBluetoothPermission(requireContext());
        boolean callDone = ProtectionStatus.hasCallAccess(requireContext());
        binding.setupCard.setVisibility(bluetoothDone && callDone ? View.GONE : View.VISIBLE);

        bindStep(binding.stepBluetooth, R.drawable.ic_bluetooth, R.string.setup_bluetooth_title,
                R.string.setup_bluetooth_body, R.string.setup_bluetooth_action, bluetoothDone,
                v -> actions().requestBluetoothPermission());
        bindStep(binding.stepCallAccess, R.drawable.ic_car, R.string.setup_call_title,
                R.string.setup_call_body, R.string.setup_call_action, callDone,
                v -> actions().startCarLink());
        binding.stepCallAccess.stepSecondary.setVisibility(callDone ? View.GONE : View.VISIBLE);
        binding.stepCallAccess.stepSecondary.setText(R.string.setup_call_adb);
        binding.stepCallAccess.stepSecondary.setOnClickListener(v -> actions().showAdbInstructions());
    }

    private void bindStep(ItemSetupStepBinding step, int icon, int title, int body, int action,
            boolean done, View.OnClickListener onAction) {
        step.stepIcon.setImageResource(done ? R.drawable.ic_check_circle : icon);
        step.stepTitle.setText(title);
        step.stepBody.setText(done ? getString(R.string.setup_done) : getString(body));
        step.stepAction.setText(action);
        step.stepAction.setOnClickListener(onAction);
        step.stepActions.setVisibility(done ? View.GONE : View.VISIBLE);
    }

    private void renderUpdate(UpdateRelease release) {
        binding.updateCard.setVisibility(release == null ? View.GONE : View.VISIBLE);
        if (release != null) binding.updateBody.setText(getString(R.string.update_body, release.version));
    }

    private void renderStats(CallHistory history) {
        long since = System.currentTimeMillis() - STATS_WINDOW_MS;
        int problems = 0;
        for (CallOutcome outcome : CallOutcome.values()) {
            if (outcome.problem) problems += history.count(outcome, since);
        }
        binding.statCalls.statValue.setText(String.valueOf(history.bluetoothCallCount(since)));
        binding.statRepaired.statValue.setText(String.valueOf(history.count(CallOutcome.REPAIRED, since)));
        binding.statProblems.statValue.setText(String.valueOf(problems));
    }

    private void renderLastCall(CallReport last) {
        binding.lastCall.getRoot().setVisibility(last == null ? View.GONE : View.VISIBLE);
        binding.lastCallEmpty.setVisibility(last == null ? View.VISIBLE : View.GONE);
        if (last != null) CallReportViewBinder.bind(binding.lastCall, last);
    }

    private SetupActions actions() {
        return (SetupActions) requireActivity();
    }
}

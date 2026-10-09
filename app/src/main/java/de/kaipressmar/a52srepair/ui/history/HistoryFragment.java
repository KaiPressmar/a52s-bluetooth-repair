package de.kaipressmar.a52srepair.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import de.kaipressmar.a52srepair.core.report.CallReport;
import de.kaipressmar.a52srepair.data.CallReportRepository;
import de.kaipressmar.a52srepair.databinding.FragmentHistoryBinding;
import de.kaipressmar.a52srepair.ui.MainActivity;
import java.util.List;

/** All calls the app accompanied, newest first. */
public final class HistoryFragment extends Fragment implements MainActivity.Refreshable {
    private FragmentHistoryBinding binding;
    private final CallReportAdapter adapter = new CallReportAdapter();

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        binding.historyList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.historyList.addItemDecoration(
                new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        binding.historyList.setAdapter(adapter);
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
        if (binding == null) return;
        List<CallReport> reports = new CallReportRepository(requireContext()).history().all();
        adapter.submitList(reports);
        binding.historyEmpty.setVisibility(reports.isEmpty() ? View.VISIBLE : View.GONE);
    }
}

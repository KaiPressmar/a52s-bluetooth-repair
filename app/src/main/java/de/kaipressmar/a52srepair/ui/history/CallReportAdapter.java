package de.kaipressmar.a52srepair.ui.history;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import de.kaipressmar.a52srepair.core.report.CallReport;
import de.kaipressmar.a52srepair.databinding.ItemCallReportBinding;
import de.kaipressmar.a52srepair.ui.common.CallReportViewBinder;

final class CallReportAdapter extends ListAdapter<CallReport, CallReportAdapter.Holder> {
    CallReportAdapter() {
        super(new DiffUtil.ItemCallback<CallReport>() {
            @Override
            public boolean areItemsTheSame(@NonNull CallReport a, @NonNull CallReport b) {
                return a.startedAt == b.startedAt;
            }

            @Override
            public boolean areContentsTheSame(@NonNull CallReport a, @NonNull CallReport b) {
                return a.encode().equals(b.encode());
            }
        });
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCallReportBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CallReportViewBinder.bind(holder.binding, getItem(position));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemCallReportBinding binding;

        Holder(ItemCallReportBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

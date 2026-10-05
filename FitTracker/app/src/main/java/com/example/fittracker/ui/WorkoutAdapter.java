package com.example.fittracker.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fittracker.R;
import com.example.fittracker.model.Workout;
import com.example.fittracker.util.DateUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorkoutAdapter extends RecyclerView.Adapter<WorkoutAdapter.Holder> {

    public interface OnWorkoutLongClick {
        void onLongClick(Workout workout);
    }

    private final List<Workout> items = new ArrayList<>();
    private final OnWorkoutLongClick longClick;

    public WorkoutAdapter(OnWorkoutLongClick longClick) {
        this.longClick = longClick;
    }

    public void setItems(List<Workout> workouts) {
        items.clear();
        items.addAll(workouts);
        notifyDataSetChanged();
    }

    /** Fills an item_workout view. Also used by the home screen's "Recent" card. */
    public static void bind(View v, Workout w) {
        TextView emoji = v.findViewById(R.id.tv_emoji);
        emoji.setText(w.type.emoji);
        // Soft circle in the activity's own colour
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ColorUtils.setAlphaComponent(w.type.color, 40));
        emoji.setBackground(circle);

        ((TextView) v.findViewById(R.id.tv_title)).setText(w.type.label);
        ((TextView) v.findViewById(R.id.tv_subtitle)).setText(DateUtil.dateTime(w.startTime));

        StringBuilder stats = new StringBuilder();
        stats.append("⏱ ").append(DateUtil.duration(w.durationSec))
                .append("   🔥 ").append(Math.round(w.calories)).append(" Cal");
        if (w.distanceM > 0) {
            stats.append("   📍 ").append(String.format(Locale.getDefault(), "%.2f km", w.distanceM / 1000));
        }
        ((TextView) v.findViewById(R.id.tv_stats)).setText(stats);
        ((TextView) v.findViewById(R.id.tv_points)).setText("+" + w.heartPoints + " pts");
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_workout, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Workout w = items.get(position);
        bind(holder.itemView, w);
        holder.itemView.setOnLongClickListener(v -> {
            longClick.onLongClick(w);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }
}

package com.example.assistant;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;

import org.json.JSONException;
import org.json.JSONObject;

public class SummaryDialogFragment extends DialogFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(16, 16, 16, 16);
        rootLayout.setBackgroundColor(getResources().getColor(android.R.color.white));

        TextView summaryTextView = new TextView(getContext());
        summaryTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        summaryTextView.setTextSize(16);

        SharedPreferences preferences = requireActivity().getSharedPreferences("AudioCache", MODE_PRIVATE);
        String secondJson = preferences.getString("second_json", null);
        if (secondJson != null) {
            try {
                JSONObject jsonObject = new JSONObject(secondJson);
                String summary = jsonObject.getString("summary");
                summaryTextView.setText(summary);
            } catch (JSONException e) {
                summaryTextView.setText("Ошибка разбора JSON: " + e.getMessage());
            }
        } else {
            summaryTextView.setText("Пересказ недоступен");
        }

        rootLayout.addView(summaryTextView);
        return rootLayout;
    }
}
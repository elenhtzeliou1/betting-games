package com.example.bettingapp.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.bettingapp.R;
import com.example.bettingapp.activities.CasinoActivity;
import com.example.bettingapp.activities.PlayActivity;
import com.example.bettingapp.activities.RateActivity;
import com.example.bettingapp.viewmodel.AppViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

public class GameBottomSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "GameBottomSheetFragment";

    private static final String ARG_GAME_NAME = "gameName";
    private static final String ARG_PROVIDER = "providerName";
    private static final String ARG_MIN_BET = "minBet";
    private static final String ARG_MAX_BET = "maxBet";
    private static final String ARG_JACKPOT = "jackpot";
    private static final String ARG_RISK = "risk";
    private static final String ARG_BET_CATEGORY = "betCategory";
    private static final String ARG_STARS = "stars";
    private static final String ARG_VOTES = "votes";


    public static GameBottomSheetFragment newInstance(
            String gameName, String providerName, double minBet, double maxBet, double jackpot , String risk, String betCategory, double stars, int votes) {

        Bundle args = new Bundle();
        args.putString(ARG_GAME_NAME, gameName);
        args.putString(ARG_PROVIDER, providerName);
        args.putDouble(ARG_MIN_BET, minBet);
        args.putDouble(ARG_MAX_BET, maxBet);
        args.putDouble(ARG_JACKPOT, jackpot);
        args.putString(ARG_RISK, risk);
        args.putString(ARG_BET_CATEGORY, betCategory);
        args.putDouble(ARG_STARS, stars);
        args.putInt(ARG_VOTES, votes);


        GameBottomSheetFragment fragment = new GameBottomSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getTheme() {
        return R.style.GameBottomSheetTheme;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        String gameName = args.getString(ARG_GAME_NAME, "");
        String provider = args.getString(ARG_PROVIDER, "");
        double minBet = args.getDouble(ARG_MIN_BET, 0.0);
        double maxBet = args.getDouble(ARG_MAX_BET, 0.0);
        double jackpot = args.getDouble(ARG_JACKPOT, 0.0);
        String risk = args.getString(ARG_RISK, "");
        String betCategory = args.getString(ARG_BET_CATEGORY, "");
        double stars = args.getDouble(ARG_STARS, 0.0);
        int votes = args.getInt(ARG_VOTES, 0);

        applyBackground(view, risk);

        TextView tvGameName    = view.findViewById(R.id.tvGameName);
        TextView tvProvider    = view.findViewById(R.id.tvProvider);
        TextView tvMinBet      = view.findViewById(R.id.tvMinBet);
        TextView tvRisk        = view.findViewById(R.id.tvRisk);
        TextView tvBetCategory = view.findViewById(R.id.tvBetCategory);
        ImageButton btnClose   = view.findViewById(R.id.btnClose);
        Button btnPlay         = view.findViewById(R.id.btnPlay);
        Button btnRate         = view.findViewById(R.id.btnRate);

        tvGameName.setText(gameName.toUpperCase());
        tvProvider.setText(provider);
        String minBetValue = String.format(Locale.getDefault(), "%.2f", minBet);
        String minBetText = minBetValue + " FUN";
        SpannableString minBetSpan = new SpannableString(minBetText);
        minBetSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, minBetValue.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvMinBet.setText(minBetSpan);
        tvRisk.setText(formatRisk(risk));
        tvBetCategory.setText(betCategory);

        btnClose.setOnClickListener(v -> dismiss());

        // Get playerId from AppViewModel once for both buttons
        AppViewModel appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        String playerId = appViewModel.getPlayerId() != null ? appViewModel.getPlayerId() : "";

        // Play Button
        btnPlay.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PlayActivity.class);
            intent.putExtra(PlayActivity.EXTRA_GAME_NAME,    gameName);
            intent.putExtra(PlayActivity.EXTRA_PROVIDER,     provider);
            intent.putExtra(PlayActivity.EXTRA_BET_CATEGORY, betCategory);
            intent.putExtra(PlayActivity.EXTRA_RISK,         formatRisk(risk));
            intent.putExtra(PlayActivity.EXTRA_MIN_BET,      minBet);
            intent.putExtra(PlayActivity.EXTRA_MAX_BET,      maxBet);
            intent.putExtra(PlayActivity.EXTRA_JACKPOT,      jackpot);
            startActivity(intent);
            dismiss();
        });

        // Rate Button
        btnRate.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), RateActivity.class);
            intent.putExtra("GAME_NAME",         gameName);
            intent.putExtra("GAME_PROVIDER",     provider);
            intent.putExtra("GAME_RISK",         formatRisk(risk));
            intent.putExtra("GAME_BET_CATEGORY", betCategory);
            intent.putExtra("GAME_STARS",        stars);
            intent.putExtra("GAME_VOTES",        votes);
            startActivity(intent);
            dismiss();
        });

    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;

        int halfScreen = getResources().getDisplayMetrics().heightPixels / 2;
        bottomSheet.getLayoutParams().height = halfScreen;
        bottomSheet.requestLayout();
        BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void applyBackground(View view, String risk) {
        float density = getResources().getDisplayMetrics().density;
        float cornerRadius = 16 * density;

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getRiskColor(risk));
        bg.setCornerRadii(new float[]{
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                0, 0,
                0, 0
        });
        view.setBackground(bg);
    }

    private int getRiskColor(String risk) {
        if (risk == null) return Color.parseColor("#8B0000");
        switch (risk.toLowerCase()) {
            case "high":   return Color.parseColor("#8B0000");
            case "medium": return Color.parseColor("#7A4500");
            case "low":    return Color.parseColor("#1B5E20");
            default:       return Color.parseColor("#8B0000");
        }
    }

    private String formatRisk(String risk) {
        if (risk == null || risk.isEmpty()) return "Unknown Risk";
        return Character.toUpperCase(risk.charAt(0)) + risk.substring(1).toLowerCase() + " Risk";
    }
}
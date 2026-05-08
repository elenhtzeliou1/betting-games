package com.example.bettingapp.fragments;
import androidx.fragment.app.Fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.bettingapp.R;
import com.example.bettingapp.activities.CasinoActivity;
import com.example.bettingapp.activities.DepositActivity;
import com.example.bettingapp.activities.SignInActivity;

public class AccountFragment extends Fragment {

    private TextView boxAmount;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_account, container, false);

        // show username
        String playerId = ((CasinoActivity) requireActivity()).getPlayerId();
        ((TextView) view.findViewById(R.id.textView2)).setText(playerId);

        // close the account pop page
        view.findViewById(R.id.btnCloseAccount).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack()
        );

        // hold reference so onResume can update it
        boxAmount = view.findViewById(R.id.balanceBox).findViewById(R.id.boxAmount);

        // deposit btn inside balance box
        view.findViewById(R.id.balanceBox).findViewById(R.id.btnDeposit).setOnClickListener(v -> openDeposit());

        // Logout
        view.findViewById(R.id.logoutBox).setOnClickListener(v ->{
            Intent intent = new Intent(requireActivity(), SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh balance every time fragment becomes visible (return from deposit)
        if (boxAmount != null) {
            ((CasinoActivity) requireActivity()).fetchAndShowBalance(boxAmount);
        }
    }

    private void openDeposit() {
        Intent intent = new Intent(requireActivity(), DepositActivity.class);
        intent.putExtra("PLAYER_ID", ((CasinoActivity) requireActivity()).getPlayerId());
        startActivity(intent);
    }
}
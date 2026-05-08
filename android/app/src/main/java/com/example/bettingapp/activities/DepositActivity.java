package com.example.bettingapp.activities;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.bettingapp.R;
import com.example.bettingapp.network.MasterConnection;
import com.example.bettingapp.network.MasterProtocol;
import com.example.bettingapp.network.SocketCallback;
import com.example.bettingapp.network.SocketTask;
import com.example.bettingapp.viewmodel.AppViewModel;

import java.util.List;

public class DepositActivity extends AppCompatActivity {

    private String playerId;
    private MasterConnection masterConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deposit);

        AppViewModel appViewModel = new ViewModelProvider(this).get(AppViewModel.class);
        masterConnection = appViewModel.getConnection();

        // get player id
        playerId = appViewModel.getPlayerId();

        // Toolbar setup
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.toolbarTitle)).setText("DEPOSIT");

        EditText depositAmount = findViewById(R.id.depositAmount);

        findViewById(R.id.btnConfirmDeposit).setOnClickListener(v -> {
            String amount = depositAmount.getText().toString().trim();
            if (amount.isEmpty() || amount.equals("0") || amount.equals("0.0")) {
                Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            new SocketTask(masterConnection, MasterProtocol.addBalance(playerId, amount),
                    this, new SocketCallback() {
                @Override
                public void onSuccess(List<String> lines) {
                    Toast.makeText(DepositActivity.this,
                            "Deposit successful!", Toast.LENGTH_SHORT).show();
                    finish(); // go back — balance will refresh on resume
                }
                @Override
                public void onError(String message) {
                    Toast.makeText(DepositActivity.this,
                            "Deposit failed: " + message, Toast.LENGTH_LONG).show();
                }
            }).start();
        });

    }

}

package com.example.bettingapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.example.bettingapp.R;

public class SignInActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        EditText playerIdInput = findViewById(R.id.playerId);
        Button connectBtn = findViewById(R.id.btn);

        connectBtn.setOnClickListener(view -> {
            String playerId = playerIdInput.getText().toString().trim().toLowerCase();

            if (playerId.isEmpty()) {
                Toast.makeText(this, "Please enter your Player ID", Toast.LENGTH_SHORT).show();
                return;
            }

            // Pass playerId to CasinoActivity
            Intent intent = new Intent(this, CasinoActivity.class);
            intent.putExtra("PLAYER_ID", playerId);
            startActivity(intent);
            finish(); // can't go back to sign in ;)

        });

    }
}
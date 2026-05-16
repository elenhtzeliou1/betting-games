package com.example.bettingapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bettingapp.R;
import com.example.bettingapp.fragments.AccountFragment;
import com.example.bettingapp.fragments.FilterFragment;
import com.example.bettingapp.fragments.GamesFragment;
import com.example.bettingapp.network.MasterConnection;
import com.example.bettingapp.network.MasterProtocol;
import com.example.bettingapp.network.SocketCallback;
import com.example.bettingapp.network.SocketTask;
import com.example.bettingapp.viewmodel.AppViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CasinoActivity extends AppCompatActivity {

    private MasterConnection masterConnection;
    private AppViewModel appViewModel;
    private String playerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_casino);

        String incomingPlayerId = getIntent().getStringExtra("PLAYER_ID");
        if (incomingPlayerId == null || incomingPlayerId.trim().isEmpty()) {
            Toast.makeText(this, "Missing player id", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        playerId = incomingPlayerId.trim().toLowerCase();

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.toolbarRoot), (v, insets) -> {
                    int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    v.setPadding(v.getPaddingLeft(), topInset,
                            v.getPaddingRight(), v.getPaddingBottom());
                    return insets;
                });

        // Account opener
        findViewById(R.id.account).setOnClickListener(v -> openAccountFragment());

        // Deposit opener
        findViewById(R.id.toolbarBalance).setOnClickListener(v -> {
            Intent intent = new Intent(this, DepositActivity.class);
            intent.putExtra("PLAYER_ID", playerId);
            startActivity(intent);
        });

        // search opener
        findViewById(R.id.toolbarSearchBtn).setOnClickListener(v ->{
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra("PLAYER_ID", playerId);
            startActivity(intent);
        });


        appViewModel = new ViewModelProvider(this).get(AppViewModel.class);
        appViewModel.setPlayerId(playerId);
        masterConnection = appViewModel.getConnection();

        new Thread(() -> {
            try {
                if(!masterConnection.isConnected()) masterConnection.connect();

                // Fetch THIS player's existing ratings from the server
                List<String> ratingLines = masterConnection.sendCommand(MasterProtocol.getUserRatings(playerId));
                Map<String, Integer> ratingsMap = parseUserRatings(ratingLines);
                appViewModel.setUserRatings(ratingsMap);

                runOnUiThread(() -> {
                            loadFragment(new GamesFragment());
                            fetchAndShowBalance(findViewById(R.id.toolbarBalance));
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(this, "Connection failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show()
                );
            }
        }).start();

    }
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh balance when returning from DepositActivity
        if (masterConnection != null && masterConnection.isConnected()) {
            fetchAndShowBalance(findViewById(R.id.toolbarBalance));
        }
    }

    public void fetchAndShowBalance(TextView target) {
        new SocketTask(masterConnection, MasterProtocol.viewBalance(playerId),
                this, new SocketCallback() {
            @Override
            public void onSuccess(List<String> lines) {
                String balance = "0.00";
                if (!lines.isEmpty()) {
                    String line = lines.get(0);
                    if (line.contains("Balance: ")) {
                        String raw = line.substring(line.lastIndexOf("Balance: ") + 9).trim();
                        try {
                            balance = String.format(Locale.US, "%.2f",
                                    new BigDecimal(raw).doubleValue());
                        } catch (Exception ignored) {
                            balance = raw;
                        }
                    }
                }
                String display = balance + " FUN";
                target.setText(display);
                // Keep toolbar in sync
                TextView toolbar = findViewById(R.id.toolbarBalance);
                if (toolbar != null && toolbar != target) {
                    toolbar.setText(display);
                }
            }
            @Override
            public void onError(String message) {
                target.setText("0.00 FUN");
            }
        }).start();
    }

    public void openAccountFragment(){
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_up,    // enter
                        0,                     // exit (no animation for the fragment behind)
                        0,                     // pop enter
                        R.anim.slide_out_down  // pop exit (when X is pressed)
                )
                .replace(R.id.fullscreenContainer, new AccountFragment())
                .addToBackStack(null)
                .commit();
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    // Expected Lines: RATING|gameName|3
    // Returns a map of gameName (lowercase) -> stars
    private Map<String, Integer> parseUserRatings(List<String> lines) {
        Map<String, Integer> map = new HashMap<>();
        for (String line : lines) {
            if (!line.startsWith("RATING|")) continue;
            String[] parts = line.split("\\|");
            if (parts.length < 3) continue;
            try {
                String name = parts[1].trim().toLowerCase();
                int stars = Integer.parseInt(parts[2].trim());

                map.put(name, stars);
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isFinishing() && masterConnection != null) {
            masterConnection.disconnect();
        }
    }
}
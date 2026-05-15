package com.example.bettingapp.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bettingapp.R;
import com.example.bettingapp.adapters.GameAdapter;
import com.example.bettingapp.fragments.GameBottomSheetFragment;
import com.example.bettingapp.model.SearchResult;
import com.example.bettingapp.network.MasterConnection;
import com.example.bettingapp.network.MasterProtocol;
import com.example.bettingapp.network.SocketCallback;
import com.example.bettingapp.network.SocketTask;
import com.example.bettingapp.viewmodel.AppViewModel;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private int minStars = 0;
    private String betCategory = "ANY";
    private String risk = "ANY";

    private final ImageView[] stars = new ImageView[5];

    private GameAdapter gameAdapter;
    private TextView resultsCount;
    private RecyclerView recyclerView;

    private String playerId;
    private MasterConnection masterConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        AppViewModel appViewModel = new ViewModelProvider(this).get(AppViewModel.class);
        masterConnection = appViewModel.getConnection();

        playerId = getIntent().getStringExtra("PLAYER_ID");
        if (playerId == null || playerId.trim().isEmpty()) {
            playerId = appViewModel.getPlayerId();
        }

        if (playerId == null || playerId.trim().isEmpty()) {
            Toast.makeText(this, "Missing player id", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        playerId = playerId.trim().toLowerCase();
        appViewModel.setPlayerId(playerId);

        // Toolbar setup
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.toolbarTitle)).setText("SEARCH");

        // Results views
        resultsCount = findViewById(R.id.resultsCount);
        recyclerView = findViewById(R.id.searchRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        gameAdapter = new GameAdapter();
        recyclerView.setAdapter(gameAdapter);

        gameAdapter.setOnGameClickListener(game -> {
            GameBottomSheetFragment sheet = GameBottomSheetFragment.newInstance(
                    game.getGameName(),
                    game.getProviderName(),
                    game.getMinBet(),
                    game.getMaxBet(),
                    game.getJackpot(),
                    game.getRisk(),
                    game.getBetCategory(),
                    game.getStars(),
                    game.getNoOfVotes(),
                    game.getGameLogo()
            );

            sheet.show(getSupportFragmentManager(), GameBottomSheetFragment.TAG);
        });

        setupStars();
        setupBetChips();
        setupRiskChips();

        findViewById(R.id.btnSearch).setOnClickListener(v -> performSearch());
    }

    // Stars

    private void setupStars() {
        stars[0] = findViewById(R.id.star1);
        stars[1] = findViewById(R.id.star2);
        stars[2] = findViewById(R.id.star3);
        stars[3] = findViewById(R.id.star4);
        stars[4] = findViewById(R.id.star5);

        for (int i = 0; i < stars.length; i++) {
            final int tapped = i + 1;
            stars[i].setOnClickListener(v -> {
                // If user taps the already-selected star, reset to 0
                minStars = (minStars == tapped) ? 0 : tapped;
                updateStarUI();
            });
        }
    }

    private void updateStarUI() {
        for (int i = 0; i < stars.length; i++) {
            stars[i].setColorFilter(i < minStars
                    ? Color.parseColor("#971959")
                    : Color.parseColor("#444444"));
        }
    }

    // Bet category chips
    private void setupBetChips() {
        Button any = findViewById(R.id.chipBetAny);
        Button b1  = findViewById(R.id.chipBet1);
        Button b2  = findViewById(R.id.chipBet2);
        Button b3  = findViewById(R.id.chipBet3);

        Button[] chips   = {any, b1, b2, b3};
        String[] values  = {"ANY", "$", "$$", "$$$"};

        for (int i = 0; i < chips.length; i++) {
            final String val = values[i];
            chips[i].setOnClickListener(v -> {
                betCategory = val;
                highlightChips(chips, (Button) v);
            });
        }

        // Default selection
        highlightChips(chips, any);
    }

    // Risk chips
    private void setupRiskChips() {
        Button any    = findViewById(R.id.chipRiskAny);
        Button low    = findViewById(R.id.chipRiskLow);
        Button medium = findViewById(R.id.chipRiskMedium);
        Button high   = findViewById(R.id.chipRiskHigh);

        Button[] chips  = {any, low, medium, high};
        String[] values = {"ANY", "low", "medium", "high"};

        for (int i = 0; i < chips.length; i++) {
            final String val = values[i];
            chips[i].setOnClickListener(v -> {
                risk = val;
                highlightChips(chips, (Button) v);
            });
        }

        highlightChips(chips, any);
    }

    // Chip highlight helper

    private void highlightChips(Button[] chips, Button selected) {
        for (Button chip : chips) {
            chip.setSelected(chip == selected);
        }
    }

    // Search
    private void performSearch() {
        String command = MasterProtocol.search(playerId, minStars, betCategory, risk);

        new SocketTask(masterConnection, command, this, new SocketCallback() {
            @Override
            public void onSuccess(List<String> lines) {
                List<SearchResult> results = new ArrayList<>();
                for (String line : lines) {
                    if (line.startsWith(MasterProtocol.GAME_LINE_PREFIX)) {
                        SearchResult game = parseGameLine(line);
                        if (game != null) results.add(game);
                    }
                }

                gameAdapter.setGames(results);

                resultsCount.setText(results.size() + " results");
                resultsCount.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(results.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                resultsCount.setText("Error: " + message);
                resultsCount.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        }).start();
    }

    private SearchResult parseGameLine(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 10) return null;
        try {
            String logo = parts.length >= 11 ? parts[10].trim() : "";
            return new SearchResult(
                    parts[1].trim(),
                    parts[2].trim(),
                    parts[3].trim(),
                    parts[4].trim(),
                    parts[5].trim(),
                    parts[6].trim(),
                    parts[7].trim(),
                    parts[8].trim(),
                    parts[9].trim(),
                    logo
            );
        } catch (Exception e) {
            return null;
        }
    }
}
package com.example.bettingapp.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.bettingapp.R;
import com.example.bettingapp.network.MasterConnection;
import com.example.bettingapp.network.MasterProtocol;
import com.example.bettingapp.network.SocketCallback;
import com.example.bettingapp.network.SocketTask;
import com.example.bettingapp.viewmodel.AppViewModel;

import java.util.List;
import java.util.Locale;

public class RateActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "game_ratings";

    public static final String EXTRA_PLAYER_ID = "PLAYER_ID";

    private int selectedRating = 0;
    private ImageButton[] stars;
    private Button submitButton;
    private TextView ratingHint;
    private TextView communityRatingText;

    private String playerId;
    private String gameName;

    // for displaying community rating
    private double gameStars = 0.0;
    private int gameVotes = 0;

    private MasterConnection masterConnection;

    // set colors that are used
    private final int pink = Color.parseColor("#E91E83");
    private final int grey = Color.parseColor("#575057");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rate);

        // Get connection & player id from AppViewModel (main Thread)
        AppViewModel appViewModel = new ViewModelProvider(this).get(AppViewModel.class);
        masterConnection = appViewModel.getConnection();

        playerId = getIntent().getStringExtra(EXTRA_PLAYER_ID);
        if (playerId == null || playerId.trim().isEmpty()) {
            playerId = appViewModel.getPlayerId();
        }

        if (playerId == null || playerId.trim().isEmpty()) {
            Toast.makeText(this, "Missing player id", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        playerId = playerId.trim().toLowerCase();
        appViewModel.setPlayerId(playerId);

        // Toolbar
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("RATE GAME");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Views
        TextView gameTitle = findViewById(R.id.gameTitle);
        TextView gameMeta = findViewById(R.id.gameMeta);
        ratingHint = findViewById(R.id.ratingHint);
        communityRatingText = findViewById(R.id.communityRatingText);

        // Intent Data
        gameName = getIntent().getStringExtra("GAME_NAME");
        String provider = getIntent().getStringExtra("GAME_PROVIDER");
        String betCategory = getIntent().getStringExtra("GAME_BET_CATEGORY");
        String risk = getIntent().getStringExtra("GAME_RISK");
        gameStars = getIntent().getDoubleExtra("GAME_STARS", 0.0);
        gameVotes = getIntent().getIntExtra("GAME_VOTES",0);

        if (isEmpty(playerId)) {
            Toast.makeText(this, "Missing player id", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        gameName = isEmpty(gameName) ? "GameName" : gameName;

        gameTitle.setText(gameName);
        gameMeta.setText(
                (isEmpty(provider) ? "Provider" : provider)
                        + " · "
                        + (isEmpty(betCategory) ? "bruh" : betCategory)
                        + " · "
                        + (isEmpty(risk) ? "Risk" : risk)
        );
        // show the real community rating in launch
        refreshCommunityRating();

        // stars buttons and submit
        submitButton = findViewById(R.id.submitRatingButton);

        stars = new ImageButton[]{
                findViewById(R.id.star1),
                findViewById(R.id.star2),
                findViewById(R.id.star3),
                findViewById(R.id.star4),
                findViewById(R.id.star5)
        };

        // Restore saved rating (if the player already rated the game)
        int savedRating = getSavedRating();
        if (savedRating > 0){
            selectedRating = savedRating;
            updateStars();
            lockRatingUI();
        }else{
            // first time rating this game
            // allow interaction
            for(int i=0; i< stars.length; i++){
                final int rating = i+1;
                stars[i].setOnClickListener(v -> {
                    selectedRating = rating;
                    updateStars();
                    enableSubmitButton();
                });
            }
            updateStars();
            submitButton.setOnClickListener(v -> sendRatingToServer());
        }
    }

    private void sendRatingToServer() {
        if (selectedRating == 0) {
            Toast.makeText(this, "Select a rating first", Toast.LENGTH_SHORT).show();
            return;
        }

        submitButton.setEnabled(false);
        submitButton.setText("Submitting...");

        String command = MasterProtocol.rate(playerId, gameName, selectedRating);

        new SocketTask(masterConnection, command, this, new SocketCallback() {

            @Override
            public void onSuccess(List<String> lines) {
                // Worker sends ERROR when the player already rated
                if (!lines.isEmpty() && lines.get(0).startsWith("ERROR")) {
                    saveRating(selectedRating);
                    lockRatingUI();
                    Toast.makeText(RateActivity.this,
                            "You have already rated this game", Toast.LENGTH_SHORT).show();
                    return;
                }

                saveRating(selectedRating);

                gameStars = (gameStars * gameVotes + selectedRating) / (gameVotes + 1);
                gameVotes = gameVotes + 1;
                refreshCommunityRating();

                lockRatingUI();
                Toast.makeText(RateActivity.this,
                        "Rating submitted successfully!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                submitButton.setEnabled(true);
                submitButton.setText("Submit rating");
                Toast.makeText(RateActivity.this,
                        "Rating failed: " + message, Toast.LENGTH_LONG).show();
            }

        }).start();


    }

    // community rating helper for update / displaying
    private void refreshCommunityRating(){
        if(communityRatingText == null) return;

        if(gameVotes == 0){
            communityRatingText.setText("No ratings yet");
            return;
        }
        String starRow = buildStarRow(gameStars);
        String avg     = String.format(Locale.US, "%.1f", gameStars);
        String label   = gameVotes == 1 ? "1 vote" : gameVotes + " votes";

        communityRatingText.setText(starRow + "   " + avg + " · " + label);
    }

    //build the 5 str unicode by rounding their average to the nearest int
    private String buildStarRow(double avg) {
        int filled = (int) Math.round(avg);
        filled = Math.max(0, Math.min(5, filled));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) sb.append(" ");
            sb.append(i < filled ? "★" : "☆");
        }
        return sb.toString();
    }

    // --------------------
    // UI helpers

    private void updateStars() {
        for (int i = 0; i < stars.length; i++) {
            stars[i].setColorFilter(i < selectedRating ? pink : grey);
        }
    }

    private void enableSubmitButton() {
        submitButton.setEnabled(true);
        submitButton.setText("Submit rating");
        submitButton.setTextColor(Color.WHITE);
        submitButton.setBackground(
                ContextCompat.getDrawable(this, R.drawable.background_submit_enabled)
        );
    }

    //helper method for 'freezing' the ui
    private void lockRatingUI(){
        for (ImageButton star : stars) {
            star.setOnClickListener(null);
            star.setClickable(false);
        }

        // Replace submit button with a locked indicator
        submitButton.setEnabled(false);
        submitButton.setText("Already rated");
        submitButton.setTextColor(pink);
        submitButton.setBackground(
                ContextCompat.getDrawable(this, R.drawable.background_sumbit_disabled)
        );

        // Update hint text
        if (ratingHint != null) {
            ratingHint.setText("You've already rated this game");
            ratingHint.setTextColor(pink);
        }

    }

    /** Unique key: one entry per player + game combination. */
    private String getRatingKey() {
        return "rated_" + playerId + "_" + gameName.trim().toLowerCase();
    }

    /** Persist the rating so it survives app restarts. */
    private void saveRating(int rating) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(getRatingKey(), rating)
                .apply();
    }

    /** Returns 0 if the player has not rated this game yet. */
    private int getSavedRating() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(getRatingKey(), 0);
    }


    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** "high risk" → "High risk" */
    private String capitalize(String value) {
        if (isEmpty(value)) return value;
        String v = value.trim();
        return Character.toUpperCase(v.charAt(0)) + v.substring(1).toLowerCase();
    }
}
package com.example.bettingapp.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

public class PlayActivity extends AppCompatActivity {

    // ── Intent extra keys ─────────────────────────────────────────────────────
    public static final String EXTRA_PLAYER_ID = "PLAYER_ID";
    public static final String EXTRA_GAME_NAME = "GAME_NAME";
    public static final String EXTRA_PROVIDER = "GAME_PROVIDER";
    public static final String EXTRA_BET_CATEGORY = "GAME_BET_CATEGORY";
    public static final String EXTRA_RISK = "GAME_RISK";
    public static final String EXTRA_MIN_BET = "GAME_MIN_BET";
    public static final String EXTRA_MAX_BET  = "GAME_MAX_BET";
    public static final String EXTRA_JACKPOT = "GAME_JACKPOT";

    // Highest non-jackpot payout multiplier across all risk tables (HIGH, index 9)
    private static final BigDecimal MAX_NORMAL_MULTIPLIER = new BigDecimal("6.5");

    // Delay (ms) after a result before Place bet re-enables
    private static final long COOLDOWN_MS = 1000L;

    // Hero background colours per outcome
    private static final int BG_DEFAULT = Color.parseColor("#200C15");
    private static final int BG_LOADING = Color.parseColor("#200C15");
    private static final int BG_WIN = Color.parseColor("#0A2B15");
    private static final int BG_LOSS = Color.parseColor("#2B0A0A");
    private static final int BG_JACKPOT = Color.parseColor("#2B1E00");

    private static final int FADE_MS = 220;

    // ── Game data ─────────────────────────────────────────────────────────────
    private String playerId;
    private String gameName;
    private String risk;
    private BigDecimal minBet;
    private BigDecimal maxBet;
    private BigDecimal step;
    private BigDecimal currentBet;

    // ── Views ─────────────────────────────────────────────────────────────────
    private View heroContainer;
    private View heroDefault;
    private View heroLoading;
    private View heroResult;
    private TextView tvResultTitle;
    private TextView tvResultPayout;
    private TextView tvResultBalance;

    private TextView tvBetAmount;
    private TextView tvBetHint;
    private Button btnBetMinus;
    private Button btnBetPlus;
    private Button btnPlaceBet;
    private ProgressBar btnPlaceBetProgress;  // spinner overlaid on the button
    private TextView toolbarBalance;

    private MasterConnection masterConnection;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int heroTransitionId = 0;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        // --- Connection & playerId from AppViewModel (main thread) ---
        AppViewModel appViewModel = new ViewModelProvider(this).get(AppViewModel.class);
        masterConnection = appViewModel.getConnection();

        playerId = getIntent().getStringExtra(EXTRA_PLAYER_ID);
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

        // --- Intent extras ---
        gameName = getIntent().getStringExtra(EXTRA_GAME_NAME);
        String provider = getIntent().getStringExtra(EXTRA_PROVIDER);
        String betCategory = getIntent().getStringExtra(EXTRA_BET_CATEGORY);
        risk = getIntent().getStringExtra(EXTRA_RISK);
        double minBetD = getIntent().getDoubleExtra(EXTRA_MIN_BET,  1.0);
        double maxBetD = getIntent().getDoubleExtra(EXTRA_MAX_BET,  100.0);
        double jackpotD = getIntent().getDoubleExtra(EXTRA_JACKPOT,  10.0);

        minBet = BigDecimal.valueOf(minBetD).setScale(2, RoundingMode.HALF_UP);
        maxBet  = BigDecimal.valueOf(maxBetD).setScale(2, RoundingMode.HALF_UP);
        currentBet = minBet;
        step = deriveStep(minBet);

        // --- Toolbar ---
        ((TextView) findViewById(R.id.toolbarTitle)).setText("Play");
        toolbarBalance = findViewById(R.id.toolbarBalance);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        fetchBalance();

        // --- Hero state views ---
        heroContainer = findViewById(R.id.heroContainer);
        heroDefault = findViewById(R.id.heroDefault);
        heroLoading = findViewById(R.id.heroLoading);
        heroResult = findViewById(R.id.heroResult);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultPayout = findViewById(R.id.tvResultPayout);
        tvResultBalance = findViewById(R.id.tvResultBalance);

        // --- Risk badge ---
        applyRiskBadge(risk);

        // --- Game info ---
        ((TextView) findViewById(R.id.tvGameName)).setText(gameName != null ? gameName : "");
        String meta = (provider != null ? provider : "")
                + (!isEmpty(betCategory) ? " · " + betCategory : "");
        ((TextView) findViewById(R.id.tvGameMeta)).setText(meta);

        // --- Stat cards ---
        ((TextView) findViewById(R.id.tvMinBetValue)).setText(
                String.format(Locale.US, "%.2f FUN", minBetD));
        ((TextView) findViewById(R.id.tvMaxBetValue)).setText(
                String.format(Locale.US, "%.2f FUN", maxBetD));
        ((TextView) findViewById(R.id.tvJackpotValue)).setText((int) jackpotD + "x");

        // Tapping Min bet / Max bet card sets the current bet instantly
        findViewById(R.id.cardMinBet).setOnClickListener(v -> {
            currentBet = minBet;
            updateBetDisplay();
        });
        findViewById(R.id.cardMaxBet).setOnClickListener(v -> {
            currentBet = maxBet;
            updateBetDisplay();
        });

        // --- Bet control ---
        tvBetAmount         = findViewById(R.id.tvBetAmount);
        tvBetHint           = findViewById(R.id.tvBetHint);
        btnBetMinus         = findViewById(R.id.btnBetMinus);
        btnBetPlus          = findViewById(R.id.btnBetPlus);
        btnPlaceBet         = findViewById(R.id.btnPlaceBet);
        btnPlaceBetProgress = findViewById(R.id.btnPlaceBetProgress);

        updateBetDisplay();
        btnBetMinus.setOnClickListener(v -> adjustBet(false));
        btnBetPlus .setOnClickListener(v -> adjustBet(true));

        // Place bet is the single trigger for all plays — initial and replays
        btnPlaceBet.setOnClickListener(v -> placeBet());
    }

    // ── Hero state machine ────────────────────────────────────────────────────

    private enum HeroState { DEFAULT, LOADING, RESULT }

    private void transitionHero(HeroState state, BetResult result) {
        final int transitionId = ++heroTransitionId;
        final View nextView = viewForState(state);
        final int  nextBg   = bgForResult(state, result);

        for (View v : new View[]{heroDefault, heroLoading, heroResult}) {
            v.animate().setListener(null);
            v.animate().cancel();
        }

        if (state == HeroState.RESULT && result != null) {
            populateResultViews(result);
        }

        // Fade out currently visible states
        for (View v : new View[]{heroDefault, heroLoading, heroResult}) {
            if (v.getVisibility() == View.VISIBLE && v != nextView) {
                final View toHide = v;
                toHide.animate()
                        .alpha(0f)
                        .setDuration(FADE_MS)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override public void onAnimationEnd(Animator a) {
                                if (transitionId == heroTransitionId) {
                                    toHide.setVisibility(View.GONE);
                                    toHide.setAlpha(1f);
                                }
                            }
                        }).start();
            }
        }

        heroContainer.setBackgroundColor(nextBg);

        // Fade in the target state
        nextView.setAlpha(0f);
        nextView.setVisibility(View.VISIBLE);
        nextView.animate().alpha(1f).setDuration(FADE_MS).setListener(null).start();
    }

    private View viewForState(HeroState state) {
        switch (state) {
            case LOADING: return heroLoading;
            case RESULT:  return heroResult;
            default:      return heroDefault;
        }
    }

    private int bgForResult(HeroState state, BetResult result) {
        if (state == HeroState.LOADING) return BG_LOADING;
        if (state == HeroState.RESULT && result != null) {
            if (result.isJackpot) return BG_JACKPOT;
            return result.isWin   ? BG_WIN : BG_LOSS;
        }
        return BG_DEFAULT;
    }

    private void populateResultViews(BetResult r) {
        tvResultTitle.setText(r.title);
        tvResultTitle.setTextColor(r.titleColour);
        tvResultPayout.setText(r.payoutLine);
        tvResultPayout.setTextColor(r.payoutColour);
        tvResultBalance.setText(r.balanceLine);
    }

    // ── Bet control ───────────────────────────────────────────────────────────

    private void adjustBet(boolean increase) {
        if (increase) {
            BigDecimal next = currentBet.add(step);
            currentBet = next.compareTo(maxBet) <= 0 ? next : maxBet;
        } else {
            BigDecimal next = currentBet.subtract(step);
            currentBet = next.compareTo(minBet) >= 0 ? next : minBet;
        }
        updateBetDisplay();
    }

    private void updateBetDisplay() {
        tvBetAmount.setText(String.format(Locale.US, "%.2f", currentBet.doubleValue()));
        tvBetHint.setText(String.format(Locale.US,
                "Min: %.2f  ·  Max: %.2f", minBet.doubleValue(), maxBet.doubleValue()));
        btnBetMinus.setAlpha(currentBet.compareTo(minBet) <= 0 ? 0.35f : 1f);
        btnBetPlus .setAlpha(currentBet.compareTo(maxBet) >= 0 ? 0.35f : 1f);
    }

    /**
     * Locks or unlocks all interactive bet controls.
     * While locked the Place bet button shows a spinner instead of its label.
     */
    private void setControlsLocked(boolean locked) {
        btnBetMinus.setEnabled(!locked);
        btnBetPlus .setEnabled(!locked);
        btnPlaceBet.setEnabled(!locked);

        // Toggle the in-button spinner
        if (locked) {
            btnPlaceBet.setText("");
            btnPlaceBetProgress.setVisibility(View.VISIBLE);
            btnBetMinus.setAlpha(0.35f);
            btnBetPlus .setAlpha(0.35f);
            // Also lock min/max shortcut cards
            findViewById(R.id.cardMinBet).setEnabled(false);
            findViewById(R.id.cardMaxBet).setEnabled(false);
        } else {
            btnPlaceBet.setText("Place bet");
            btnPlaceBetProgress.setVisibility(View.GONE);
            updateBetDisplay(); // restore +/- alpha to correct state
            // Re-enable min/max shortcut cards
            findViewById(R.id.cardMinBet).setEnabled(true);
            findViewById(R.id.cardMaxBet).setEnabled(true);
        }
    }

    // ── Network — place bet ───────────────────────────────────────────────────

    private void placeBet() {
        // Lock everything and show hero loading state
        setControlsLocked(true);
        transitionHero(HeroState.LOADING, null);

        String command = MasterProtocol.play(playerId, gameName, currentBet.toPlainString());

        new SocketTask(masterConnection, command, this, new SocketCallback() {
            @Override
            public void onSuccess(List<String> lines) {
                String playResultLine = null;
                String newBalance     = null;

                for (String line : lines) {
                    if (line.startsWith("PLAY_RESULT")) playResultLine = line;
                    if (line.contains("Balance: "))
                        newBalance = line.substring(line.lastIndexOf("Balance: ") + 9).trim();
                }

                // Server returned an error (insufficient funds, game not found, etc.)
                if (playResultLine == null) {
                    String msg = lines.isEmpty() ? "Unknown error" : lines.get(0);
                    Toast.makeText(PlayActivity.this, msg, Toast.LENGTH_LONG).show();
                    transitionHero(HeroState.DEFAULT, null);
                    setControlsLocked(false);
                    return;
                }

                BigDecimal payout = parsePayout(playResultLine);

                // Update the balance pill immediately
                if (newBalance != null) toolbarBalance.setText(newBalance + " FUN");

                // Show result in the hero, keep controls locked during cooldown
                BetResult result = BetResult.from(payout, currentBet, newBalance);
                transitionHero(HeroState.RESULT, result);

                // Re-enable Place bet after 1-second cooldown so the user
                // can read the result before accidentally firing another bet
                mainHandler.postDelayed(() -> setControlsLocked(false), COOLDOWN_MS);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(PlayActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
                transitionHero(HeroState.DEFAULT, null);
                setControlsLocked(false);
            }
        }).start();
    }

    // ── Network — balance ─────────────────────────────────────────────────────

    private void fetchBalance() {
        new SocketTask(masterConnection, MasterProtocol.viewBalance(playerId),
                this, new SocketCallback() {
            @Override
            public void onSuccess(List<String> lines) {
                if (lines.isEmpty()) return;
                String line = lines.get(0);
                if (line.contains("Balance: ")) {
                    String bal = line.substring(line.lastIndexOf("Balance: ") + 9).trim();
                    toolbarBalance.setText(bal + " FUN");
                }
            }
            @Override public void onError(String message) { /* keep placeholder */ }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal parsePayout(String line) {
        try {
            for (String part : line.split("\\|")) {
                String t = part.trim();
                if (t.startsWith("payout="))
                    return new BigDecimal(t.substring("payout=".length()).trim());
            }
        } catch (Exception ignored) {}
        return BigDecimal.ZERO;
    }

    private void applyRiskBadge(String risk) {
        TextView badge = findViewById(R.id.tvRiskBadge);
        if (badge == null || risk == null) return;
        String raw = risk.replace(" Risk", "").trim();
        badge.setText(raw);
        int color;
        switch (raw.toLowerCase()) {
            case "high":   color = Color.parseColor("#FF4444"); break;
            case "medium": color = Color.parseColor("#FFA500"); break;
            default:       color = Color.parseColor("#44BB44"); break;
        }
        badge.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private BigDecimal deriveStep(BigDecimal minBet) {
        if (minBet.compareTo(BigDecimal.ONE) < 0)         return new BigDecimal("0.10");
        if (minBet.compareTo(BigDecimal.valueOf(5)) < 0)  return new BigDecimal("1.00");
        return new BigDecimal("5.00");
    }

    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    // ── BetResult value object ────────────────────────────────────────────────

    private static final class BetResult {

        final boolean isJackpot;
        final boolean isWin;
        final String title;
        final int titleColour;
        final String payoutLine;
        final int payoutColour;
        final String balanceLine;

        private BetResult(boolean isJackpot, boolean isWin,
                          String title, int titleColour,
                          String payoutLine, int payoutColour,
                          String balanceLine) {
            this.isJackpot    = isJackpot;
            this.isWin = isWin;
            this.title  = title;
            this.titleColour  = titleColour;
            this.payoutLine   = payoutLine;
            this.payoutColour = payoutColour;
            this.balanceLine  = balanceLine;
        }

        static BetResult from(BigDecimal payout, BigDecimal bet, String newBalance) {
            boolean isJackpot = payout.compareTo(bet.multiply(MAX_NORMAL_MULTIPLIER)) > 0;
            boolean isWin     = payout.compareTo(BigDecimal.ZERO) > 0;

            String title;
            int    titleColour;
            String payoutLine;
            int    payoutColour;

            if (isJackpot) {
                title = "JACKPOT!";
                titleColour = Color.parseColor("#FFD700");
                payoutLine = "+" + String.format(Locale.US, "%.2f FUN", payout.doubleValue());
                payoutColour = Color.parseColor("#FFD700");
            } else if (isWin) {
                title = "YOU WON!";
                titleColour = Color.parseColor("#4CAF50");
                payoutLine = "+" + String.format(Locale.US, "%.2f FUN", payout.doubleValue());
                payoutColour = Color.parseColor("#4CAF50");
            } else {
                title = "NO LUCK";
                titleColour = Color.parseColor("#FF5252");
                payoutLine = "−" + String.format(Locale.US, "%.2f FUN", bet.doubleValue());
                payoutColour = Color.parseColor("#FF5252");
            }

            String balanceLine = newBalance != null ? "Balance:  " + newBalance + " FUN" : "";
            return new BetResult(isJackpot, isWin, title, titleColour,
                    payoutLine, payoutColour, balanceLine);
        }
    }
}

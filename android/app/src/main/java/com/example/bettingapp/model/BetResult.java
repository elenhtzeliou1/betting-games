package com.example.bettingapp.model;

import android.graphics.Color;
import java.math.BigDecimal;
import java.util.Locale;

public final class BetResult {

    private static final BigDecimal MAX_NORMAL_MULTIPLIER = new BigDecimal("6.5");

    public final boolean isJackpot;
    public final boolean isWin;
    public final String title;
    public final int titleColour;
    public final String payoutLine;
    public final int payoutColour;
    public final String balanceLine;

    private BetResult(boolean isJackpot, boolean isWin,
                      String title, int titleColour,
                      String payoutLine, int payoutColour,
                      String balanceLine) {
        this.isJackpot    = isJackpot;
        this.isWin        = isWin;
        this.title        = title;
        this.titleColour  = titleColour;
        this.payoutLine   = payoutLine;
        this.payoutColour = payoutColour;
        this.balanceLine  = balanceLine;
    }

    public static BetResult from(BigDecimal payout, BigDecimal bet, String newBalance) {
        boolean isJackpot = payout.compareTo(bet.multiply(MAX_NORMAL_MULTIPLIER)) > 0;
        boolean isWin     = payout.compareTo(BigDecimal.ZERO) > 0;

        String title;
        int    titleColour;
        String payoutLine;
        int    payoutColour;

        if (isJackpot) {
            title        = "JACKPOT!";
            titleColour  = Color.parseColor("#FFD700");
            payoutLine   = "+" + String.format(Locale.US, "%.2f FUN", payout.doubleValue());
            payoutColour = Color.parseColor("#FFD700");
        } else if (isWin) {
            title        = "YOU WON!";
            titleColour  = Color.parseColor("#4CAF50");
            payoutLine   = "+" + String.format(Locale.US, "%.2f FUN", payout.doubleValue());
            payoutColour = Color.parseColor("#4CAF50");
        } else {
            title        = "NO LUCK";
            titleColour  = Color.parseColor("#FF5252");
            payoutLine   = "−" + String.format(Locale.US, "%.2f FUN", bet.doubleValue());
            payoutColour = Color.parseColor("#FF5252");
        }

        String balanceLine = newBalance != null ? "Balance:  " + newBalance + " FUN" : "";
        return new BetResult(isJackpot, isWin, title, titleColour,
                payoutLine, payoutColour, balanceLine);
    }
}
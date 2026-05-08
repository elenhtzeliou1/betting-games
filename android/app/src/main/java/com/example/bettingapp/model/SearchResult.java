package com.example.bettingapp.model;

public class SearchResult {

    private final String gameName;
    private final String providerName;
    private final double stars;
    private final int noOfVotes;
    private final String betCategory;  // "$", "$$", "$$$"
    private final String risk;         // low, medium, high
    private final double minBet;
    private final double maxBet;
    private final double jackpot;
    private final String gameLogo;

    public SearchResult(String gameName, String providerName, String stars,
                        String noOfVotes, String betCategory, String risk,
                        String minBet, String maxBet, String jackpot, String gameLogo) {
        this.gameName = gameName;
        this.providerName = providerName;
        this.stars = Double.parseDouble(stars);
        this.noOfVotes = Integer.parseInt(noOfVotes);
        this.betCategory = betCategory;
        this.risk = risk;
        this.minBet = Double.parseDouble(minBet);
        this.maxBet = Double.parseDouble(maxBet);
        this.jackpot = Double.parseDouble(jackpot);
        this.gameLogo = gameLogo;
    }

    public String getGameName() { return gameName; }
    public String getProviderName() { return providerName; }
    public double getStars() { return stars; }
    public int getNoOfVotes() { return noOfVotes; }
    public String getBetCategory() { return betCategory; }
    public String getRisk() { return risk; }
    public double getMinBet() { return minBet; }
    public double getMaxBet() { return maxBet; }
    public double getJackpot() { return jackpot; }
    public String getGameLogo() {return gameLogo;}
}
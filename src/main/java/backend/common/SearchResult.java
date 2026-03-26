package backend.common;

public class SearchResult {

    private final String gameName;
    private final String providerName;
    private final String stars;
    private final String betCategory;
    private final String risk;
    private final String minBet;
    private final String maxBet;
    private final String jackpot;

    public SearchResult(String gameName,
                        String providerName,
                        String stars,
                        String betCategory,
                        String risk,
                        String minBet,
                        String maxBet,
                        String jackpot) {
        this.gameName = gameName;
        this.providerName = providerName;
        this.stars = stars;
        this.betCategory = betCategory;
        this.risk = risk;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.jackpot = jackpot;
    }

    public String getGameName() { return gameName; }
    public String getProviderName() { return providerName; }
    public String getStars() { return stars; }
    public String getBetCategory() { return betCategory; }
    public String getRisk() { return risk; }
    public String getMinBet() { return minBet; }
    public String getMaxBet() { return maxBet; }
    public String getJackpot(){return jackpot;}

    @Override
    public String toString() {
        return "GameName: " + gameName +
                " | Provider: " + providerName +
                " | Stars: " + stars +
                " | BetCategory: " + betCategory +
                " | Risk: " + risk +
                " | MinBet: " + minBet +
                " | MaxBet: " + maxBet +
                " | Jackpot: "+ jackpot;
    }


}

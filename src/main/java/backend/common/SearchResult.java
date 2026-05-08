package backend.common;

public class SearchResult {

    private final String gameName;
    private final String providerName;
    private final String stars;
    private final String noOfVotes;
    private final String betCategory;
    private final String risk;
    private final String minBet;
    private final String maxBet;
    private final String jackpot;
    private final String gameLogo;

    public SearchResult(String gameName,
                        String providerName,
                        String stars,
                        String noOfVotes,
                        String betCategory,
                        String risk,
                        String minBet,
                        String maxBet,
                        String jackpot,
                        String gameLogo) {
        this.gameName = gameName;
        this.providerName = providerName;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.betCategory = betCategory;
        this.risk = risk;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.jackpot = jackpot;
        this.gameLogo = gameLogo;
    }

    public String getGameName() { return gameName; }
    public String getProviderName() { return providerName; }
    public String getStars() { return stars; }
    public String getNoOfVotes(){return noOfVotes;}
    public String getBetCategory() { return betCategory; }
    public String getRisk() { return risk; }
    public String getMinBet() { return minBet; }
    public String getMaxBet() { return maxBet; }
    public String getJackpot(){return jackpot;}
    public String getGameLogo(){ return gameLogo; }

    @Override
    public String toString() {
        return "GameName: " + gameName +
                " | Provider: " + providerName +
                " | Stars: " + stars +
                " | noOfVotes: "+ noOfVotes+
                " | BetCategory: " + betCategory +
                " | Risk: " + risk +
                " | MinBet: " + minBet +
                " | MaxBet: " + maxBet +
                " | Jackpot: " + jackpot +
                " | GameLogo: " + gameLogo;
    }
}

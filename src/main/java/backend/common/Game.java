package backend.common;

public class Game {
    private final String gameName;
    private final String providerName;

    private int stars;
    private int noOfVotes;
    private String gameLogo;
    private final double minBet;
    private final double maxBet;

    private RiskLevel riskLevel; // can change by manager
    private final String hashKey;
    private final String betCategory; // "$" or "$$" or "$$$"

    public Game(String gameName,String providerName, int stars, int noOfVotes, String gameLogo,
                double minBet,double maxBet, RiskLevel riskLevel, String hashKey){
        this.gameName = gameName;
        this.providerName = providerName;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.gameLogo =gameLogo;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.riskLevel = riskLevel;
        this.hashKey = hashKey;
        this.betCategory = calculateBetCategory(minBet);
    }

    // Getters and Setters
    public String getGameName(){
        return this.gameName;
    }
    public String getProviderName(){
        return this.providerName;
    }
    public int getStars(){
        return this.stars;
    }
    public int getNoOfVotes(){
        return this.noOfVotes;
    }
    public String getGameLogo(){
        return this.gameLogo;
    }
    public double getMinBet(){
        return this.minBet;
    }
    public double getMaxBet(){
        return this.maxBet;
    }
    public RiskLevel getRiskLevel(){
        return this.riskLevel;
    }
    public String getHashKey(){
        return this.hashKey;
    }

    public void setRiskLevel(RiskLevel newRiskLevel){
        this.riskLevel = newRiskLevel;
    }
    public void increaseNoOfVotes(){
        this.noOfVotes++;
    }
    //add a method for calculating stars
    //
    //

    private static String calculateBetCategory(double minBet){
        if (minBet >=5) return "$$$";
        if(minBet >=1) return "$$";
        return "$";
    }

    public String getBetCategory(){
        return this.betCategory;
    }




}

package backend.common;

import java.math.BigDecimal;

public class Game {
    private final String gameName;
    private final String providerName;

    private double stars;
    private int noOfVotes;

    private double starsSum; //counter for totalStars

    private String gameLogo;
    private final BigDecimal minBet;
    private final BigDecimal maxBet;

    private RiskLevel riskLevel; // can change by manager
    private final String hashKey;
    private final String betCategory; // "$" or "$$" or "$$$"


    public Game(String gameName,String providerName, double stars, int noOfVotes, String gameLogo,
                BigDecimal minBet,BigDecimal maxBet, RiskLevel riskLevel, String hashKey){
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

        this.starsSum = noOfVotes * stars;
    }

    // Getters and Setters
    public String getGameName(){
        return this.gameName;
    }
    public String getProviderName(){
        return this.providerName;
    }
    public double getStars(){
        return this.stars;
    }
    public int getNoOfVotes(){
        return this.noOfVotes;
    }
    public String getGameLogo(){
        return this.gameLogo;
    }
    public BigDecimal getMinBet(){
        return this.minBet;
    }
    public BigDecimal getMaxBet(){
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


    // Helping methods for rating a game by players
    public void increaseNoOfVotes(){
        this.noOfVotes++;
    }
    public void increaseStarsSum(int stars){
        this.starsSum += stars;
    }
    public void setStars(){
        //avoid dividing by 0:
        if(this.noOfVotes<=0){
            this.noOfVotes=0;
            this.starsSum =0;
            this.stars =0;
            return;
        }
        //else

        this.stars = this.starsSum/this.noOfVotes
        ;
    }

    public void decreaseNoOfVotes(){this.noOfVotes--;}
    public void decreaseStarsSum(int stars) {
        if (this.noOfVotes >0){
            this.starsSum -= stars;
        }

    }


    private static String calculateBetCategory(BigDecimal minBet){
        if (minBet.compareTo(new BigDecimal("5"))>=0) return "$$$";
        if(minBet.compareTo(new BigDecimal("1"))>=0) return "$$";
        return "$";
    }

    public String getBetCategory(){
        return this.betCategory;
    }


}

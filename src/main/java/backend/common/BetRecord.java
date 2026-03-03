package backend.common;

public class BetRecord {

    private final String playerId;
    private final String gameName;
    private final String providerName;
    private final double bet;
    private final double payout;
  //  private final double playerDelta; //calculate it here

    //constructur
    public BetRecord(String playerId, String gameName, String providerName, double bet
                     , double payout){
        this.playerId = playerId;
        this.gameName = gameName;
        this.providerName = providerName;
        this.bet = bet;
        this.payout = payout;

    }

    //getters
    public String getPlayerId(){
        return this.playerId;
    }
    public String getGameName(){
        return this.gameName;
    }
    public String getProviderName(){
        return this.providerName;
    }
    public double getBet(){
        return this.bet;
    }
    public double getPayout(){
        return this.payout;
    }
    public double getPlayerDelta(){
        return this.payout - this.bet;
    }
    //maybe add getSystemDelta

}

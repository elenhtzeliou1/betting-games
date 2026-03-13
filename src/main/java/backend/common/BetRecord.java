package backend.common;

import java.math.BigDecimal;

public class BetRecord {

    private final String playerId;
    private final String gameName;
    private final BigDecimal bet;
    private final BigDecimal payout;
    private final int randomNumber;


    public BetRecord(String playerId, String gameName ,BigDecimal bet
                     , BigDecimal payout, int randomNumber){
        this.playerId = playerId;
        this.gameName = gameName;
        this.bet = bet;
        this.payout = payout;
        this.randomNumber = randomNumber;
    }

    //getters
    public String getPlayerId(){
        return this.playerId;
    }
    public String getGameName(){
        return this.gameName;
    }
    public BigDecimal getBet(){
        return this.bet;
    }
    public BigDecimal getPayout(){
        return this.payout;
    }
    public int getRandomNumber(){
        return this.randomNumber;
    }
    //maybe add getSystemDelta

}

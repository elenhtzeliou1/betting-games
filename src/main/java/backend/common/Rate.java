package backend.common;


// Player can review (rate) a game
// Rate: playerId | gameName | stars

public class Rate {
    private final String playerId;
    private final String gameName;
    private int stars;

    public Rate( String playerId,String gameName,int stars){
        this.playerId = playerId;
        this.gameName = gameName;
        this.stars = stars;
    }




    // Getters and Setters
    public String getPlayerId(){return this.playerId;}
    public String getGameName(){return this.gameName;}
    public int getStars(){return this.stars;}

    // Let user update this Rate stars
    public void setStars(int newStars){this.stars = newStars;}



}

package backend.common;

import java.util.HashMap;
import java.util.Map;

public class GameState {
    private final Game game;
    private boolean isActive = true; // true by default

    // Onereview per player for THIS game
    // Map<String = userId, Rate> ratesByPlayerId
    private final Map<String,Rate> ratesByPlayerId = new HashMap<>();

    private int noOfVotes;
    private double stars;

    // total loss or profit for this exact game
    // implemented later
    private double totalLossProfit = 0.0;

    public GameState(Game game, boolean isActive){
        if (game == null) throw new IllegalArgumentException("game == null");
        this.game= game;
        this.isActive = isActive;

        // Every stored game has by default (by the given JSON) some stars and noOfVotes
        // initialize stars and also initialize noOfVotes
        this.noOfVotes = game.getNoOfVotes();
        this.stars = game.getStars();
    }

    //getters and setters
    public Game getGame(){return this.game;}
    public synchronized boolean isActive(){
        return isActive;
    }

    // helping method for changing (flipping) current visibility of the game that user wants to change
    public synchronized boolean flipCurrentActiveState(){
        this.isActive = !this.isActive;
        return this.isActive;
    }
    public synchronized boolean addRate(String playerId, int stars){

        playerId = playerId.trim().toLowerCase(); //normalize
        areStarsValueValid(stars);

        // see if a rate by this player of THIS game already exits
        Rate existingRate = ratesByPlayerId.get(playerId);
        if(existingRate == null){
            // we good!
            // no previous rate from this player for this game exists
            Rate newRate = new Rate(playerId, game.getGameName(), stars);
            ratesByPlayerId.put(playerId,newRate);

            game.increaseNoOfVotes();
            game.increaseStarsSum(stars);
            game.setStars(); // recompute avg stars rating
            return true;
        }else{
            // rating already exists!
           return false;
        }

    }

    public synchronized boolean deleteExistingRate(String playerId){
        Rate rateForRemove = ratesByPlayerId.remove(playerId);
        if(rateForRemove == null) return false;

        game.decreaseNoOfVotes();
        game.decreaseStarsSum(rateForRemove.getStars());
        game.setStars();
        return true;
    }



    public synchronized double getTotalLossProfit(){
        return totalLossProfit;
    }
    public synchronized void addProfitLoss(double delta){
        this.totalLossProfit +=delta;
    }



    // Helping methods
    private void areStarsValueValid(int stars){
        if (stars < 1 || stars > 5) throw new IllegalArgumentException("Stars must be integer: 1<= Stars <=5");
    }
}

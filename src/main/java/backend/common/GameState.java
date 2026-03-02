package backend.common;

public class GameState {
    private final Game game;
    private boolean isActive = true; // true by default

    // total loss or profit for this exact game
    //
    private double totalLossProfit = 0.0;

    public GameState(Game game) {
        if (game == null) throw new IllegalArgumentException("game == null");
        this.game = game;
    }

    public GameState(Game game, boolean isActive){
        if (game == null) throw new IllegalArgumentException("game == null");
        this.game= game;
        this.isActive = isActive;
    }

    //getters and setters
    public Game getGame(){
        return this.game;
    }
    public synchronized boolean isActive(){
        return isActive;
    }
    /*
    public synchronized void setGameInactive(){
        this.isActive = false;
    }
    public synchronized void setGameActive(){
        this.isActive = true;
    }
    */

    // helping method for changing (flipping) current visibility of the game that user wants to change
    public synchronized boolean flipCurrentActiveState(){
        this.isActive = !this.isActive;
        return this.isActive;
    }

    public synchronized double getTotalLossProfit(){
        return totalLossProfit;
    }
    public synchronized void addProfitLoss(double delta){
        this.totalLossProfit +=delta;
    }

}

package backend.worker;

import backend.common.Game;
import backend.common.GameState;
import backend.common.RiskLevel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class WorkerServer {

    // GameName, GameState
    // Later remove //
    private static final Map<String, GameState> gamesByName = new HashMap<>();
    // List<BetRecord> betHistory = new ArrayList<>();


    public static void main(String[] args) {
        int port;
        if(args.length ==1){
            port = Integer.parseInt(args[0]);
        }else{
            port = 6001;
        }


        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Worker Listening on port: "+ port);

            do {
                Socket socket = serverSocket.accept();

                // every new connection to worker is handled in a separate thread
                // Multithreaded Worker
                // Worker can handle many requests from masterServer parallel
                new Thread(() -> handleWorker(socket, port)).start();
            } while (true);
        }catch (Exception e){
            System.out.println("Worker exception occurred: "+e.getMessage());
        }
    }
    //
    //
    // all the logic behind what the worker handles and how is inside handleWorker()
    //
    //
    public static void handleWorker(Socket socket, int port){
        try(socket;
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);){

            String inputString = input.readLine();
            if (inputString==null)return;

            inputString = inputString.trim();

            //add store
            if (inputString.startsWith("ADD_NEW_GAME ")){
                handleAddNewGame(inputString, port, output);
                return;

            }else if(inputString.startsWith("SHOW_ALL_GAMES")){
                handleShowAllGames(output);
                return;
            }else if(inputString.startsWith("UPDATE_GAME_RISK ")){
                handleUpdateGameRisk(inputString,output);
                return;
            } else if (inputString.startsWith("DELETE_EXISTING_GAME ")) {
                handleChangeGameVisibility(inputString,output);
                return;
            }
            else if (inputString.startsWith("MAP_PROVIDER_PROFIT ")){
                //handleProviderProfit(inputString, port,output);
                return;
            }else if(inputString.equalsIgnoreCase("FETCH_ALL_AVAILABLE_GAMES")){
                handleShowAllAvailableGame(output);
                return;
            } else if (inputString.startsWith("MAP_SEARCH ")) {
                handleMapSearch(inputString,port,output);
                return;

            }

            // continue with other else if
            //
            //

            output.println("ERROR unknown worker command: " + inputString);
            output.println("END");
        }catch(Exception e){
            System.out.println("Worker error appeared: " + e.getMessage());
        }
    }


    private static void handleAddNewGame(String inputString, int port, PrintWriter output) throws Exception {
        String b64 = inputString.substring("ADD_NEW_GAME ".length()).trim();
        String json = new String(java.util.Base64.getDecoder().decode(b64), java.nio.charset.StandardCharsets.UTF_8);

        Game game = WorkerCustomJSONParser.parseGameJSON(json);

        int total;
        synchronized (gamesByName) {
            String gameNameKey = game.getGameName().trim().toLowerCase();
            if (gamesByName.containsKey(gameNameKey)) {
                output.println("ERROR! This Game: " + game.getGameName() + " already exists!");
                output.println("END");
                return;
            }
            gamesByName.put(gameNameKey, new GameState(game, true));
            total = gamesByName.size();
        }

        String reply = "Worker (" + port + ") successfully stored: "
                + game.getGameName()
                + " | betCategory: " + game.getBetCategory()
                + " | total Games Stored in this Worker: " + total;

        output.println(reply);
        output.println("END");
    }

    private static void handleShowAllGames(PrintWriter output) {
        synchronized (gamesByName) {
            for (Map.Entry<String, GameState> val : gamesByName.entrySet()){
                GameState gameState = val.getValue();
                output.println("GameName: "+gameState.getGame().getGameName() +
                        " | Provider: " + gameState.getGame().getProviderName()+
                        " | BetCategory: "+ gameState.getGame().getBetCategory() +
                        " | Risk: " + gameState.getGame().getRiskLevel() +
                        " | isGameActive: "+ gameState.isActive()
                );

            }
        }
        output.println("END");
    }

    private static void handleUpdateGameRisk(String inputString, PrintWriter output) {
        String payload = inputString.substring("UPDATE_GAME_RISK ".length()).trim();
        String[] parts = payload.split("\\|");

        if(parts.length !=3){
            output.println("Error, bad format: Expected: gameName|providerName|risk(low||medium||high)");
            output.println("END");
            return;
        }
        String gameName = parts[0].toLowerCase().trim();
        String providerName = parts[1].trim();
        String riskLevelStr = parts[2].trim();

        RiskLevel newRisk;
        // Validation check for riskLevel input
        // can be removed because it is also happen to MasterServer
        try{
            newRisk= RiskLevel.parse(riskLevelStr);
        }catch (Exception e){
            output.println("ERROR invalid riskString. Allowed: low || medium || high");
            output.println("END");
            return;
        }

        GameState gameState;
        synchronized (gamesByName){
            gameState = gamesByName.get(gameName);
        }
        if (gameState == null){
            // No Game Exist with this name
            output.println("Error, no Game found with GameName: "+gameName);
            output.println("END");
            return;
        }
        synchronized (gameState){
            // verify that given provider matches stored provider
            String storedProvider = gameState.getGame().getProviderName();
            if(!storedProvider.equalsIgnoreCase(providerName)){
                // providerName mismatch
                output.println("ERROR providerName mismatch for game: " +gameName);
                output.println("Expected: "+storedProvider+", got: "+providerName);
                output.println("END");
                return;
            }
            gameState.getGame().setRiskLevel(newRisk);
        }
        output.println("RiskLevel Updated Successfully!");
        output.println("END");

    }
    
    private static void handleChangeGameVisibility(String inputString, PrintWriter output){
        String gameName = inputString.substring("DELETE_EXISTING_GAME ".length()).toLowerCase().trim();

        if(gameName.isBlank()){
            output.println("ERROR gameName is required!");
            output.println("END");
            return;
        }
        GameState gameState;
        synchronized (gamesByName){
            gameState = gamesByName.get(gameName);
        }
        if(gameState==null){
            output.println("Error, no game found with name: "+  gameName);
            output.println("END");
            return;
        }

        // method flipCurrentActiveState() is sychronized
        // no need for new sychronized () block here
        boolean newIsActive = gameState.flipCurrentActiveState();
        output.println("Visibility Changed for: "+gameName+" to: " + newIsActive);
        output.println("END");



    }

    private void handleProviderProfit(String jobId, String provider, int expectedN,
                                             String reducerHost, int reducerPort) throws Exception{
        try (Socket s = new Socket(reducerHost, reducerPort);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {

            out.println("MAP_PROVIDER_PROFIT " + jobId + " " + provider + " " + expectedN);

            synchronized (gamesByName) {
                for (GameState gs : gamesByName.values()) {
                    if (gs.getGame().getProviderName().equalsIgnoreCase(provider)) {
                        String gameName = gs.getGame().getGameName();
                        double profit = gs.getTotalLossProfit();
                        out.println(gameName + "\t" + profit);
                    }
                }
            }

            out.println("END");
        }

    }


    /*
    private static void handleShowAllAvailableGame(PrintWriter output){
        // Connect with Reducer to push him the request
        // Every worker send's to Reducer it's Map with available Games

        try(Socket s = new Socket(reducerHost,reducerPort);
        )

        synchronized (gamesByName){
            for (Map.Entry<String, GameState> val : gamesByName.entrySet()){

                if(val.getValue().isActive()){
                    // Fetch Only Active Games
                    GameState gameState = val.getValue();
                    output.println("GameName: "+gameState.getGame().getGameName() +
                            " | Provider: " + gameState.getGame().getProviderName()+
                            " | BetCategory: "+ gameState.getGame().getBetCategory() +
                            " | Risk: " + gameState.getGame().getRiskLevel() +
                            " | isGameActive: "+ gameState.isActive()
                    );

                }

            }
        }
        output.println("END");
    } */

    private static void handleMapSearch(String inputString, int port, PrintWriter output){
        String payload = inputString.substring("MAP_SEARCH ".length()).trim();
        String[] parts = payload.split("\\|");
        if(parts.length!=7){
            output.println("ERROR bad format. Expected jobId|minStars|betCategory|risk|reducerHost|reducerPost|expectedN");
            output.println("END");
            return;
        }
        String jobId = parts[0].trim();
        int minStars = Integer.parseInt(parts[1].trim());
        String betCategory = parts[2].trim();
        String risk = parts[3].trim();
        String reducerHost = parts[4].trim();
        int reducerPort = Integer.parseInt(parts[5].trim());
        int expectedN = Integer.parseInt(parts[6].trim());

        // CONNECT TO REDUCER
        // send map output
        try(Socket s = new Socket(reducerHost,reducerPort);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter writer = new PrintWriter(s.getOutputStream(),true))
        {
            // 1. First Line tells Reducer:
            // - which job this belongs to && how many total Workers are expected to contribute
            // Reducer Command format: MAP_SEARCH <jobId> <expectedN>
            writer.println("MAP_SEARCH "+ jobId+ " "+ expectedN);

            // Lock gamesByName while the itteration happens to avoid concurrent modification issues
            // (Workers are multithreaded; another request could add/delete/update game visibility.)

            synchronized (gamesByName){
                for(GameState gameState : gamesByName.values()){
                    // Only games that are visible to player
                    // Skip non visible games (to player)
                    if(!gameState.isActive()) continue;
                    var game = gameState.getGame();

                    // --- Filters:
                    // 1) Filter: minStars (if minStars ===0 : skip this)
                    if(minStars>0 && game.getStars()<minStars) continue;

                    // 2) Filter: by bet category (if betCategory ==ANY : accept thema all)
                    if (!"ANY".equalsIgnoreCase(betCategory) && !game.getBetCategory().equals(betCategory)) continue;

                    // 3) Filter: Risk level (if risk == ANY: accept all)
                    String gameRisk = game.getRiskLevel().name().toLowerCase();
                    if (!"ANY".equalsIgnoreCase(risk) && !gameRisk.equalsIgnoreCase(risk)) continue;

                    // If it passed all filters, emit one intermediate record
                    // We choose key2 implicitly as gameName (Reducer can deduplicate by gameName).
                    // Value2 is the rest of the game info needed by the UI.
                    //
                    // Wire format (one line per game):
                    //   GAME|gameName|provider|stars|betCategory|risk|minBet|maxBet
                    writer.println(
                            "GAME|" + game.getGameName() + "|" + game.getProviderName() + "|" + game.getStars() + "|" +
                                    game.getBetCategory() + "|" + gameRisk + "|" + game.getMinBet() + "|" + game.getMaxBet()
                    );
                }
            }
            // Signal the end-of-list fot this Worker's partial results
            writer.println("END");



            // optional use in to read reducer ack so we know the reducer accepted our submission
            String ack = reader.readLine(); // should be "ACK"
            System.out.println("[Worker " + port + "] Reducer replied: " + ack);

            // FINAL STEP
            // Reply back to Master that this Worker finished its MAP_SEARCH work
            output.println("OK Worker ("+port+") MAP_SEARCH sent to REDUCER");
            output.println("END");

        }catch (Exception e){
            output.println("ERROR: MAP_SEARCH failed: "+e.getMessage());
            output.println("END");
        }
    }
}

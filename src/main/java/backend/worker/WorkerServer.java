package backend.worker;

import backend.common.Game;
import backend.common.GameState;
import backend.common.RiskLevel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
            }
            // continue with other else if
            //
            //
            //
            //
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
}

package backend.worker;

import backend.common.BetRecord;
import backend.common.Game;
import backend.common.GameState;
import backend.common.RiskLevel;
import backend.secureRandomGenerator.HashHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkerServer {

    // MAP -> <GameName, GameState>
    private static final Map<String, GameState> gamesByName = new HashMap<>();

    // SecureRandomNumberGenerator Info
    private static String srngHost;
    private static int srngPort;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java backend.worker.WorkerServer <workerPort> <srngHost> <srngPort>");
            System.out.println("Example: java backend.worker.WorkerServer 6001 192.168.1.20 8000");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(args[0].trim());
            srngHost = args[1].trim();
            srngPort = Integer.parseInt(args[2].trim());
        } catch (Exception e) {
            System.out.println("Worker invalid startup arguments: " + e.getMessage());
            return;
        }
        System.out.println("Worker configured SRNG at: " + srngHost + ":" + srngPort);


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
            }else if(inputString.startsWith("MODIFY_GAME ")){
                handleModifyGame(inputString,output);
                return;
            } else if (inputString.startsWith("DELETE_EXISTING_GAME ")) {
                handleSetGameVisibilityInactive(inputString,output);
                return;
            } else if (inputString.startsWith("MAKE_VISIBLE ")) {
                handleSetGameVisibilityActive(inputString,output);
                return;
            } else if (inputString.startsWith("MAP_PROVIDER_PROFIT ")){
                handleProviderProfit(inputString, port,output);
                return;
            } else if (inputString.startsWith("MAP_PLAYER_PROFIT ")) {
                handlePlayerProfit(inputString,port,output);
                return;
            } else if (inputString.startsWith("MAP_SEARCH ")) {
                handleMapSearch(inputString,port,output);
                return;
            }else if(inputString.startsWith("SHOW_GAME_PROFIT_LOSS ")){
                handleFindSpecificGameProfitLoss(inputString,output);
                return;
            }
            else if(inputString.startsWith("RATE ")){
                handleGameRate(inputString,output);
                return;
            }else if(inputString.startsWith("PLAY ")){
                handlePlayRequest(inputString,output);
                return;
            }

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
        String gameNameKey = game.getGameName().trim().toLowerCase();

        // Use 2 sychronized blocks for holding the lock for less seconds

        // 1. First Sychronized Block, Check if game exists
        synchronized (gamesByName) {
            if (gamesByName.containsKey(gameNameKey)) {
                output.println("ERROR! This Game: " + game.getGameName() + " already exists!");
                output.println("END");
                return;
            }

        }
        // 2. Register the New Game to SRNG
        try{
            // CHANGE BUFFER SIZE
            registerNewGameToSRNG(game.getGameName(), game.getHashKey(), 10);
        }catch (Exception e){
            output.println("ERROR Failed to Register the game to SRNG: "+e.getMessage());
            output.println("END");
            return;
        }

        // 3. If register to SRNG complete, add it to gamesByName
        int total; // Just for our debug
        synchronized (gamesByName){
            if(gamesByName.containsKey(gameNameKey)){
                // If some other thread already added it
                // Delete it from SRNG
                try{
                    deleteGameFromSRNG(game.getGameName());
                }catch (Exception ignore){
                }
                output.println("Error, This Game: "+game.getGameName()+" already exists!");
                output.println("END");
                return;
            }
            // else add it
            gamesByName.put(gameNameKey, new GameState(game, true));
            total = gamesByName.size();
        }

        output.println("STORED"); // Message for MasterServerOnly

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
                BigDecimal jackpot = getJackpotForSpecificRiskLevel(gameState.getGame().getRiskLevel());
                Game game = gameState.getGame();
                output.println("GameName: " + game.getGameName()
                        + " | Provider: " + game.getProviderName()
                        + " | MinBet: " + game.getMinBet()
                        + " | MaxBet: " + game.getMaxBet()
                        + " | BetCategory: " + game.getBetCategory()
                        + " | Risk: " + game.getRiskLevel()
                        + " | Jackpot: " + jackpot
                        + " | isGameActive: " + gameState.isActive()
                );
            }
        }
        output.println("END");
    }

    private static void handleModifyGame(String inputString, PrintWriter output) {
        String payload = inputString.substring("MODIFY_GAME ".length()).trim();
        String[] parts = payload.split("\\|",-1);

        if (parts.length != 5) {
            output.println("ERROR bad format. Expected: gameName|providerName|riskOrKEEP|minBetOrKEEP|maxBetOrKEEP");
            output.println("END");
            return;
        }
        String gameName = parts[0].trim().toLowerCase();
        String providerName = parts[1].trim();
        String riskLevelStr = parts[2].trim();
        String minBetStr = parts[3].trim();
        String maxBetStr = parts[4].trim();

        GameState gameState;
        synchronized (gamesByName) {
            gameState = gamesByName.get(gameName);
        }
        if (gameState == null) {
            output.println("ERROR no game found with GameName: " + gameName);
            output.println("END");
            return;
        }

        synchronized (gameState) {
            Game game = gameState.getGame();

            String storedProvider = game.getProviderName();
            if (!storedProvider.equalsIgnoreCase(providerName)) {
                output.println("ERROR providerName mismatch for game: " + gameName);
                output.println("Expected: " + storedProvider + ", got: " + providerName);
                output.println("END");
                return;
            }

            RiskLevel finalRisk = game.getRiskLevel();
            BigDecimal finalMinBet = game.getMinBet();
            BigDecimal finalMaxBet = game.getMaxBet();

            if (isKeepValue(riskLevelStr)) {
                try {
                    finalRisk = RiskLevel.parse(riskLevelStr);
                } catch (Exception e) {
                    output.println("ERROR invalid riskString. Allowed: low | medium | high | KEEP");
                    output.println("END");
                    return;
                }
            }

            if (isKeepValue(minBetStr)) {
                try {
                    finalMinBet = new BigDecimal(minBetStr);
                } catch (Exception e) {
                    output.println("ERROR minBet must be a valid decimal number or KEEP");
                    output.println("END");
                    return;
                }

                if (finalMinBet.compareTo(BigDecimal.ZERO) <= 0) {
                    output.println("ERROR minBet must be > 0");
                    output.println("END");
                    return;
                }
            }

            if (isKeepValue(maxBetStr)) {
                try {
                    finalMaxBet = new BigDecimal(maxBetStr);
                } catch (Exception e) {
                    output.println("ERROR maxBet must be a valid decimal number or KEEP");
                    output.println("END");
                    return;
                }

                if (finalMaxBet.compareTo(BigDecimal.ZERO) <= 0) {
                    output.println("ERROR maxBet must be > 0");
                    output.println("END");
                    return;
                }
            }

            if (finalMaxBet.compareTo(finalMinBet) < 0) {
                output.println("ERROR maxBet must be >= minBet");
                output.println("END");
                return;
            }

            game.setRiskLevel(finalRisk);
            game.updateBetLimits(finalMinBet, finalMaxBet);

            output.println("OK Game modified successfully");
            output.println("GameName: " + game.getGameName());
            output.println("Provider: " + game.getProviderName());
            output.println("Risk: " + game.getRiskLevel());
            output.println("MinBet: " + game.getMinBet());
            output.println("MaxBet: " + game.getMaxBet());
            output.println("BetCategory: " + game.getBetCategory());
            output.println("END");
        }

    }
    
    private static void handleSetGameVisibilityInactive (String inputString, PrintWriter output){
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

        // delete (set visibility inactive) the method (setVisibilityInactive()) is sycronized in GameState.java
        gameState.setVisibilityInactive();

        // Stop the SRNG
        try{
            deleteGameFromSRNG(gameState.getGame().getGameName());
        }catch (Exception e){
            output.println("Warning! Game set inactive but failed to stop SRNG");
            output.println("END");
            return;
        }

        output.println("Visibility Changed for: "+gameName+" to: " + gameState.isActive());
        output.println("END");

    }

    private static void handleSetGameVisibilityActive(String inputString, PrintWriter output){
        String gameName = inputString.substring("MAKE_VISIBLE ".length()).trim().toLowerCase();
        if(gameName.isBlank()){
            output.println("Error, GameName is empty!");
            output.println("END");
            return;
        }
        GameState gameState;
        synchronized (gamesByName){
            gameState = gamesByName.get(gameName);
        }
        if(gameState ==null){
            output.println("Error, no Game found with name: "+ gameName);
            output.println("END");
            return;
        }
        String realGameName;
        String secret;
        synchronized (gameState){
            if(gameState.isActive()){
                output.println("Game: "+gameName+" is already Visible!");
                output.println("END");
                return;
            }
            realGameName = gameState.getGame().getGameName();
            secret = gameState.getGame().getHashKey();
        }
        // Start SRNG first
        try{
            // CHANGE BUFFER SIZE
            registerNewGameToSRNG(gameState.getGame().getGameName(), gameState.getGame().getHashKey(), 10);
        }catch (Exception e){
            output.println("ERROR Failed to Register the game to SRNG: "+e.getMessage());
            output.println("END");
            return;
        }

        gameState.setVisibilityActive(); // setVisibilityActive is synchronized

        output.println("Game: "+gameName+" is now visivle to players! ");
        output.println("END");
        return;
    }

    private static void handleProviderProfit(String inputString, int port, PrintWriter output) throws Exception{

        //+jobId +"|" + providerName +"|" +reducerHost +"|"+reducerPost+"|"+ expectedWorkers
        String payload = inputString.substring("MAP_PROVIDER_PROFIT ".length()).trim();
        String[] parts = payload.split("\\|");

        if(parts.length!=5){
            output.println("ERROR bad format. Expected jobId|providerName|reducerHost|reducerPost|expectedN");
            output.println("END");
            return;
        }
        String jobId = parts[0].trim();
        String providerName = parts[1].trim();
        String reducerHost = parts[2].trim();
        int reducerPort = Integer.parseInt(parts[3].trim());
        int expectedN = Integer.parseInt(parts[4].trim());

        // Connect to reducer
        try (Socket s = new Socket(reducerHost, reducerPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter writer = new PrintWriter(s.getOutputStream(), true)) {

            writer.println("MAP_PROVIDER_PROFIT " + jobId + " " + providerName + " " + expectedN);

            synchronized (gamesByName) {
                for (GameState gamestate : gamesByName.values()) {
                    if (gamestate.getGame().getProviderName().equalsIgnoreCase(providerName)) {
                        String gameName =gamestate.getGame().getGameName();
                        BigDecimal profitLoss = gamestate.getTotalLossProfit();
                        writer.println(gameName + "|" + profitLoss);
                    }
                }
            }

            writer.println("END");

            String ack = reader.readLine();
            System.out.println("[Worker "+port+"] Reducer replied: "+ack);

            output.println("OK Worker ("+port+") MAP_PROVIDER_PROFIT sent to REDUCER");
            output.println("END");

        }catch (Exception e){
            output.println("ERROR: MAP_PROVIDER_PROFIT failed: "+e.getMessage());
            output.println("END");
        }

    }

    private static void handlePlayerProfit(String inputString, int port, PrintWriter output){
        //+userId +"|" + userId +"|" +reducerHost +"|"+reducerPost+"|"+ expectedWorkers

        String payload = inputString.substring("MAP_PLAYER_PROFIT ".length()).trim();
        String[] parts = payload.split("\\|");

        if(parts.length != 5){
            output.println("ERROR bad format. Expected jobId|userId|reducerHost|reducerPost|expectedN");
            output.println("END");
            return;
        }
        String jobId = parts[0].trim();
        String userId = parts[1].trim();
        String reducerHost = parts[2].trim();
        int reducerPort = Integer.parseInt(parts[3].trim());
        int expectedN = Integer.parseInt(parts[4].trim());

        // Connect to reducer
        try(Socket s = new Socket(reducerHost, reducerPort);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter writer = new PrintWriter(s.getOutputStream(), true))
        {
            writer.println("MAP_PLAYER_PROFIT "+ jobId +" "+userId + " "+ expectedN);

            List<GameState> snapshot;
            synchronized (gamesByName) {
                snapshot = new ArrayList<>(gamesByName.values());
            }

            for(GameState gameState : snapshot){

                for(BetRecord br : gameState.getBetHistorySnapshot()){
                    if(br.getPlayerId().equalsIgnoreCase(userId)){
                        BigDecimal playerDelta = br.getPayout().subtract(br.getBet());
                        writer.println(playerDelta);
                    }
                }

            }


            writer.println("END");

            String ack = reader.readLine();
            System.out.println("[Worker "+port+"] Reducer replied: "+ack);

            output.println("OK Worker ("+port+") MAP_PLAYER_PROFIT sent to REDUCER");
            output.println("END");

        }catch (Exception e){
            output.println("ERROR: MAP_PLAYER_PROFIT failed: "+e.getMessage());
            output.println("END");
        }
    }

    private static void handleFindSpecificGameProfitLoss(String inputString, PrintWriter output){
        String gameName  = inputString.substring("SHOW_GAME_PROFIT_LOSS ".length()).trim();
        if(gameName.isBlank()){
            output.println("Error, gameName is empty! ");
            output.println("END");
            return;
        }

        GameState gameState;
        synchronized (gamesByName){
            gameState = gamesByName.get(gameName);
        }

        if(gameState==null){
            output.println("ERROR! No game found with gameName: "+ gameName);
            output.println("END");
            return;
        }
        BigDecimal totalProfitLoss = gameState.getTotalLossProfit();

        output.println("GAME_PROFIT_LOSS|"+gameState.getGame().getGameName()+"|"
        +gameState.getGame().getProviderName()+"|"+
                totalProfitLoss);

        output.println("END");
    }

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
                    BigDecimal jackpot = getJackpotForSpecificRiskLevel(game.getRiskLevel());
                    writer.println(
                            "GAME|" + game.getGameName() + "|"
                                    + game.getProviderName() + "|"
                                    + game.getStars() + "|"
                                    + game.getNoOfVotes() +"|"
                                    + game.getBetCategory() + "|"
                                    + gameRisk + "|"
                                    + game.getMinBet() + "|"
                                    + game.getMaxBet() + "|"
                                    + jackpot
                    );
                }
            }
            // Signal the end-of-list fot this Worker's partial results
            writer.println("END");



            // use in to read reducer ack so we know the reducer accepted our submission
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

    private static void handleGameRate(String inputString, PrintWriter output){
        String payload = inputString.substring("RATE ".length()).trim();
        String[] parts = payload.split("\\|");

        if (parts.length !=3){
            output.println("ERROR bad RATE format. Expected: playerId|gameName|stars");
            output.println("END");
            return;
        }
        String playerId = parts[0].trim();
        String gameName = parts[1].trim().toLowerCase();
        int stars;
        try {
            stars = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            output.println("ERROR stars must be an integer 1-5");
            output.println("END");
            return;
        }


        GameState gameState;
        synchronized (gamesByName){
            gameState = gamesByName.get(gameName);
        }

        if(gameState == null){
            // No game with this name Exists
            output.println("Error, no Game found with GameName: "+gameName);
            output.println("END");
            return;
        }
        // allow review from player only if game is visible to player
        if(!gameState.isActive()){
            output.println("ERROR Game is not available for rating right now!");
            output.println("END");
            return;
        }

        try{
            boolean rateSaved = gameState.addRate(playerId,stars); // syncronized inside GameState
            if (rateSaved){
                output.println("Your rate is saved! ");
            }else{
                output.println("ERROR you already rated this game (delete or update your rate)");
            }
        }catch(IllegalArgumentException e){
            output.println("ERROR "+e.getMessage());
        }
        output.println("END");

    }


    private static void handlePlayRequest(String inputString, PrintWriter output){
        String payload = inputString.substring("PLAY ".length()).trim();
        String[] parts = payload.split("\\|");

        if(parts.length !=3){
            output.println("ERROR, bad PLAY format. Expected: playerId|gameName|bet");
            output.println("END");
            return;
        }
        String playerId = parts[0].trim();
        String gameName = parts[1].trim().toLowerCase();
        BigDecimal requestedBet;

        try{
            requestedBet = new BigDecimal(parts[2].trim());
        }catch (NumberFormatException e){
            output.println("ERROR, Bet must be a valid decimal Number: "+e.getMessage());
            output.println("END");
            return;
        }
        if (requestedBet.compareTo(BigDecimal.ZERO) <= 0) {
            output.println("ERROR bet must be > 0");
            output.println("END");
            return;
        }

        GameState gameState;
        synchronized (gamesByName){
            gameState = gamesByName.get(gameName);
        }

        if(gameState==null){
            // No game with this gameName exists!
            output.println("Error, No Game found with gameName: "+gameName);
            output.println("END");
            return;
        }
        BigDecimal minBet;
        BigDecimal maxBet;
        String gameSecret;
        String riskLevel;

        // Get a consistent snapshot of gameData
        synchronized (gameState){
            // allow play only if game is visible to player
            if(!gameState.isActive()){
                output.println("Error, This game is not available for playing!");
                output.println("END");
                return;
            }
            minBet = gameState.getGame().getMinBet();
            maxBet = gameState.getGame().getMaxBet();
            gameSecret = gameState.getGame().getHashKey();
            riskLevel = gameState.getGame().getRiskLevel().name().toLowerCase();
        }

        // Check if the bet that User Requested is Valid
        if (requestedBet.compareTo(minBet)<0 || requestedBet.compareTo(maxBet)>0){
            output.println("Error, Invalid Bet!");
            output.println("Bet should be: "+minBet+" <= bet <= "+ maxBet);
            output.println("END");
            return;
        }

        // Call the RandomNumberGenerator
        // Request: GET|gameId

        // 1. worker gets number
        // 2. Worker computes hash
        // 3. Verify it locally
        // 4. Compute player's payout
        // 5. Add Bet Record

        try{
            // 1. Call the RandomNumberGenerator AND get the number
            SRNGReply srngReply = getNumber(gameName);

            // 2. Verify the hash locally
            String replyNumberStr = Integer.toString(srngReply.getNumber());
            String localHash = HashHelper.sha256(replyNumberStr+ gameSecret);

            if(!localHash.equals(srngReply.getHash())){
                output.println("Error, Hash varification failed!");
                output.println("END");
                return;
            }

            // 3. Calculate payout
            BigDecimal payout = PayoutCalculator.calculatePayout(riskLevel,requestedBet,srngReply.getNumber() );

            // House profit/loss from this bet (Check again)
            BigDecimal houseDelta = requestedBet.subtract(payout);

            // 4. Update GameState (add the new betRecord to game bet record history)
            synchronized (gameState){
                gameState.addProfitLoss(houseDelta);

                BetRecord betRecord = new BetRecord(playerId,gameName,requestedBet,payout, srngReply.getNumber());
                gameState.addBetRecord( betRecord);
            }

            // 5. Send Result to MasterServer
            output.println("PLAY_RESULT| player= "+ playerId
                    +"|game="+gameName
                    + "|random=" + srngReply.getNumber()
                    + "|payout=" + payout
                    + "|houseDelta=" + houseDelta
            );
            output.println("END");

        }catch (Exception e){
            output.println("ERROR PLAY request failed: "+e.getMessage());
            output.println("END");
        }
    }

    // Helping method for Registering New Game to SRNG
    // Used when manager adds a new game to worker
    // Used in handleAddNewGame()
    private static void registerNewGameToSRNG(String gameName, String secret, int bufferSize) throws Exception{
        try(Socket socket = new Socket(srngHost,srngPort);
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(),true)
        ){
            output.println("REGISTER "+gameName+"|"+secret+"|"+bufferSize);

            String SRNGResponse = input.readLine();
            if(SRNGResponse == null || !SRNGResponse.equals("COMPLETE")){
                throw new RuntimeException("SNRG Game registration failse: "+ SRNGResponse);
            }
        }
    }

    // Helpring method for Deleting (Hide) this game from SRNG
    // Used in handleAddNewGame() when some other thread added first from another thread the game
    // && Used in setGameVisibilityInactive()
    private static void  deleteGameFromSRNG(String gameName) throws Exception{
        try(Socket socket = new Socket(srngHost,srngPort);
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(),true)
        ){
            // Send the request to SRNG
            output.println("DELETE "+ gameName);

            String srngResponse = input.readLine();
            if(srngResponse==null || !srngResponse.equals("COMPLETE")){
                throw new RuntimeException("SRNG game stop failed: "+ srngResponse);
            }
        }
    }


    // Get Random Number from SecureRandomNumberGenerator
    // Send the request receive the number
    // This Method:
    //  - Opens a socket to SRNG
    //  - Sends to it GET gameName
    //  - Reads the response
    //  - Parses the response from SRNG
    private static SRNGReply getNumber(String gameName) throws Exception{

        try( Socket socket = new Socket(srngHost,srngPort);
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true)
        ){
            // Send the request to SRNG
            output.println("GET "+ gameName);

            // Read the SRNG Reply
            String reply = input.readLine();
            if(reply == null){
                throw new RuntimeException("SRNG return null response! Check for errors!");
            }
            if(reply.startsWith("Error") || reply.startsWith("ERROR")){
                throw new RuntimeException(reply);
            }

            String[] parts =reply.split("\\|");
            if(parts.length !=3 || !parts[0].equalsIgnoreCase("NUMBER")){
                throw new RuntimeException(reply);
            }
            int number = Integer.parseInt(parts[1].trim());
            String hash = parts[2].trim();

            SRNGReply srngReply = new SRNGReply(number, hash);

            return srngReply;

        }

    }

    private static BigDecimal getJackpotForSpecificRiskLevel(RiskLevel riskLevel){
        return switch (riskLevel) {
            case LOW -> BigDecimal.valueOf(10);
            case MEDIUM -> BigDecimal.valueOf(20);
            case HIGH -> BigDecimal.valueOf(40);
            default -> throw new IllegalArgumentException("Unknown risk level: " + riskLevel);
        };
    }

    private static String deriveBetCategory(BigDecimal minBet) {
        if (minBet.compareTo(BigDecimal.valueOf(5)) >= 0) {
            return "$$$";
        }
        if (minBet.compareTo(BigDecimal.ONE) >= 0) {
            return "$$";
        }
        return "$";
    }

    private static boolean isKeepValue(String value) {
        return value != null && !value.isBlank() && !value.equalsIgnoreCase("KEEP");
    }
}

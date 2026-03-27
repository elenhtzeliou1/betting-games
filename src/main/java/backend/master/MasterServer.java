package backend.master;

import backend.common.GameProvider;
import backend.common.PlayerBalance;
import backend.common.RiskLevel;
import backend.worker.Worker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class MasterServer {
    // MasterServer needs to keep his worker
    // and also needs to keep player balance's
    // Also needs to hold the reducer

    private static final List<Worker> workers = new ArrayList<>();
    private static final Map<String, PlayerBalance> playerBalances = new HashMap<>();

    // Reducer reducer;
    //

    // Set of providers that have Games stored in workers (Set avoids duplicates)
    // Update the list of providers after successfull storing a game to Worker (Check if already exists)
    private static final Set<GameProvider> providers = new HashSet<>();

    //-------------------- Reducer Info----------------//
    private static String reducerHost = "localhost";
    private static int reducerPort = 7000;
    //-------------------------------------------------//

    private static final Map<String,String> pendingReduceResults = new HashMap<>();
    private static final Object reduceLock = new Object();

    // callback port (masterPort + 1)
    private static int callbackPort = 5001; // set in main after parsing masterPort

    public static void main(String[] args) {
        //Helping Message
        if (args.length < 2) {
            System.out.println("Usage: java MasterServer <masterPort> <WorkerHost:port> [workerHost:port] ... ");
            System.out.println("Example: java MasterServer 5000 localhost:6001 localhost:6003");
            return;
        }

        int masterPort = Integer.parseInt(args[0]);

        for(int i =1; i<args.length; i++){
            String[] hostAndPort = args[i].split(":");
            String host = hostAndPort[0];
            int port = Integer.parseInt(hostAndPort[1]);
            workers.add(new Worker(host,port));
        }
        // Callback Port for reducer
        callbackPort = masterPort+1;
        new Thread(()-> startReducerCallbackServer(callbackPort)).start();
        System.out.println("[MasterServer] Reducer callback listening on port: "+callbackPort);

        // Worker List:
        System.out.println("[MasterServer] Workers: "+ workers);


        try(ServerSocket serverSocket = new ServerSocket(masterPort)){
            System.out.println("[MasterServer] Listening on port: "+ masterPort);

            while(true){
                // Connect with multiple clients (Managers & Players)
                // MultiThreaded MasterServer
                Socket socket = serverSocket.accept();
                System.out.println("[MasterServer] accepted client connection from: "
                        + socket.getInetAddress().getHostAddress() +":"+socket.getPort()
                );

                // drop client if no communication after 15min
                socket.setSoTimeout(900_000);
                new Thread(() -> handleClientRequest(socket)).start();
            }

        }catch (Exception e){
            System.out.println("MasterServer exception "+ e.getMessage());
        }

    }

    private static void handleClientRequest(Socket socket){
        try(socket;
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);) {

            //print hello msg to greet the connected client
            output.println("Welcome to BettingApp!");

            //Get the client role
            String role = input.readLine();
            if(role ==null){
                return;
            }

            role = role.trim().toUpperCase();
            //debug the role to client
            output.println("Client role is ok: "+ role);
            // remove the upper line later
            //
            //
            while(true){
                String inputString = input.readLine();
                if(inputString == null) break;

                inputString = inputString.trim();
                System.out.println("[MasterServer] Got new request data: "+ inputString);

                if(inputString.equalsIgnoreCase("exit")){
                    output.println("Bye bye client!");
                    output.println("END");
                    break;
                }

                // handle based on the role
                if(role.equals("MANAGER")){
                    handleManagerLogic(inputString, output);
                }else if(role.equals("PLAYER")){
                    handlePlayerLogic(inputString,output);
                }else{
                    output.println("ERROR Unkown role");
                    output.println("END");
                }
            }

        }catch(Exception e){
            System.out.println("[MasterServer] Client socket shutdown here "+ e.getMessage());
        }
    }


    // Choose which worker will "own" this game using hash-based routing.
    // 1) Normalize the game name (trim spaces + lowercase) so " Poker " and "POKER" map to the same worker.
    // 2) Compute a deterministic hash of the normalized name (same input -> same hash).
    // 3) Convert the hash to a valid worker index in [0 .. workers.size()-1] using floorMod
    //    (floorMod is used instead of % to avoid negative indexes when hashCode() is negative).
    // Result: the same gameName is always routed to the same worker as long as the worker list/order doesn't change.
    private static Worker chooseWorker(String gameName){
        String key =gameName.trim().toLowerCase(); //text normalization
        int idx = Math.floorMod(key.hashCode(), workers.size()); // stable index 0..N-1 (handles negative hashes)
        return workers.get(idx);   // worker responsible for this game
    }

    private static void handleManagerLogic(String inputString, PrintWriter output){

        if(inputString.startsWith("ADD_NEW_GAME ")){
            handleAddNewGameRequest(inputString,output);
            return;
        }
        else if(inputString.startsWith("SHOW_ALL_GAMES")){
            // Simple Gather all the stored Games that the workers have in their memory
            // Use Reducer (To avoid duplicates if we implement active replication)
            handleShowAllGamesRequest(inputString, output);
            return;
        } else if (inputString.startsWith("MODIFY_GAME ")) {
            handleModifyGameRequest(inputString,output);
            return;
            
        }else if(inputString.startsWith("DELETE_EXISTING_GAME ")){
            handleDeleteExistingGameRequest(inputString,output);
            return;

        }else if(inputString.startsWith("FIND_PROVIDER_PROFIT_LOSS ")){
            handleFindSpecifProviderProfitLossRequest(inputString,output);
            return;
        }
        else if(inputString.startsWith("FIND_PLAYER_PROFIT_LOSS ")){
            handleFindSpecificPlayerProfitLossRequest(inputString,output);
            return;
        }
        else if (inputString.startsWith("SHOW_GAME_PROFIT_LOSS ")){
            handleFindSpecificGameProfitLossRequest(inputString,output);
            return;
        }
        else if (inputString.startsWith("MAKE_VISIBLE ")) {
            handleMakeGameVisibleAgain(inputString,output);
            return;
        }


        output.println("ERROR unknown manager command");
        output.println("END");

    }



    private static void handlePlayerLogic(String inputString,PrintWriter output){
        if (inputString.equalsIgnoreCase("FETCH_ALL_AVAILABLE_GAMES")){
            String workerResponseStr = fetchAllAvailableGames();

            // Send reply back to player
            for(String ln: workerResponseStr.split("\n")){
                if(!ln.isBlank()) output.println(ln);
            }
            output.println("END");
            return;
        }
        else if(inputString.startsWith("SEARCH ")){
            handlePlayerSearch(inputString,output);
            return;
        }
        else if(inputString.startsWith("PLAY ")){
            handlePlayRequest(inputString,output);
            return;
        }else if (inputString.startsWith("RATE ")){
            handlePlayerRate(inputString,output);
            return;
        }
        else if(inputString.startsWith("ADD_BALANCE ")){
            handlePlayerAddBalanceRequest(inputString,output);
            return;
        } else if (inputString.startsWith("VIEW_BALANCE ")) {
            handlePlayerViewBalanceRequest(inputString,output);
            return;
        }
        /*
        else if (inputString.startsWith("CAN_USER_PLAY ")) {
            handleCanUserPlayRequest(inputString,output);
            return;
        }*/
        output.println("ERROR unknown player command");
        output.println("END");
    }


    // Handle Manager Requests (Helping Methods)
    private static void handleAddNewGameRequest(String inputString, PrintWriter output){
        String b64 = inputString.substring("ADD_NEW_GAME ".length()).trim();

        //decode JSON to extract gameName for routing
        String json = new String(java.util.Base64.getDecoder().decode(b64), java.nio.charset.StandardCharsets.UTF_8);

        String gameName;
        String providerName;
        try{
            JSONParser parser = new JSONParser();
            JSONObject obj = (JSONObject) parser.parse(json);
            gameName = (String) obj.get("GameName");
            providerName = (String) obj.get("ProviderName");
        }catch (Exception e){
            output.println("[MasterServer] Error: Invalid JSON: "+e.getMessage());
            output.println("END");
            return;
        }

        // Debug
        if (gameName == null || gameName.isBlank()){
            output.println("[MasterServer] Error: GameName not found in given JSON! ");
            output.println("END");
            return;
        }
        if(providerName == null || providerName.isBlank()){
            output.println("[MasterServer] Error: ProviderName not found in given JSON! ");
            output.println("END");
            return;
        }
        System.out.println("[MasterServer] Given GameName: " + gameName);

        // Choose worker
        Worker worker = chooseWorker(gameName);

        //Debug: Print the chosen worker
        System.out.println("[MasterServer] Chosen worker: "+ worker.getPort());

        //Debug: Print the JSON
        System.out.println("[MasterServer] Full Given JSON: "+json);

        //Forward the request to the choosen worker:
        String workerResponse = forwardMsgToWorker(worker, "ADD_NEW_GAME "+ b64);

        //show to which worker the request was routed
        output.println("Master server routed Manager Request to worker: "+ worker.getPort());

        // Check if worker send "STORED" (it's the first line, meaning success)
        String firstLine = workerResponse.split("\\R",2)[0];
        firstLine = firstLine.trim();

        GameProvider provider = new GameProvider(providerName);
        if(firstLine.equalsIgnoreCase("STORED")){

            // Update providers list (Set will store it if it doesnt exists)
            synchronized (providers){
                providers.add(provider);
            }
        }
        //Give all the worker response
        for(String ln : workerResponse.split("\n")){
            if(!ln.isBlank()) output.println(ln);
        }

        output.println("END");
    }

    private static void handleModifyGameRequest(String inputString, PrintWriter output){
        String payload = inputString.substring("MODIFY_GAME ".length()).trim();

        String[] parts = payload.split("\\|");
        if(parts.length !=5){
            output.println("Error, bad format: Expected: gameName|providerName|risk(low||medium||high)");
            output.println("END");
            return;
        }
        String gameName = parts[0].trim();
        String providerName = parts[1].trim();
        String riskLevelStr = parts[2].trim();
        String minBetStr = parts[3].trim();
        String maxBetStr = parts[4].trim();

        if(gameName.isBlank()){
            output.println("Error, gameName is required");
            output.println("END");
            return;
        }
        if(providerName.isBlank()){
            output.println("Error, providerName is required");
            output.println("END");
            return;
        }

        // Validation check for riskLevel input
        // Doing the validation here provides faster reply to manager
        // This validation can also happen only to worker

        if(!riskLevelStr.equalsIgnoreCase("KEEP")){
            try{
                RiskLevel.parse(riskLevelStr);
            }catch (Exception e){
                output.println("ERROR invalid riskString. Allowed: low || medium || high");
                output.println("END");
                return;
            }
        }

        if(!minBetStr.equalsIgnoreCase("KEEP")) {
            try {
                BigDecimal minBet = new BigDecimal(minBetStr);
                if(minBet.compareTo(BigDecimal.ZERO) <=0){
                    output.println("Error, min bet must be > 0");
                    output.println("END");
                    return;
                }

            } catch (Exception e) {
                output.println("ERROR minBet must be a valid decimal number or KEEP");
                output.println("END");
                return;
            }
        }
        if (!maxBetStr.equalsIgnoreCase("KEEP")) {
            try {
                BigDecimal maxBet = new BigDecimal(maxBetStr);
                if (maxBet.compareTo(BigDecimal.ZERO) <= 0) {
                    output.println("ERROR maxBet must be > 0");
                    output.println("END");
                    return;
                }
            } catch (Exception e) {
                output.println("ERROR maxBet must be a valid decimal number or KEEP");
                output.println("END");
                return;
            }
        }
        if (!minBetStr.equalsIgnoreCase("KEEP") && !maxBetStr.equalsIgnoreCase("KEEP")) {
            BigDecimal minBet = new BigDecimal(minBetStr);
            BigDecimal maxBet = new BigDecimal(maxBetStr);
            if (maxBet.compareTo(minBet) < 0) {
                output.println("ERROR maxBet must be >= minBet");
                output.println("END");
                return;
            }
        }

        // Send it to its worker (it's owner)
        Worker worker = chooseWorker(gameName);

        // Take the worker's response
        String workerResponse = forwardMsgToWorker(
                worker,
                "MODIFY_GAME " + gameName + "|" + providerName + "|" + riskLevelStr + "|" + minBetStr + "|" + maxBetStr
        );

        // Send the response to Manager
        for(String ln: workerResponse.split("\n")){
            if (!ln.isBlank()) output.println(ln);
        }
        output.println("END");
    }

    private static void handleDeleteExistingGameRequest(String inputString, PrintWriter output){
        String gameName = inputString.substring("DELETE_EXISTING_GAME ".length()).trim();

        if(gameName.isBlank()){
            output.println("ERROR gameName is required!");
            output.println("END");
            return;
        }

        Worker worker = chooseWorker(gameName);
        String workerResponse = forwardMsgToWorker(worker, "DELETE_EXISTING_GAME "+gameName);

        for(String ln : workerResponse.split("\n")){
            if(!ln.isBlank()) output.println(ln);
        }
        output.println("END");
    }

   private static void handleMakeGameVisibleAgain(String inputString, PrintWriter output){
        String gameName = inputString.substring("MAKE_VISIBLE ".length()).trim();

        if(gameName.isBlank()){
            output.println("Error, Game name is empty!");
            output.println("END");
            return;
        }
        Worker worker = chooseWorker(gameName);
        String workerResponse = forwardMsgToWorker(worker,"MAKE_VISIBLE "+gameName);

       for(String ln : workerResponse.split("\n")){
           if(!ln.isBlank()) output.println(ln);
       }
       output.println("END");
   }

    private static void handleFindSpecifProviderProfitLossRequest(String inputString, PrintWriter output){
        String providerName = inputString.substring("FIND_PROVIDER_PROFIT_LOSS ".length()).trim();
        if(providerName.isBlank()){
            output.println("ERROR: Provider Name is Required!");
            output.println("END");
            return;
        }
        gatherProviderProfit(providerName, reducerHost, reducerPort, output);
    }

    private static void handleFindSpecificPlayerProfitLossRequest(String inputString,PrintWriter output){
        String userId = inputString.substring("FIND_PLAYER_PROFIT_LOSS ".length()).trim();

        if(userId.isBlank()){
            output.println("ERROR, UserId is required!");
            output.println("END");
            return;
        }

        gatherPlayerProfit(userId,reducerHost,reducerPort,output);
    }


    private static void handleFindSpecificGameProfitLossRequest(String inputString, PrintWriter output){
        String gameName = inputString.substring("SHOW_GAME_PROFIT_LOSS ".length()).trim();
        if(gameName.isBlank()){
            output.println("Error, GameName required!");
            output.println("END");
            return;
        }
        Worker worker = chooseWorker(gameName);

        String workerResponse = forwardMsgToWorker(worker, "SHOW_GAME_PROFIT_LOSS "+gameName);

        for (String ln : workerResponse.split("\n")) {
            if (!ln.isBlank()) output.println(ln);
        }
        output.println("END");

    }

    private static void handleShowAllGamesRequest(String inputString, PrintWriter output){
        String workerResponse  = gatherAllGamesFromWorkers(inputString);

        // Send the reply to client
        for(String ln : workerResponse.split("\n")){
            if(!ln.isBlank()) output.println(ln);
        }
        output.println("END");
    }

    // Method that forwards a single request from the Master to a specific (chosen) Worker over TCP and returns worker's reply
    // - Opens a new TCP connection to the chosen worker (Host and Port) to execute the message
    // - MasterServer Sends the given from the client message as ONE line using the println().
    // - Reads the worker response line by line until it receives the terminating signal -> "END"
    // - Returns all the collected response lines (that got from worker) joined with '\n'
    // - If the worker is unreachable or any other IO error happens, the method return an error string
    private static String forwardMsgToWorker(Worker worker, String msg){
        try(Socket workerSocket = new Socket(worker.getHost(),worker.getPort());){

            BufferedReader input = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
            PrintWriter output = new PrintWriter(workerSocket.getOutputStream(), true);

            //send the request msg to worker
            // this message initially came from client
            // Client forwarded here (to MasterServer)
            output.println(msg);

            // Read the multi-line response until you find the sent from the worker "END" string
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = input.readLine())!=null){
                if(line.equals("END")) break;
                sb.append(line).append(("\n"));
            }

            // return the worker's response without the "END" terminator
            return sb.toString();
        }catch (Exception e){
            // Convert any worker/network failure into a string response so MasterServer can report it (send it) to the client
            return "Worker Error Occurred: "+ e.getMessage();
        }
    }

    private static String gatherAllGamesFromWorkers(String inputString){

        // USE REDUCER FOR THIS GATHERING

        StringBuilder gameNames = new StringBuilder();

        for (Worker worker: workers){
            String workerResponse = forwardMsgToWorker(worker,inputString );
            if (workerResponse == null || workerResponse.isBlank())continue;

            for(String ln : workerResponse.split("\n")){
                ln = ln.trim();
                if( !ln.isBlank()) gameNames.append(ln).append("\n");
            }
        }
        return gameNames.length() ==0 ? "NO GAMES YET!\n" :  gameNames.toString();
    }


    private static void gatherProviderProfit(String providerName, String reducerHost, int reducerPort, PrintWriter output){

        // jobId tag so the Reducer can separate this specific request from other request that run at the same time in him
        String jobId = UUID.randomUUID().toString();

        // How many workers should reach the reducer
        int expectedWorkers = workers.size();

        // 1. Map: ask all workers to send partials to reducer
        for(Worker worker: workers){
            forwardMsgToWorker(worker, "MAP_PROVIDER_PROFIT "+jobId +"|" + providerName +"|" +reducerHost +"|"+reducerPort+"|"+ expectedWorkers);
        }

        // 2. Wait for reducer to push REDUCE_RESULT back here (here to MasterServer)
        String key = "PROVIDER_PROFIT|"+jobId;
        String finalJson;

        synchronized (reduceLock){
            while(!pendingReduceResults.containsKey(key)) {
                try {
                    reduceLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            finalJson = pendingReduceResults.remove(key);
        }
        if (finalJson == null){
            output.println("ERROR: No reduce result for finding provider profit!");
            output.println("END");
            return;
        }
        output.println(finalJson); //JSON includes per-game + Total
        output.println("END");
    }

    private static void gatherPlayerProfit(String userId, String reducerHost, int reducerPort, PrintWriter output){
        String jobId = UUID.randomUUID().toString();

        int expectedWorkers = workers.size();
        for(Worker worker: workers){
            forwardMsgToWorker(worker, "MAP_PLAYER_PROFIT "+ jobId+"|"+userId+"|"+reducerHost+"|"+reducerPort+"|"+expectedWorkers);
        }

        String key= "PLAYER_PROFIT|"+ jobId;
        String finalJson;
        synchronized (reduceLock){
            while(!pendingReduceResults.containsKey(key)) {
                try {
                    reduceLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            finalJson = pendingReduceResults.remove(key);
        }
        if (finalJson == null){
            output.println("ERROR: No reduce result for finding player profit!");
            output.println("END");
            return;
        }
        output.println(finalJson); //JSON includes per-game + Total
        output.println("END");

    }

    // --- Helping Methods for Player Logic --- //
    // Fetch all available games
    // We gonna reuse the search() method but with filters: ANY
    // Make the request to every worker via: forwardMsgToWorker
    // In worker gather all the available games and forward the result list to Reducer
    // Reducer gathers all the lists from all the workers
    // Reducer Mergers them list's to a new finall one
    // Lastly he pushes it back to Master
    // MasterServer send the result to Player
    private static String fetchAllAvailableGames(){

        // Use unique id so we can match Reducer result to this request
        // (many players / managers are requesting different things simuteniously)
        String jobId = UUID.randomUUID().toString();

        // Reducer must know how many workers will forward him results
        int expectedNWorkers = workers.size();

        // MAP: Ask all workers to send their active games to reducer
        for(Worker worker: workers){
            // Re-use MAP_SEARCH with accept all filters:
            // jobId|minStars|betCategory|risk|reducerHost|reducerPost|expectedNworkers
            forwardMsgToWorker(worker, "MAP_SEARCH "+jobId+"|0|ANY|ANY|"+reducerHost+"|"+reducerPort+"|"+expectedNWorkers);
        }

        // Waiting for Reducer's Result
        String key = "SEARCH|" +jobId;
        String finalResult;

        // Safety net: If reducer fails or network error occurs -> timeout (we dont block it forever)
        long deadline = System.currentTimeMillis()+10_000; // 10 seconds timeout

        // reduceLock protects the shared map (pendingReduceResults)
        // because multiple threads access it
        // - client threads waiting here
        // - reducer callback threads writing results in handleReducerPush()
        synchronized (reduceLock) {

            // while the reducer hasnt delivered the result for this jobId, wait
            while (!pendingReduceResults.containsKey(key)) {

                // compute remaining time till timeout
                long remain = deadline - System.currentTimeMillis();

                // If timeout is reached, stop waiting
                if (remain <= 0) break;

                try {
                    // Wait releases the lock temporarily and sleeps until:
                    // - notified by reducer's callback thread (notifyAll)
                    // - or timeout expires
                    reduceLock.wait(remain);
                } catch (InterruptedException e) {
                    // If the thread is interrupted, show intterupt flag and stop waiting
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Take the result and remove it from the map so it doesn't accumulate in memory
            finalResult = pendingReduceResults.remove(key);
        }
        // If nothing arrived (timeout/error), return empty message
        if (finalResult == null || finalResult.isBlank()) {
            return "There are no available games yet!\n";
        }
        return finalResult;

    }

    // Player search() method implementation:
    private static void handlePlayerSearch(String inputString, PrintWriter output){
        String payload = inputString.substring("SEARCH ".length()).trim();
        String[] parts = payload.split("\\|");
        if (parts.length != 4) {
            output.println("ERROR bad SEARCH format. Expected: playerId|minStars|betCategory|risk");
            output.println("END");
            return;
        }

        String playerId = parts[0].trim(); // (not used in filtering right now, but good to keep)
        int minStars = Integer.parseInt(parts[1].trim());
        String betCat = parts[2].trim();
        String risk = parts[3].trim();

        // assign unique id so reducer can separate different searches
        String jobId = UUID.randomUUID().toString();
        int expectedWorkers = workers.size();

        // MAP: broadcast to all workers
        for (Worker worker : workers) {
            forwardMsgToWorker(worker,
                    "MAP_SEARCH " + jobId + "|" + minStars + "|" + betCat + "|" + risk + "|" +
                            reducerHost + "|" + reducerPort + "|" + expectedWorkers
            );
        }

        // WAIT for reducer push on callback port
        String key = "SEARCH|" + jobId;
        String finalResult;


        long deadline = System.currentTimeMillis() + 10_000; // 10 sec


        synchronized (reduceLock) {
            while (!pendingReduceResults.containsKey(key)) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) break;
                try { reduceLock.wait(remain); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            finalResult = pendingReduceResults.remove(key);
        }

        if (finalResult == null) {
            output.println("ERROR SEARCH timeout (Reducer did not push result)");
            output.println("END");
            return;
        }

        // send final lines to player
        for (String ln : finalResult.split("\n")) {
            if (!ln.isBlank()) output.println(ln);
        }
        output.println("END");
    }

    // Player's handle play() method implementation
    private static void handlePlayRequest(String inputString, PrintWriter output){
        String payload = inputString.substring("PLAY ".length()).trim();
        String[] parts = payload.split("\\|");
        if(parts.length !=3){
            output.println("ERROR bad PLAY format. Expected: playerId|gameName|bet");
            output.println("END");
            return;
        }
        // split the input to retrieve gameName
        String playerId = parts[0].trim();
        String gameName = parts[1].trim();
        String bet = parts[2].trim();

        BigDecimal betAmount;
        try {
           betAmount = new BigDecimal(bet); // validation only
        } catch (NumberFormatException e) {
            output.println("ERROR Bet must be a valid decimal number");
            output.println("END");
            return;
        }

        if (betAmount.compareTo(BigDecimal.ZERO) <= 0) {
            output.println("ERROR Bet must be > 0");
            output.println("END");
            return;
        }


        // Send the request to worker only if player's Balance is >= to his bet
        PlayerBalance playerBalance;
        synchronized (playerBalances){
            playerBalance = playerBalances.get(playerId);
        }
        if(playerBalance == null){
            output.println("This Player: "+playerId+" has 0 tokens. Add tokens to continue");
            output.println("END");
            return;
        }
        if(playerBalance.getBalance().compareTo(new BigDecimal(bet)) <0){
            output.println("Insufficient balance for player: "+playerId+". Add balance to continue or lower your bet!");
            output.println("END");
            return;
        }
        // Subtract the bet for user's Balance
        playerBalance.removeBalance(new BigDecimal(bet));

        // forward request to worker
        Worker worker = chooseWorker(gameName);

        String workerResponse;
        try{
            workerResponse = forwardMsgToWorker(worker, "PLAY "+playerId+"|"+gameName+"|"+bet);
        }catch (Exception e) {
            // refund on worker if communication failure
            playerBalance.addBalance(betAmount);
            output.println("ERROR Failed to contact worker: " + e.getMessage());
            output.println("END");
            return;
        }

        if (workerResponse.isBlank()) {
            playerBalance.addBalance(betAmount);
            output.println("ERROR Empty response from worker");
            output.println("END");
            return;
        }

        String[] lines = workerResponse.split("\n");
        String firstNonBlank = null;
        for (String line : lines) {
            if (!line.isBlank()) {
                firstNonBlank = line.trim();
                break;
            }
        }

        if (firstNonBlank == null) {
            playerBalance.addBalance(betAmount);
            output.println("ERROR Empty response from worker");
            output.println("END");
            return;
        }

        if (firstNonBlank.startsWith("PLAY_RESULT")) {
            String[] workerResponseParts = firstNonBlank.split("\\|");
            if (workerResponseParts.length != 6) {
                playerBalance.addBalance(betAmount);
                output.println("ERROR Worker response bad format!");
                output.println("END");
                return;
            }

            try {
                // workerResponseParts[4] is like "payout=123.45"
                String payoutPart = workerResponseParts[4].trim();
                if (!payoutPart.startsWith("payout=")) {
                    throw new IllegalArgumentException("Missing payout field");
                }

                String payoutValue = payoutPart.substring("payout=".length()).trim();
                BigDecimal payout = new BigDecimal(payoutValue);

                playerBalance.addBalance(payout);


            } catch (Exception e) {
                // refund original bet if payout parsing fails
                playerBalance.addBalance(betAmount);
                output.println("ERROR Failed to parse worker payout: " + e.getMessage());
                output.println("END");
                return;
            }
        } else {
            // worker reported an error, so refund the bet
            playerBalance.addBalance(betAmount);
        }

        // Forward worker lines except END
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.equals("END")) {
                continue;
            }
            output.println(trimmed);
            System.out.println(trimmed);
        }

        output.println("Player: " + playerId + " Balance: " + playerBalance.getBalance());
        output.println("END");
    }

    // Player rate() method implementation:
    private static void  handlePlayerRate(String inputString, PrintWriter output){
        // Rate request from player comes like:
        // SEARCH playerId|gameName|stars;
        String payload = inputString.substring("RATE ".length()).trim();
        String[] parts = payload.split("\\|");

        if (parts.length !=3){
            output.println("ERROR bad RATE format. Expected: playerId|gameName|stars");
            output.println("END");
            return;
        }
        String playerId = parts[0].trim();
        String gameName = parts[1].trim();
        int stars = Integer.parseInt(parts[2].trim());

        // Send it to its stored worker
        Worker worker  = chooseWorker(gameName);

        String workerResponse = forwardMsgToWorker(worker, "RATE "+playerId+"|"+gameName+"|"+stars);

        // Send the response to Player
        for(String ln: workerResponse.split("\n")){
            if (!ln.isBlank()) output.println(ln);
        }
        output.println("END");
        return;
    }

    // Player addBalance() method implementation:
    private static void handlePlayerAddBalanceRequest(String inputString, PrintWriter output){
        //ADD_BALANCE "+userId+"|"+tokens
        String payload = inputString.substring("ADD_BALANCE ".length()).trim();
        String[] parts = payload.split("\\|");

        if(parts.length != 2){
            output.println("Error. Bad format, expected: ADD_BALANCE userId|tokens");
            output.println("END");
            return;
        }
        String userId = parts[0].trim();
        if(userId.isBlank()){
            output.println("Error, userId required!");
            output.println("END");
            return;
        }
        BigDecimal tokens;
        try{
            tokens = new BigDecimal(parts[1].trim());
        } catch (NumberFormatException e) {
            output.println("ERROR tokens must be a valid decimal number");
            output.println("END");
            return;
        }
        if (tokens.compareTo(BigDecimal.ZERO) <= 0) {
            output.println("ERROR tokens must be > 0");
            output.println("END");
            return;
        }

        // Add the balance to user
        PlayerBalance playerBalance;
        synchronized (playerBalances){
            playerBalance = playerBalances.get(userId);
            if(playerBalance==null){
                playerBalance = new PlayerBalance(BigDecimal.ZERO);
                playerBalances.put(userId,playerBalance);
            }
        }

        playerBalance.addBalance(tokens);
        BigDecimal updatedBalance = playerBalance.getBalance();

        output.println("OK balance added successfully for userId=" + userId + " | newBalance=" + updatedBalance);
        output.println("END");
    }

    // Player viewBalance() method implementation:
    private static void handlePlayerViewBalanceRequest(String inputString, PrintWriter output){
        String userId = inputString.substring("VIEW_BALANCE ".length()).trim().toLowerCase();

        if(userId.isBlank()){
            output.println("Error, userId is empty!");
            output.println("END");
            return;
        }
        PlayerBalance playerBalance = playerBalances.get(userId);
        if(playerBalance == null){
            output.println("This user doesnt exist!");
            output.println("END");
            return;
        }

        output.println("User: "+userId+" Balance: "+ playerBalance.getBalance());
        output.println("END");
    }

    /*
    private static void handleCanUserPlayRequest(String inputString, PrintWriter output){

        // "CAN_USER_PLAY "+playerId+"|"+gameName;
        String payload = inputString.substring("CAN_USER_PLAY ".length()).trim();
        String[] parts = payload.split("\\|");

        String userId = parts[1].trim().toLowerCase();
        if (userId.isBlank()){
            output.println("Error, userId is Empty!");
            output.println("END");
            return;
        }
        String gameName = parts[2].trim().toLowerCase();
        if(gameName.isBlank()){
            output.println("Error, gameName is Empty!");
            output.println("END");
            return;
        }
        // Send the request to Worker so he will send back the min bet
        Worker worker = chooseWorker(worker, "FIND_MIN_")
    }*/

    // ------------------------------------------------------------------------------  //
    // ------------------------------------------------------------------------------  //
    // ------------------------------------------------------------------------------  //
    // ------------------------------------------------------------------------------  //
    // ------------------------------------------------------------------------------  //

    // Helping methods for connection with ReducerServer
    // Not fully implemented yet!
    private static void startReducerCallbackServer(int port){
        try(ServerSocket serverSocket = new ServerSocket(port)){
            while(true){
                Socket socket = serverSocket.accept();
                new Thread(()-> handleReducerPush(socket)).start();
            }
        }catch (Exception e){
            System.out.println("[MasterServer] Reducer-callback server error: "+e.getMessage());
        }
    }

    /*
     * Receives pushed reduce results from Reducer.
     * Protocol:
     *   REDUCE_SEARCH_RESULT jobId
     *   GAME|...
     *   GAME|...
     *   END
     */

    private static void handleReducerPush(Socket socket){
        try(socket;
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(),true))
        {
            String header = input.readLine();
            if(header ==null)return;
            header = header.trim();

            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while((line=input.readLine())!=null){
                if("END".equals(line)) break;
                stringBuilder.append(line).append("\n");
            }

            if (header.startsWith("REDUCE_SEARCH_RESULT ")) {
                String jobId = header.substring("REDUCE_SEARCH_RESULT ".length()).trim();
                String key = "SEARCH|" + jobId;

                synchronized (reduceLock) {
                    pendingReduceResults.put(key, stringBuilder.toString().trim());
                    reduceLock.notifyAll(); // wake waiting player thread
                }
                output.println("ACK");
                return;
            }

            if (header.startsWith("REDUCE_PROVIDER_PROFIT_RESULT ")) {
                String payload = header.substring("REDUCE_PROVIDER_PROFIT_RESULT ".length()).trim();
                String[] parts = payload.split("\\|");

                if (parts.length != 2) {
                    output.println("ERROR bad REDUCE_PROVIDER_PROFIT_RESULT header");
                    return;
                }

                String jobId = parts[0].trim();
                String providerName = parts[1].trim(); // optional, useful for debug
                String key = "PROVIDER_PROFIT|" + jobId;

                synchronized (reduceLock) {
                    pendingReduceResults.put(key, stringBuilder.toString().trim());
                    reduceLock.notifyAll();
                }

                System.out.println("[MasterServer] Got provider profit reduce result for provider="
                        + providerName + " jobId=" + jobId);

                output.println("ACK");
                return;
            }


            if (header.startsWith("REDUCE_PLAYER_PROFIT_RESULT ")) {
                String payload = header.substring("REDUCE_PLAYER_PROFIT_RESULT ".length()).trim();
                String[] parts = payload.split("\\|");

                if (parts.length != 2) {
                    output.println("ERROR bad REDUCE_PLAYER_PROFIT_RESULT header");
                    return;
                }

                String jobId = parts[0].trim();
                String playerId = parts[1].trim();
                String key = "PLAYER_PROFIT|" + jobId;

                synchronized (reduceLock) {
                    pendingReduceResults.put(key, stringBuilder.toString().trim());
                    reduceLock.notifyAll();
                }

                System.out.println("[MasterServer] Got player profit reduce result for player="
                        + playerId + " jobId=" + jobId);

                output.println("ACK");
                return;
            }


            output.println("ERROR unknown reducer push: " + header);



        }catch (Exception e){
            System.out.println("[MasterServer] handleReducerPush failed: " + e.getMessage());
        }

    }



}


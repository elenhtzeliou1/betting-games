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
    // MasterServer needs to keep his workers
    // and also needs to keep player balance's
    // Also needs to hold the reducer

    private static final List<Worker> workers = new ArrayList<>();
    private static final Map<String, PlayerBalance> playerBalances = new HashMap<>();

    private static final int REPLICATION_FACTOR = 2; // 1primary + 1 replica

    // Set of providers that have Games stored in workers (Set avoids duplicates)
    // Update the list of providers after successfully storing a game to Worker (Check if already exists)
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
        if (args.length < 3) {
            System.out.println("Usage: java backend.master.MasterServer <masterPort> <reducerHost:reducerPort> <workerHost:port> [workerHost:port] ...");
            System.out.println("Example: java backend.master.MasterServer 5000 192.168.1.20:7000 localhost:6001 localhost:6002");
            return;
        }

        int masterPort;
        try {
            masterPort = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("[MasterServer] Invalid masterPort: " + args[0]);
            return;
        }

        try {
            String[] reducerParts = args[1].split(":");
            if (reducerParts.length != 2) {
                System.out.println("[MasterServer] Invalid reducer address. Expected reducerHost:reducerPort");
                return;
            }

            reducerHost = reducerParts[0].trim();
            reducerPort = Integer.parseInt(reducerParts[1].trim());
        } catch (Exception e) {
            System.out.println("[MasterServer] Invalid reducer configuration: " + e.getMessage());
            return;
        }

        for(int i =2; i<args.length; i++){
            try {
                String[] hostAndPort = args[i].split(":");
                if (hostAndPort.length != 2) {
                    System.out.println("[MasterServer] Skipping invalid worker: " + args[i]);
                    continue;
                }

                String host = hostAndPort[0].trim();
                int port = Integer.parseInt(hostAndPort[1].trim());
                workers.add(new Worker(host, port));
            } catch (Exception e) {
                System.out.println("[MasterServer] Skipping invalid worker '" + args[i] + "': " + e.getMessage());
            }
        }

        if (workers.isEmpty()) {
            System.out.println("[MasterServer] No valid workers configured.");
            return;
        }

        // Callback Port for reducer
        callbackPort = masterPort+1;
        new Thread(()-> startReducerCallbackServer(callbackPort)).start();
        System.out.println("[MasterServer] Reducer callback listening on port: "+callbackPort);
        System.out.println("[MasterServer] Reducer configured at: " + reducerHost + ":" + reducerPort);
        System.out.println("[MasterServer] Workers: " + workers);

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
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true)) {

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
    // UNUSED FOR ACTIVE REPLICATION
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
        } else if (inputString.startsWith("GET_USER_RATINGS ")){
            handlePlayerGetUserRatings(inputString, output);
            return;
        }
        output.println("ERROR unknown player command");
        output.println("END");
    }


    // Handle Manager Requests
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
            output.println("[MasterServer] Error: Invalid JSON: " + e.getMessage());
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
        List<Worker> replicas = getWorkersForGame(gameName);
        Worker primary = replicas.get(0);

        System.out.println("[MasterServer] ADD_NEW_GAME primary="+ primary + " replicas="+ replicas.subList(1, replicas.size()));


        // 1. Send full command to primary worker (he registers to SRNG + stores data)
        String primaryWorkerResponse;
        try{
            primaryWorkerResponse = forwardMsgToWorkerOrThrowExc(primary, "ADD_NEW_GAME " + b64);
        }catch (Exception e){
            output.println("ERROR Primary worker "+ primary + " unreachable: "+ e.getMessage());
            output.println("END");
            return;
        }
        String firstLine = primaryWorkerResponse.split("\\R", 2)[0].trim();
        if(firstLine.equalsIgnoreCase("STORED")){
            synchronized (providers){
                providers.add(new GameProvider(providerName));
            }
        }
        // 2. Send REPLICA command to all other replicas (they store data only, the DO NOT REGISTER to SRNG)
        for(int i = 1; i< replicas.size(); i++){
            Worker replica = replicas.get(i);

            try{
                forwardMsgToWorkerOrThrowExc(replica, "ADD_NEW_GAME_REPLICA "+ b64);
                System.out.println("[MasterServer] ADD_NEW_GAME_REPLICA sent to replica: "+ replica);
            }catch (Exception e){
                System.out.println("[MasterServer] Warning: Replica: "+replica +" unreachable for ADD_NEW_GAME: " + e.getMessage());
            }
        }

        output.println("Master routed to primary worker: " + primary.getPort());
        for(String ln : primaryWorkerResponse.split("\n")){
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

        // Send MODIFY_GAME to ALL replicas
        // same command no SRNG involved
        List<Worker> replicas = getWorkersForGame(gameName);
        String firstSuccessResponse = null;
        String cmd = "MODIFY_GAME " + gameName + "|" + providerName + "|" + riskLevelStr + "|" + minBetStr + "|" + maxBetStr;

        for (Worker w : replicas) {
            try {
                String response = forwardMsgToWorkerOrThrowExc(w, cmd);
                if (firstSuccessResponse == null && !response.trim().toUpperCase().startsWith("ERROR")) {
                    firstSuccessResponse = response;
                }
            } catch (Exception e) {
                System.out.println("[MasterServer] WARNING: MODIFY_GAME failed for worker " + w + ": " + e.getMessage());
            }
        }

        String responseToShow = firstSuccessResponse != null
                ? firstSuccessResponse
                : "ERROR all replicas unreachable or failed\n";

        for(String ln: responseToShow.split("\n")){
            if(!ln.isBlank()) output.println(ln);
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

        List<Worker> replicas = getWorkersForGame(gameName);
        String firstSuccessResponse = null;

        for (int i = 0; i < replicas.size(); i++) {
            Worker w = replicas.get(i);
            String cmd = (i == 0)
                    ? "DELETE_EXISTING_GAME " + gameName
                    : "DELETE_EXISTING_GAME_REPLICA " + gameName;

            try {
                String response = forwardMsgToWorkerOrThrowExc(w, cmd);
                if (firstSuccessResponse == null && !response.trim().toUpperCase().startsWith("ERROR")) {
                    firstSuccessResponse = response;
                }
            } catch (Exception e) {
                System.out.println("[MasterServer] DELETE failed for worker " + w + ": " + e.getMessage());
            }
        }

        if (firstSuccessResponse == null) {
            output.println("ERROR all replicas unreachable or failed");
        } else {
            for (String ln : firstSuccessResponse.split("\n")) {
                if (!ln.isBlank()) output.println(ln);
            }
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
        List<Worker> replicas = getWorkersForGame(gameName);
        String firstSuccessResponse = null;
        boolean activeWorkerRegisteredWithSrng = false;

        for (int i = 0; i < replicas.size(); i++) {
            Worker replica = replicas.get(i);
            String command;

            if (i == 0) {
                command = "MAKE_VISIBLE " + gameName;
            } else if (!activeWorkerRegisteredWithSrng) {
                command = "MAKE_VISIBLE_PROMOTED " + gameName;
            } else {
                command = "MAKE_VISIBLE_REPLICA " + gameName;
            }

            try{
                String response = forwardMsgToWorkerOrThrowExc(replica, command);
                if (!response.trim().toUpperCase().startsWith("ERROR")) {
                    if (firstSuccessResponse == null) {
                        firstSuccessResponse = response;
                    }
                    if (!command.startsWith("MAKE_VISIBLE_REPLICA ")) {
                        activeWorkerRegisteredWithSrng = true;
                    }
                }
            }catch (Exception e){
                System.out.println("[MasterServer] WARNING: MAKE_VISIBLE replica " + replica + " unreachable: " + e.getMessage());
            }
        }

        if (firstSuccessResponse == null) {
            output.println("ERROR all replicas unreachable or failed");
        } else {
            for(String ln : firstSuccessResponse.split("\n")){
                if(!ln.isBlank()) output.println(ln);
            }
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
        // Try primary first, fallback to replicas
        List<Worker> replicas = getWorkersForGame(gameName);
        String workerResponse = null;

        for (Worker w : replicas) {
            try {
                workerResponse = forwardMsgToWorkerOrThrowExc(w, "SHOW_GAME_PROFIT_LOSS " + gameName);
                break; // success
            } catch (Exception e) {
                System.out.println("[MasterServer] Worker " + w + " unreachable for SHOW_GAME_PROFIT_LOSS, trying replica...");
            }
        }
        if (workerResponse == null) {
            output.println("ERROR All workers unreachable for game: " + gameName);
            output.println("END");
            return;
        }

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

    private static void  handlePlayerGetUserRatings(String inputString, PrintWriter output){
        String playerId = inputString.substring("GET_USER_RATINGS ".length()).trim().toLowerCase();

        if (playerId.isBlank()) {
            output.println("ERROR GET_USER_RATINGS: playerId is required");
            output.println("END");
            return;
        }

        String jobId = UUID.randomUUID().toString();
        List<Worker> aliveWorkers = new ArrayList<>();
        for (Worker w : workers) {
            if (isWorkerAlive(w)) {
                aliveWorkers.add(w);
            } else {
                System.out.println("[MasterServer] Worker " + w + " is down, skipping GET_USER_RATINGS MapReduce");
            }
        }

        if (aliveWorkers.isEmpty()) {
            output.println("ERROR No workers available");
            output.println("END");
            return;
        }

        int totalWorkers   = workers.size();
        int expectedWorkers = aliveWorkers.size();

        StringBuilder aliveIdxSb = new StringBuilder();
        for (int i = 0; i < aliveWorkers.size(); i++) {
            if (i > 0) aliveIdxSb.append(",");
            aliveIdxSb.append(workers.indexOf(aliveWorkers.get(i)));
        }
        String aliveIndicesStr = aliveIdxSb.toString();

        // MAP: each alive worker scans its primary games for this player's ratings
        for (Worker w : aliveWorkers) {
            final int workerIndex = workers.indexOf(w);
            final String mapCmd = "MAP_USER_RATINGS " + jobId + "|" + playerId + "|"
                    + reducerHost + "|" + reducerPort + "|" + expectedWorkers
                    + "|" + workerIndex + "|" + totalWorkers + "|" + aliveIndicesStr;
            new Thread(() -> forwardMsgToWorker(w, mapCmd)).start();
        }

        // Wait for Reducer to push back
        String key = "USER_RATINGS|" + jobId;
        String finalResult;

        long deadline = System.currentTimeMillis() + 10_000;
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
            output.println("ERROR GET_USER_RATINGS timeout");
            output.println("END");
            return;
        }

        // Send RATING lines to player (empty response = player has no ratings yet)
        for (String ln : finalResult.split("\n")) {
            if (!ln.isBlank()) output.println(ln);
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
        try(Socket workerSocket = new Socket(worker.getHost(),worker.getPort())){

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

    // Forwards msg to worker and THROWS on network failure. used by replication logic
    private static String forwardMsgToWorkerOrThrowExc(Worker worker, String msg) throws Exception{
        try(Socket workerSocket = new Socket(worker.getHost(), worker.getPort())){
            BufferedReader input = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
            PrintWriter output = new PrintWriter(workerSocket.getOutputStream(), true);

            output.println(msg);
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = input.readLine()) != null){
                if(line.equals("END")) break;
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
        // IO Exception propagates to caller and caller decides how to handle :)
    }

    private static String gatherAllGamesFromWorkers(String inputString){
        StringBuilder gameNames = new StringBuilder();

        // Determine alive workers and build routing metadata
        List<Worker> aliveWorkers = new ArrayList<>();
        for (Worker worker : workers) {
            if (isWorkerAlive(worker)) {
                aliveWorkers.add(worker);
            } else {
                System.out.println("[MasterServer] Worker " + worker + " is down, skipping SHOW_ALL_GAMES");
            }
        }

        if (aliveWorkers.isEmpty()) return "NO GAMES YET!\n";

        int totalWorkers = workers.size();

        StringBuilder aliveIdxSb = new StringBuilder();
        for (int i = 0; i < aliveWorkers.size(); i++) {
            if (i > 0) aliveIdxSb.append(",");
            aliveIdxSb.append(workers.indexOf(aliveWorkers.get(i)));
        }
        String aliveIndicesStr = aliveIdxSb.toString();

        // Each worker receives its index so it applies shouldReportGame() and
        // only returns games it is the primary (or alive fallback) for.
        // No client-side deduplication needed.
        for (Worker worker : aliveWorkers) {
            int workerIndex = workers.indexOf(worker);
            String cmd = "SHOW_ALL_GAMES " + workerIndex + "|" + totalWorkers + "|" + aliveIndicesStr;
            String workerResponse = forwardMsgToWorker(worker, cmd);
            if (workerResponse == null || workerResponse.isBlank()) continue;

            for (String ln : workerResponse.split("\n")) {
                ln = ln.trim();
                if (!ln.isBlank()) gameNames.append(ln).append("\n");
            }
        }

        return gameNames.length() == 0 ? "NO GAMES YET!\n" : gameNames.toString();
    }

    // Extracts the gameName key from a SHOW_ALL_GAMES response line for deduplication
    private static String extractGameNameFromLine(String line) {
        if (!line.startsWith("GameName: ")) return null;
        int pipe = line.indexOf(" | ");
        String name = pipe >= 0 ? line.substring("GameName: ".length(), pipe) : line.substring("GameName: ".length());
        return name.trim().toLowerCase();
    }

    private static void gatherProviderProfit(String providerName, String reducerHost, int reducerPort, PrintWriter output){

        // jobId tag so the Reducer can separate this specific request from other request that run at the same time in him
        String jobId = UUID.randomUUID().toString();

        // Determine alive workers and correct expectedN
        List<Worker> aliveWorkers = new ArrayList<>();
        for (Worker w : workers) {
            if (isWorkerAlive(w)) {
                aliveWorkers.add(w);
            } else {
                System.out.println("[MasterServer] Worker " + w + " is down, skipping PROVIDER_PROFIT MapReduce");
            }
        }

        if(aliveWorkers.isEmpty()){
            output.println("ERROR No workers available for PROVIDER_PROFIT");
            output.println("END");
            return;
        }
        int expectedWorkers = aliveWorkers.size();
        int totalWorkers = workers.size();

        // Build comma-separated alive indices so workers know who is up
        // e.g. "0,2" means W0 and W2 are alive
        StringBuilder aliveIdxSb = new StringBuilder();
        for (int i = 0; i < aliveWorkers.size(); i++) {
            if (i > 0) aliveIdxSb.append(",");
            aliveIdxSb.append(workers.indexOf(aliveWorkers.get(i)));
        }
        String aliveIndicesStr = aliveIdxSb.toString();

        // MAP: each alive worker get its global index so it can filter primary only games
        // parallel - all workers start their map phase simultaneously
        for (Worker w : aliveWorkers) {
            final int workerIndex = workers.indexOf(w);
            final String mapCmd = "MAP_PROVIDER_PROFIT " + jobId + "|" + providerName + "|"
                    + reducerHost + "|" + reducerPort + "|" + expectedWorkers
                    + "|" + workerIndex + "|" + totalWorkers + "|" + aliveIndicesStr;

            new Thread(() -> forwardMsgToWorker(w, mapCmd)).start();
        }

        String key = "PROVIDER_PROFIT|"+jobId;
        String finalJson;

        long deadline = System.currentTimeMillis() + 15_000; // 15s timeout
        synchronized (reduceLock) {
            while (!pendingReduceResults.containsKey(key)) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) break;
                try { reduceLock.wait(remain); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            finalJson = pendingReduceResults.remove(key);
        }

        if (finalJson == null){
            output.println("ERROR: No reduce result for finding provider profit!");
            output.println("END");
            return;
        }

        output.println(finalJson);
        output.println("END");
    }

    private static void gatherPlayerProfit(String userId, String reducerHost, int reducerPort, PrintWriter output){
        String jobId = UUID.randomUUID().toString();

        List<Worker> aliveWorkers = new ArrayList<>();

        for (Worker w : workers) {
            if (isWorkerAlive(w)) {
                aliveWorkers.add(w);
            } else {
                System.out.println("[MasterServer] Worker " + w + " is down, skipping PLAYER_PROFIT MapReduce");
            }
        }

        if (aliveWorkers.isEmpty()) {
            output.println("ERROR No workers available for PLAYER_PROFIT");
            output.println("END");
            return;
        }

        int expectedWorkers = aliveWorkers.size();
        int totalWorkers = workers.size();

        StringBuilder aliveIdxSb = new StringBuilder();
        for (int i = 0; i < aliveWorkers.size(); i++) {
            if (i > 0) aliveIdxSb.append(",");
            aliveIdxSb.append(workers.indexOf(aliveWorkers.get(i)));
        }
        String aliveIndicesStr = aliveIdxSb.toString();

        for (Worker w : aliveWorkers) {
            final int workerIndex = workers.indexOf(w);
            final String mapCmd = "MAP_PLAYER_PROFIT " + jobId + "|" + userId + "|"
                    + reducerHost + "|" + reducerPort + "|" + expectedWorkers
                    + "|" + workerIndex + "|" + totalWorkers + "|" + aliveIndicesStr;

            new Thread(() -> forwardMsgToWorker(w, mapCmd)).start();
        }

        String key = "PLAYER_PROFIT|" + jobId;
        String finalJson;

        long deadline = System.currentTimeMillis() + 15_000; // 15s timeout
        synchronized (reduceLock) {
            while (!pendingReduceResults.containsKey(key)) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) break;
                try { reduceLock.wait(remain); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            finalJson = pendingReduceResults.remove(key);
        }

        if (finalJson == null){
            output.println("ERROR: No reduce result for finding player profit!");
            output.println("END");
            return;
        }

        output.println(finalJson);
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
        // (many players / managers are requesting different things simultaneously)
        String jobId = UUID.randomUUID().toString();

        // Only send to alive workers, adjust expectedN
        List<Worker> aliveWorkers = new ArrayList<>();
        for (Worker w : workers) {
            if (isWorkerAlive(w)) {
                aliveWorkers.add(w);
            } else {
                System.out.println("[MasterServer] Worker " + w + " is down, skipping FETCH_ALL MapReduce");
            }
        }

        if (aliveWorkers.isEmpty()) {
            return "ERROR No workers available\n";
        }

        int totalWorkers = workers.size();
        int expectedNWorkers = aliveWorkers.size();

        // Build alive-index string so each worker knows which peers are up
        // e.g. workers=[W0, W1, W2], alive=[W0, W2] -> "0,2"
        StringBuilder aliveIdxSb = new StringBuilder();
        for (int i = 0; i < aliveWorkers.size(); i++) {
            if (i > 0) aliveIdxSb.append(",");
            aliveIdxSb.append(workers.indexOf(aliveWorkers.get(i)));
        }
        String aliveIndicesStr = aliveIdxSb.toString();

        for (Worker worker : aliveWorkers) {
            final int workerIndex = workers.indexOf(worker);
            // New format: jobId|minStars|betCat|risk|reducerHost|reducerPort|expectedN|workerIndex|totalWorkers|aliveIndices
            final String mapCmd = "MAP_SEARCH " + jobId + "|0|ANY|ANY|"
                    + reducerHost + "|" + reducerPort + "|" + expectedNWorkers
                    + "|" + workerIndex + "|" + totalWorkers + "|" + aliveIndicesStr;
            new Thread(() -> forwardMsgToWorker(worker, mapCmd)).start();
        }

        String key = "SEARCH|" + jobId;
        String finalResult;

        long deadline = System.currentTimeMillis() + 10_000;

        synchronized (reduceLock) {
            while (!pendingReduceResults.containsKey(key)) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) break;
                try {
                    reduceLock.wait(remain);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            finalResult = pendingReduceResults.remove(key);
        }

        if (finalResult == null) {
            return "ERROR FETCH_ALL_AVAILABLE_GAMES timeout (Reducer did not push result)\n";
        }

        if (finalResult.isBlank()) {
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

        String playerId = parts[0].trim(); // (not used in filtering right now, but good to keep??)
        int minStars = Integer.parseInt(parts[1].trim());
        String betCat = parts[2].trim();
        String risk = parts[3].trim();

        // assign unique id so reducer can separate different searches
        String jobId = UUID.randomUUID().toString();

        // Keep only alive workers
        List<Worker> aliveWorkers = new ArrayList<>();
        for (Worker w : workers) {
            if (isWorkerAlive(w)) {
                aliveWorkers.add(w);
            } else {
                System.out.println("[MasterServer] Worker " + w + " is down, skipping SEARCH MapReduce");
            }
        }

        if (aliveWorkers.isEmpty()) {
            output.println("ERROR No workers available");
            output.println("END");
            return;
        }

        int totalWorkers = workers.size();
        int expectedWorkers = aliveWorkers.size();

        // Build alive-index string (same pattern as MAP_PROVIDER_PROFIT / MAP_PLAYER_PROFIT)
        StringBuilder aliveIdxSb = new StringBuilder();
        for (int i = 0; i < aliveWorkers.size(); i++) {
            if (i > 0) aliveIdxSb.append(",");
            aliveIdxSb.append(workers.indexOf(aliveWorkers.get(i)));
        }
        String aliveIndicesStr = aliveIdxSb.toString();

        // MAP: broadcast to alive workers only, each with its own index so it
        // can decide which games it is the primary (or fallback) for.
        for (Worker worker : aliveWorkers) {
            final int workerIndex = workers.indexOf(worker);
            final String mapCmd = "MAP_SEARCH " + jobId + "|" + minStars + "|" + betCat + "|" + risk + "|"
                    + reducerHost + "|" + reducerPort + "|" + expectedWorkers
                    + "|" + workerIndex + "|" + totalWorkers + "|" + aliveIndicesStr;
            new Thread(() -> forwardMsgToWorker(worker, mapCmd)).start();
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

        synchronized (playerBalance){
            if(playerBalance.getBalance().compareTo(new BigDecimal(bet)) <0){
                output.println("Insufficient balance for player: "+playerId+". Add balance to continue or lower your bet!");
                output.println("END");
                return;
            }
            // Subtract the bet for user's Balance
            playerBalance.removeBalance(new BigDecimal(bet));
        }

        // Try primary first, then replicas on failure
        List<Worker> replicas = getWorkersForGame(gameName);
        String workerResponse = null;
        Worker usedWorker = null;

        for (Worker w : replicas) {
            try {
                workerResponse = forwardMsgToWorkerOrThrowExc(w, "PLAY " + playerId + "|" + gameName + "|" + bet);
                usedWorker = w;
                break; // primary worked, stop here
            } catch (Exception e) {
                System.out.println("[MasterServer] Worker " + w + " failed for PLAY, trying replica: " + e.getMessage());
            }
        }

        if (workerResponse == null) {
            // All workers unreachable — refund
            playerBalance.addBalance(betAmount);
            output.println("ERROR All workers unreachable for PLAY");
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
                // workerResponseParts[3] is "random=N"
                String randomPart = workerResponseParts[3].trim();
                int randomNumber =0;
                if (randomPart.startsWith("random=")){
                    randomNumber = Integer.parseInt(randomPart.substring("random=".length()).trim());
                }

                // workerResponseParts[4] is like "payout=123.45"
                String payoutPart = workerResponseParts[4].trim();
                if (!payoutPart.startsWith("payout=")) {
                    throw new IllegalArgumentException("Missing payout field");
                }

                String payoutValue = payoutPart.substring("payout=".length()).trim();
                BigDecimal payout = new BigDecimal(payoutValue);

                if (payout.compareTo(BigDecimal.ZERO) > 0) {
                    playerBalance.addBalance(payout);
                }

                // SYNC_PLAY: update all other replicas without calling SRNG
                final String payoutStr = payout.toPlainString();
                final int finalRandom = randomNumber;
                final Worker handledBy = usedWorker;
                for (Worker w : replicas) {
                    if (w == handledBy) continue; // skip the one that handled the PLAY
                    try {
                        forwardMsgToWorkerOrThrowExc(w, "SYNC_PLAY " + gameName + "|" + playerId + "|" + bet + "|" + payoutStr + "|" + finalRandom);
                    } catch (Exception ex) {
                        System.out.println("[MasterServer] SYNC_PLAY failed for replica " + w + ": " + ex.getMessage());
                    }
                }


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

        // Send RATE to ALL replicas (same command, no SRNG involved)
        List<Worker> replicas = getWorkersForGame(gameName);
        String firstSuccessResponse = null;

        for (Worker w : replicas) {
            try {
                String resp = forwardMsgToWorkerOrThrowExc(w, "RATE " + playerId + "|" + gameName + "|" + stars);
                if (firstSuccessResponse == null && !resp.trim().toUpperCase().startsWith("ERROR")) {
                    firstSuccessResponse = resp;
                }
            } catch (Exception e) {
                System.out.println("[MasterServer] WARNING: RATE failed for worker " + w + ": " + e.getMessage());
            }
        }

        String responseToShow = firstSuccessResponse != null
                ? firstSuccessResponse
                : "ERROR all replicas unreachable or failed\n";

        for(String ln: responseToShow.split("\n")){
            if (!ln.isBlank()) output.println(ln);
        }
        output.println("END");
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
        String userId = parts[0].trim().toLowerCase();
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

        output.println("OK balance added successfully for user: " + userId + "\nCurrent Player Balance: " + updatedBalance);
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


    // Active replication helping methods
    private static List<Worker> getWorkersForGame(String gameName){
        String key = gameName.trim().toLowerCase();

        int hash = Math.floorMod(key.hashCode(), workers.size());
        int count = Math.min(REPLICATION_FACTOR, workers.size());

        List<Worker> results = new ArrayList<>();

        for(int i=0; i< count; i++){
            results.add(workers.get((hash+i) % workers.size()));
        }
        return results;
    }

    // check worker if alive
    private static boolean isWorkerAlive(Worker worker){
        try(Socket s = new Socket()){
            s.connect(new java.net.InetSocketAddress(worker.getHost(), worker.getPort()), 2000);
            return true;
        }catch (Exception e){
            return false;
        }
    }


    private static String forwardToReplicasAndReturnFirstSuccess(
            String gameName,
            String primaryCommand,
            String replicaCommand
    ) {
        List<Worker> replicas = getWorkersForGame(gameName);
        String firstSuccess = null;

        for (int i = 0; i < replicas.size(); i++) {
            Worker worker = replicas.get(i);
            String command = (i == 0) ? primaryCommand : replicaCommand;

            try {
                String response = forwardMsgToWorkerOrThrowExc(worker, command);

                if (firstSuccess == null && !response.trim().toUpperCase().startsWith("ERROR")) {
                    firstSuccess = response;
                }
            } catch (Exception e) {
                System.out.println("[MasterServer] Replica command failed on " + worker + ": " + e.getMessage());
            }
        }

        if (firstSuccess == null) {
            return "ERROR all replicas unreachable or failed\n";
        }

        return firstSuccess;
    }
    
    // ------------------------------------------------------------------------------  //
    // ------------------------------------------------------------------------------  //
    // ------------------------------------------------------------------------------  //
    // ------------------------------------------------------------------------------  //
    // ------------------------------------------------------------------------------  //

    // Helping methods for connection with ReducerServer
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
            if (header.startsWith("REDUCE_USER_RATINGS_RESULT ")) {
                String payload = header.substring("REDUCE_USER_RATINGS_RESULT ".length()).trim();
                String[] parts = payload.split("\\|");
                if (parts.length != 2) {
                    output.println("ERROR bad REDUCE_USER_RATINGS_RESULT header");
                    return;
                }
                String jobId    = parts[0].trim();
                String playerId = parts[1].trim();
                String key = "USER_RATINGS|" + jobId;

                synchronized (reduceLock) {
                    pendingReduceResults.put(key, stringBuilder.toString().trim());
                    reduceLock.notifyAll();
                }

                System.out.println("[MasterServer] Got user ratings reduce result for player=" + playerId);
                output.println("ACK");
                return;
            }


            output.println("ERROR unknown reducer push: " + header);



        }catch (Exception e){
            System.out.println("[MasterServer] handleReducerPush failed: " + e.getMessage());
        }
    }
}


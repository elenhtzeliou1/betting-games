package backend.master;

import backend.common.RiskLevel;
import backend.worker.Worker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class MasterServer {
    // MasterServer needs to keep his worker
    // and also needs to keep player balance's
    // Also nees to hold the reducer

    private static final List<Worker> workers = new ArrayList<>();
    // Map<String,PlayerState> playersById (balance)
    // Reducer reducer;
    //
    //

    public static void main(String[] args) {
        //Helping Message
        if (args.length < 2) {
            System.out.println("Usage: java MasterServer <masterPort> <WorkerHost:port> [workerHost:prot] ... ");
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

        System.out.println("[MasterServer] Workers: "+ workers);


        try(ServerSocket serverSocket = new ServerSocket(masterPort)){
            System.out.println("[MasterServer] Listening on port: "+ masterPort);

            while(true){
                Socket socket = serverSocket.accept();
                System.out.println("[MasterServer] accepted client connection from: "
                        + socket.getInetAddress().getHostAddress() +":"+socket.getPort()
                );

                //drop client if no communication after 15min
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

            String b64 = inputString.substring("ADD_NEW_GAME ".length()).trim();

            //decode JSON to extract gameName only for routing
            String json = new String(java.util.Base64.getDecoder().decode(b64), java.nio.charset.StandardCharsets.UTF_8);

            String gameName;
            try{
                JSONParser parser = new JSONParser();
                JSONObject obj = (JSONObject) parser.parse(json);
                gameName = (String) obj.get("GameName");
            }catch (Exception e){
                output.println("[MasterServer] Error: Invalid JSON: "+e.getMessage());
                output.println("END");
                return;
            }

            //Debug
            if (gameName == null || gameName.isBlank()){
                output.println("[MasterServer] Error: GameName not found in given JSON! ");
                output.println("END");
                return;
            }
            System.out.println("[MasterServer] Given GameName: " + gameName);

            //Choose worker
            Worker worker = chooseWorker(gameName);

            //Debug: Print the chosen worker
            System.out.println("[MasterServer] Chosen worker: "+ worker.getPort());

            //Debug: Print the JSON
            System.out.println("[MasterServer] Full Given JSON: "+json);

            //Forward the request to the choosen worker:
            String workerResponse = forwardMsgToWorker(worker, "ADD_NEW_GAME "+ b64);

            //show to which worker the request was routed
            output.println("Master server routed Manager Request to worker: "+ worker.getPort());

            //Give all the worker response
            for(String ln : workerResponse.split("\n")){
                if(!ln.isBlank()) output.println(ln);
            }

            output.println("END");
            return;
        }
        else if(inputString.startsWith("SHOW_ALL_GAMES")){
           //Simple Gather all the stored Games that the workers have in their memory
            String workerResponse  = gatherAllGamesForWorkers(inputString);

            //Send the reply to client
            for(String ln : workerResponse.split("\n")){
                if(!ln.isBlank()) output.println(ln);
            }
            output.println("END");
            return;
        } else if (inputString.startsWith("UPDATE_GAME_RISK ")) {
            String payload = inputString.substring("UPDATE_GAME_RISK ".length()).trim();

            String[] parts = payload.split("\\|");
            if(parts.length !=3){
                output.println("Error, bad format: Expected: gameName|providerName|risk(low||medium||high)");
                output.println("END");
                return;
            }
            String gameName = parts[0].trim();
            String providerName = parts[1].trim();
            String riskLevelStr = parts[2].trim();

            // Validation check for riskLevel input
            // Doing the validation here provides faster reply to manager
            // This validation can also happen only to worker

            try{
                RiskLevel.parse(riskLevelStr);
            }catch (Exception e){
                output.println("ERROR invalid riskString. Allowed: low || medium || high");
                output.println("END");
                return;
            }

            // Send it to its worker (it's owner)
            Worker worker = chooseWorker(gameName);

            // Take the worker's response
            String workerResponse = forwardMsgToWorker(worker, "UPDATE_GAME_RISK "+ gameName+"|"+providerName+"|"+riskLevelStr);

            // Send the response to Manager
            for(String ln: workerResponse.split("\n")){
                if (!ln.isBlank()) output.println(ln);
            }
            output.println("END");
            return;
            
        }else if(inputString.startsWith("DELETE_EXISTING_GAME ")){
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
            return;

        }

        output.println("ERROR unknown manager command");
        output.println("END");

    }



    private static void handlePlayerLogic(String inputString,PrintWriter output){
        if(inputString.startsWith("SEARCH ")){

            //
            //
            output.println("END");
            return;
        }
        if(inputString.startsWith("PLAY ")){

            //
            //
            output.println("END");
            return;
        }

        if(inputString.startsWith("ADD_BALANCE ")){

            //
            //
            output.println("END");
            return;
        }

        output.println("ERROR unkown player command");
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

    private static String gatherAllGamesForWorkers(String inputString){
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

}


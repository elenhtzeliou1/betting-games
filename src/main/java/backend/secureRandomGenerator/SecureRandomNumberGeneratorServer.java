package backend.secureRandomGenerator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;


// Handles Multiple Worker Request
// Worker requests:
//  - Register Game
//  - Delete Game
//  - Get Number
public class SecureRandomNumberGeneratorServer {

    // always on worker connects with it
    // multithreaded tcp server
    // accepts many worker requests

    // later change them 2 below to set the attributes via args
    private static final int randomGeneratorPort = 8000;
    private static final String randomGeneratorHost = "localhost";

    // Holds the Game Registrations
    // <GameName, RNGContext>
    // RNGContext contains all the required information
    //  - Buffer
    //  - Producer
    //  - Secret
    private static final Map<String, RNGContext> games = new HashMap<>();

    public static void main(String[] args) {

        // Establish TCP connection with multiple workers
        try (ServerSocket serverSocket = new ServerSocket(randomGeneratorPort)) {

            while (true) {
                // Let multiple worker's connection
                // Multithreaded RandomGenerator
                Socket socket = serverSocket.accept();
                System.out.println("[RandomGeneratorServer] Accepted Worker connection from: " +
                        socket.getInetAddress().getHostAddress() + ":" + socket.getPort());

                // Handle worker requests
                new Thread(() -> handleWorkerRequest(socket)).start();

            }

        } catch (Exception e) {
            System.out.println("RandomGeneratorServer exception: " + e.getMessage());
        }

    }

    private static void handleWorkerRequest(Socket socket) {
        try (socket;
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true)
        ) {

            String inputString = input.readLine();
            if (inputString == null) return;

            inputString = inputString.trim();
            System.out.println("[RandomGeneratorServer] Got request data: " + inputString);

            if (inputString.startsWith("REGISTER ")) {
                handleGameRegistration(inputString, output);
                return;
            }
            if (inputString.startsWith("GET ")) {
                handleGetNumber(inputString, output);
                return;
            }
            if (inputString.startsWith("DELETE ")) {
                handleGameDeletion(inputString, output);
                return;
            }
            output.println("Error! Unknown Command");


        } catch (Exception e) {
            System.out.println("[RandomGeneratorServer] Worker socket shutdown here! " + e.getMessage());
        }
    }

    private static void handleGameRegistration(String inputString, PrintWriter output) {
        try{
            String payload = inputString.substring("REGISTER ".length()).trim();
            String[] parts = payload.split("\\|");
            if (parts.length != 3) {
                output.println("Error, Bad REGISTER format. Expected: gameName|secret|bufferSize");
                return;
            }

            String gameName = parts[0].trim().toLowerCase();
            String gameSecret = parts[1].trim();
            int bufferSize = Integer.parseInt(parts[2].trim());

            // Validations
            if(gameName.isBlank()){
                output.println("Error, gameName is empty!");
                return;
            }
            if(gameSecret.isBlank()){
                output.println("Error, gameSecret is empty!");
                return;
            }
            if(bufferSize<=0){
                output.println("Error, BufferSize should be >0");
                return;
            }

            synchronized (games) {
                if (games.containsKey(gameName)) {
                    output.println("Error, This game with GameName: " + gameName + " already exists!");
                    return;
                }
                // Buffer and producer for this game are in RNG Context
                RNGContext rngContext = new RNGContext(gameName,gameSecret,bufferSize);
                games.put(gameName,rngContext);
            }

            System.out.println("[SRNG | DEBUG] Game: "+gameName+" Register SUCCESSFULLY | bufferSize="+bufferSize);
            output.println("COMPLETE");

        }catch (NumberFormatException e){
            output.println("ERROR, Invalid Buffer Size");
        }catch (Exception e){
            output.println("ERROR, Game Registration Failed! "+e.getMessage() );
        }

    }

    private static void handleGetNumber(String inputString, PrintWriter output){
            try{
                String gameName = inputString.substring("GET ".length()).trim().toLowerCase();

                // Game name validation
                if(gameName.isBlank()){
                    output.println("Error|Game name is empty!");
                    return;
                }
                RNGContext gameContext;
                synchronized (games){
                    gameContext = games.get(gameName);
                }
                // Game name validation
                if(gameContext == null){
                    output.println("Error, Game not found!");
                    return;
                }
                // -------
                // [DEBUG]
                System.out.println("[SRNG | DEBUG] GET NUMBER request for Game: "+ gameName);

                int number = gameContext.getNumber();

                // -------
                // [DEBUG]
                System.out.println("[SRNG | DEBUG] Number pulled for Game: "+gameName+" : ->"+number);

                String shaNumStr = Integer.toString(number);
                String hash = HashHelper.sha256(shaNumStr+gameContext.getSecret());

                // Worker is going to verify this hash locally to himself
                // Send reply to worker
                output.println("NUMBER|"+ number+"|"+ hash);

            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
                output.println("Error, getNumber() was interrupted "+ e.getMessage());
            }catch (Exception e){
                output.println("Error, getNumber() failed! "+ e.getMessage());
            }

    }

    private static void handleGameDeletion(String inputString, PrintWriter output){
        try{
            String gameName = inputString.substring("DELETE ".length()).trim().toLowerCase();

            if(gameName.isBlank()){
                output.println("Error, GameName is empty!");
                return;
            }
            RNGContext context;
            synchronized (games){
                context = games.remove(gameName);
            }
            if (context== null){
                output.println("Error, No game found for gameName: "+ gameName);
                return;
            }
            context.stop();
            output.println("COMPLETE");
        }catch (Exception e){
            output.println("Error, Deletion failed: "+ e.getMessage());
        }
    }

}

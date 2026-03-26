package backend.consoleApps;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Scanner;

public class ManagerConsoleApp {


    public static void main(String[] args) {

        String masterHost =  "localhost";
        int masterPort =5000;

        // ManagerConsoleApp <masterHost> <masterPort>
        if(args.length >=1) masterHost = args[0];
        if(args.length >=2) masterPort =Integer.parseInt(args[1]);

        try(Socket socket = new Socket(masterHost,masterPort)){
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(),true);

            Scanner scanner = new Scanner(System.in);
            String choice;

            //Read and print to manager console the server greeting
            System.out.println(input.readLine());

            //role
            //send the role to server
            output.println("MANAGER");
            // print the ok message from the server
            System.out.println(input.readLine());

            //manager menu:
            //
            //
            do{
                System.out.println("=== MANAGER MENU ===");
                System.out.println("1. Add new Game");
                System.out.println("2. Delete existing game");
                System.out.println("3. Make an existing game visible again");
                System.out.println("4. Update game Risk");
                System.out.println("5. Show total Profit/Loss for specific provider");
                System.out.println("6. Show total Profit/Loss for specific player");
                System.out.println("7. Show total Profit/Loss for specific game");
                System.out.println("8. Show all games");
                System.out.println("0. EXIT");

                choice = scanner.nextLine().trim();

                System.out.println("[DEBUG] choice: " +choice);
                if (choice.equalsIgnoreCase("exit") || choice.equals("0")) {
                    output.println("exit");
                    readMsgUntilEnd(input);   // Master sends Bye bye + END
                    break;                    // exit the menu loop
                }


                switch(choice){

                    case "1": {
                        // Case: Add new game
                        addNewGame(scanner, input, output);
                        break;
                    }
                    case "2": {
                        // Case: Delete an existing game (Make game not Visible for Player)

                        // 1. Show all games if there are any:
                        boolean noGames = showAllGamesOrReturnNoGames(input, output);
                        if (noGames) break; // go back to menu

                        // 2. Delete an existing game (Make game not Visible for Player)
                        deleteExistingGame(scanner, input,output);
                        break;
                    }
                    case "3": {
                        // Case: Make an existing game visible to player again

                        // 1. Show all games if there are any
                        boolean noGames = showAllGamesOrReturnNoGames(input, output);
                        if (noGames) break; // go back to menu

                        makeExistingGameVisible(scanner,input,output);
                        break;
                    }
                    case "4": {
                        // Case: Update Risk Level of Specific Game

                        // 1. show all games:
                        boolean noGames = showAllGamesOrReturnNoGames(input, output);
                        if (noGames) break; // go back to menu

                        updateGameRisk(scanner,input,output);
                        break;
                    }
                    case "5": {
                        // case: Show profit/loss for specific provider
                        requestSpecificProviderProfitLoss(scanner,input,output);
                        break;

                    }
                    case "6":{
                        //case: Show profit/loss for specific player (using player id)
                        requestSpecificPlayerProfitLoss(scanner,input,output);
                        break;
                    }
                    case "7":{
                        //case: show profit/loss for specif game
                        requestSpecificGameProfitLoss(scanner,input,output);
                        break;
                    }
                    case "8": {
                        System.out.println("Printing all existing games...");
                        output.println("SHOW_ALL_GAMES ");
                        readMsgUntilEnd(input);
                        break;
                    }
                    default:
                        System.out.println("Not Valid choice...");
                        break;
                }


            } while (true);

        }catch (IOException e){
            System.out.println("[Manager] Error: "+ e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println("Manager Disconnected");
        }
    }

    private static void addNewGame(Scanner scanner, BufferedReader input, PrintWriter output) throws Exception {
        // Add New Game
        System.out.println("[Adding Game] Give JSON File path: ");
        String path = scanner.nextLine().trim();

        String json = Files.readString(Paths.get(path), StandardCharsets.UTF_8);

        // use base64 encoding to keep it 1line
        String base64encoding = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        //send the request to MasterServer
        output.println("ADD_NEW_GAME " + base64encoding);

        //echo it for debugging:
        System.out.println("[Adding Game] Path you gave: " + path);

        //read the masterServers Response
        readMsgUntilEnd(input);
    }

    private static void deleteExistingGame(Scanner scanner, BufferedReader input, PrintWriter output) throws Exception {
        // 1. Ask user (manager) for GameName:
        System.out.println("Give GameName to delete (Make it not visible to Player): ");
        String gameName = scanner.nextLine().trim();

        // 2. Send Request to MasterServer
        output.println("DELETE_EXISTING_GAME " + gameName);

        // 3. Show the result
        readMsgUntilEnd(input);
    }

    private static void updateGameRisk(Scanner scanner, BufferedReader input, PrintWriter output) throws Exception {
        // 2. Ask user (manager) for GameName:
        System.out.println("Give GameName: ");
        String gameName = scanner.nextLine().trim();

        // 3. Ask user for the ProviderName
        System.out.println("Give ProviderName: ");
        String providerName = scanner.nextLine().trim();

        // 4. Ask for the new RiskLevel
        System.out.println("Give new Risk Level: (low | medium | high)");
        String riskLevel = scanner.nextLine().trim();

        // 5. Send the request to MasterServer
        output.println("UPDATE_GAME_RISK " + gameName + "|" + providerName + "|" + riskLevel);

        // 6. Show the Result
        readMsgUntilEnd(input);
    }

    private static void  makeExistingGameVisible(Scanner scanner, BufferedReader input, PrintWriter output) throws Exception {
        // 1. Ask the manager for GameName
        System.out.println("Give GameName to make it visible to player: ");
        String gameName = scanner.nextLine().trim();

        // 2. Send Request to MasterServer
        output.println("MAKE_VISIBLE "+gameName);

        // 3. Show MasterServer's response
        readMsgUntilEnd(input);
    }

    private static void requestSpecificProviderProfitLoss(Scanner scanner, BufferedReader input, PrintWriter output) throws Exception {
        // 1. Show profit damages per provider
        System.out.println("Give ProviderName: ");
        String providerName = scanner.nextLine().trim();

        // 2. Send the request to MasterServer
        output.println("FIND_PROVIDER_PROFIT_LOSS "+ providerName);

        // 3. Show the received from MasterServer, result
        readMsgUntilEnd(input);
    }

    private static void requestSpecificPlayerProfitLoss(Scanner scanner, BufferedReader input, PrintWriter output) throws Exception {
        System.out.println("Give player id: ");
        String playerId = scanner.nextLine().trim();

        output.println("FIND_PLAYER_PROFIT_LOSS "+ playerId);

        readMsgUntilEnd(input);
    }

    private static void requestSpecificGameProfitLoss(Scanner scanner, BufferedReader input, PrintWriter output) throws Exception {
        System.out.println("Give gameName to see profitLoss: ");
        String gameName  = scanner.nextLine().trim();

        output.println("SHOW_GAME_PROFIT_LOSS "+gameName);

        readMsgUntilEnd(input);
    }
    // helper method for multi-lines responses
    private static void readMsgUntilEnd(BufferedReader input) throws Exception{
        String line;
        while((line = input.readLine())!=null){
            if(line.equals("END")) break;
            System.out.println(line);
        }
    }

    private static boolean showAllGamesOrReturnNoGames(BufferedReader input, PrintWriter output) throws Exception{

        output.println("SHOW_ALL_GAMES");
        String firstLine = input.readLine();

        if(firstLine==null) throw new IOException("Disconnected");

        firstLine =firstLine.trim();

        if(firstLine.equalsIgnoreCase("NO GAMES YET!")){
            System.out.println(firstLine);

            //consume until END so protocol stays in sync
            String line;
            while ((line = input.readLine())!=null){
                if (line.equals("END")) break;
            }
            return true; // No games exist
        }
        //otherwise
        System.out.println(firstLine);
        String line;
        while ((line = input.readLine())!=null){
            if (line.equals("END")) break;
            System.out.println(line);
        }
        return false; // There are games Stored
    }

}

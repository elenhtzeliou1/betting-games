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
                System.out.println("1. ADD_NEW_GAME");
                System.out.println("2. DELETE_EXISTING_GAME");
                System.out.println("3. UPDATE_GAME_RISK");
                System.out.println("4. SHOW_TOTAL_PROFITS_DAMAGES_PER_GAME");
                System.out.println("5. PROFIT_DAMAGES_PER_PROVIDER");
                System.out.println("6. SHOW TOTAL_PROFIT_DAMAGES_FOR_SPECIFIC_PLAYER");
                System.out.println("7. SHOW ALL GAMES");
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

                        break;
                    }

                    case "2": {
                        // Change Game Visibility for Player

                        // 1. Show all games if there are any:
                        boolean noGames = showAllGamesOrReturnNoGames(input, output);
                        if (noGames) break; // go back to menu

                        // 2. Ask user (manager) for GameName:
                        System.out.println("Give GameName to Flip it's Visibility: ");
                        String gameName = scanner.nextLine().trim();

                        // 3. Send Request to MasterServer
                        output.println("DELETE_EXISTING_GAME " + gameName);

                        // 4. Show the result
                        readMsgUntilEnd(input);
                        break;
                    }
                    case "3": {
                        // --- Update Risk Level of Specific Game

                        // 1. show all games:
                        boolean noGames = showAllGamesOrReturnNoGames(input, output);
                        if (noGames) break; // go back to menu

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
                        break;
                    }
                    case "4": {
                        System.out.println("Not implemented yet!");
                        break;
                    }
                    case "5": {
                        // 1. Show profit damages per provider
                        System.out.println("Give ProviderName: ");
                        String providerName = scanner.nextLine().trim();

                        // 2. Send the request to MasterServer
                        output.println("FIND_PROVIDER_PROFIT_LOSS "+ providerName);

                        // 3. Show the received from MasterServer, result
                        readMsgUntilEnd(input);
                        break;


                    }
                    case "7": {
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

    // helper method for multilines responses
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

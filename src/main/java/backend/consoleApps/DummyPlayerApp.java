package backend.consoleApps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class DummyPlayerApp {

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

            // Read and print to player console the server greeting
            System.out.println(input.readLine());

            // role
            // send role to MasterServer
            output.println("PLAYER");

            // print the ok msg from MasterServer
            System.out.println(input.readLine());

            // Dummy player Menu
            do{
                System.out.println("=== DUMMY PLAYER MENU ===");
                System.out.println("1. Show all available Games");
                System.out.println("2. Search");
                System.out.println("3. Rate Game");
                System.out.println("4. Add Tokens");
                System.out.println("0. Exit");


                choice=scanner.nextLine().trim();

                System.out.println("[DEBUG] choice: " +choice);
                if (choice.equalsIgnoreCase("exit") || choice.equals("0")) {
                    output.println("exit");
                    readMsgUntilEnd(input);   // Master sends Bye bye + END
                    break;                    // exit the menu loop
                }

                switch (choice){
                    case "1":{
                        // Show all games that are available
                        fetchAllAvailableGame(output,input);
                        break;
                    }
                    case "2":{
                        // Perform Search()
                        search(scanner, output,  input);
                        break;
                    }
                    case "3":{
                        System.out.println("Not implemented yet");

                        break;
                    }
                    case "4":{
                        System.out.println("Not implemented yet");
                        break;
                    }
                    default:
                        System.out.println("Not Valid choice...");
                        break;
                }

            }while (true);
        } catch (Exception e) {
            System.out.println("[Player] Error: "+ e.getMessage());
        }
    }

    private static void fetchAllAvailableGame(PrintWriter output, BufferedReader input) throws Exception {
        // send request to master
        output.println("FETCH_ALL_AVAILABLE_GAMES");

        // read his result
        readMsgUntilEnd(input);
    }

    // helper method for multilines responses that MasterServer sends
    private static void readMsgUntilEnd(BufferedReader input) throws Exception{
        String line;
        while((line = input.readLine())!=null){
            if(line.equals("END")) break;
            System.out.println(line);
        }
    }

    private static void search(Scanner scanner, PrintWriter output, BufferedReader input) throws Exception {
        System.out.println("Give PlayerId (e.g. user123):");
        String playerId = scanner.nextLine().trim();

        System.out.println("Give MinStars: (0-5)");
        int minStars = readInt(scanner, "Min stars (0-5, 0=ANY): ", 0, 5);

        String betCategory = readEnum(scanner,
                "BetCategory (ANY, $, $$, $$$): ",
                new String[]{"ANY", "$", "$$", "$$$"},
                "ANY"
        );
        String risk = readEnum(scanner,
                "Risk (ANY, low, medium, high): ",
                new String[]{"ANY", "low", "medium", "high"},
                "ANY"
        );

        String cmd = "SEARCH " + playerId + "|" + minStars + "|" + betCategory + "|" + risk;
        output.println(cmd);

        System.out.println("\n--- SEARCH RESULTS ---");
        readMsgUntilEnd(input);
    }

    private static int readInt(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v < min || v > max) {
                    System.out.println("Please enter a number in range " + min + ".." + max);
                    continue;
                }
                return v;
            } catch (Exception e) {
                System.out.println("Invalid number.");
            }
        }
    }

    private static String readEnum(Scanner scanner, String prompt, String[] allowed, String def) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            if (s.isBlank()) return def;

            for (String a : allowed) {
                if (a.equalsIgnoreCase(s)) return a; // keep exact token ($, $$, $$$)
            }
            System.out.println("Invalid option. Allowed: " + String.join(", ", allowed));
        }
    }

}

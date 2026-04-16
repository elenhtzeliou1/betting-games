package backend.consoleApps;

import backend.common.SearchResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.Socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class DummyPlayerApp {

    // simple 'database' that holds search() results for this user that latest searched
    private static final List<SearchResult> lastSearchResults = new ArrayList<>();
    private static String lastPlayerIdThatSearched = null;


    public static void main(String[] args) {
        String masterHost =  "localhost";
        int masterPort = 5000;

        // ManagerConsoleApp <masterHost> <masterPort>
        if(args.length >=1) masterHost = args[0];
        if(args.length >=2) masterPort =Integer.parseInt(args[1]);

        try(Socket socket = new Socket(masterHost,masterPort)){
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(),true);

            Scanner scanner = new Scanner(System.in);
            String choice;

            // Read and print to player console the server's greeting
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
                System.out.println("3. Play");
                System.out.println("4. Rate Game");
                System.out.println("5. Add Tokens");
                System.out.println("6. View Balance");
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
                        // Perform Play(). Play is only allowed to previous searched games
                        System.out.println("Give playerId (e.g. user123): ");
                        String playerId = scanner.nextLine().trim();
                        play(playerId,scanner,input,output);
                        break;
                    }
                    case "4":{
                        // Rate game Rate()
                        rate(scanner,output,input);
                        break;
                    }
                    case "5":{
                        // Perform AddTokens() (increase player's balance by specific tokens)
                        addTokens(scanner, output,input);
                        break;
                    }
                    case "6":{
                        // View player Balance
                        viewBalance(scanner,output,input);
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

    // Helping method to show all available games to Player
    private static void fetchAllAvailableGame(PrintWriter output, BufferedReader input) throws Exception {
        // send request to master
        output.println("FETCH_ALL_AVAILABLE_GAMES");

        List<SearchResult> allAvailableGames = readFetchAllAvailableGamesResults(input);

        if(allAvailableGames.isEmpty()){
            System.out.println("No available games yet!");
            return;
        }
        printAllAvailableGames(allAvailableGames);
    }

    // Search() method implementation
    private static void search(Scanner scanner, PrintWriter output, BufferedReader input) throws Exception {
        String playerId = readValidUsername(scanner, "Give PlayerId (e.g. user123): ");

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

        List<SearchResult> results = readAndStoreSearchResults(input);
        //clear lastSearchResults list
        lastSearchResults.clear();
        lastSearchResults.addAll(results);
        lastPlayerIdThatSearched = playerId;

        if(lastSearchResults.isEmpty()){
            System.out.println("No games found!");
            return;
        }

        System.out.println("\n--- SEARCH RESULTS ---");
        for(int i=0; i< lastSearchResults.size();i++){
            System.out.println((i+1)+". "+ lastSearchResults.get(i));
        }

        System.out.println();
        System.out.println("Do you want to play from these results?");
        System.out.println("1. Yes");
        System.out.println("2. No");
        String playChoice = scanner.nextLine().trim();

        switch (playChoice){
            case "1":{
                play(playerId, scanner,input, output);
                break;
            }
            case "2":{
                System.out.println("Continue with something else!");
                break;
            }
            default:
                System.out.println("Not Valid choice...");
                break;

        }

    }

    // play() method implementation
    private static void play(String playerId, Scanner scanner,BufferedReader input ,PrintWriter output) throws Exception{

        if(lastSearchResults.isEmpty()){
            System.out.println("You must search first! No saved results found!");
            return;
        }

        if(lastPlayerIdThatSearched == null || !lastPlayerIdThatSearched.equalsIgnoreCase(playerId)){
            System.out.println("You can only play using the latest search results of the same player!");
            return;
        }

        System.out.println("Select Game, from the last search results: ");
        for(int i=0; i< lastSearchResults.size(); i++){
            System.out.println((i+1)+". "+lastSearchResults.get(i));
        }

        int choice = readInt(scanner, "Select game number: ", 1, lastSearchResults.size());
        SearchResult selectedGame = lastSearchResults.get(choice - 1);


        System.out.println("Give Bet: ");
        String bet = scanner.nextLine().trim();

        if(bet.isBlank()){
            System.out.println("Bet is blank!");
            return;
        }

        // Structure the play request and send it to MasterServer
        String cmd = "PLAY " +playerId +"|"+ selectedGame.getGameName() + "|" +bet;
        output.println(cmd);

        // Receive the answer from MasterServer
        // Print it
        System.out.println("\n--- PLAY RESULT --- ");
        readMsgUntilEnd(input);
    }

    // Rate() method implementation
    // Let a user rate a game
    // DON'T let him rate it again
    // If user has already rated this game let him just update this review or delete it
    private static void rate(Scanner scanner, PrintWriter output, BufferedReader input) throws Exception{
        String playerId  = readValidUsername(scanner, "Give PlayerId (e.g. user123): ");

        System.out.println("Choose Game for review: ");
        String gameName = scanner.nextLine().trim();

        System.out.println("Give stars (1-5):");
        int stars = readInt(scanner, "Stars (1-5): ",1,5);

        String cmd = "RATE "+playerId+"|"+gameName+"|"+stars;
        output.println(cmd);

        System.out.println("Result: ");
        readMsgUntilEnd(input);
    }

    private static void addTokens(Scanner scanner, PrintWriter output, BufferedReader input) throws Exception {
        String userId = readValidUsername(scanner, "Give PlayerId (e.g. user123): ");

        System.out.println("Give token amount you want to add to your balance: ");
        BigDecimal tokens = readBigDecimal(scanner, "Tokens should be > 0");

        // Send request to Master Server
        String cmd = "ADD_BALANCE "+userId+"|"+tokens;
        output.println( cmd);

        // Read His response
        readMsgUntilEnd(input);
    }

    private static void viewBalance(Scanner scanner, PrintWriter output, BufferedReader input) throws Exception {
        String userId = readValidUsername(scanner, "Give PlayerId (e.g. user123): ");

        // Send request to MasterServer
        output.println("VIEW_BALANCE "+userId);

        // Read MasterServer's Response
        readMsgUntilEnd(input);
    }

    // ---------------------------------------------------------//
    // ---------------------------------------------------------//
    // ---------------------------------------------------------//
    // Reading Helping Methods
    // helper method for multilines responses that MasterServer sends
    private static int readInt(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String userInput = scanner.nextLine().trim();
            try {
                int givenNum = Integer.parseInt(userInput);
                if (givenNum < min || givenNum > max) {
                    System.out.println("Please enter a number in range " + min + ".." + max);
                    continue;
                }
                return givenNum;
            } catch (Exception e) {
                System.out.println("Invalid number value "+ e.getMessage());
            }
        }
    }

    private static BigDecimal readBigDecimal(Scanner scanner, String prompt){
        while(true){
            System.out.println(prompt);
            String userInput = scanner.nextLine().trim();
            try{
                BigDecimal tokens = new BigDecimal(userInput);
                if(tokens.compareTo(BigDecimal.ZERO) <=0){
                    System.out.println("Entered Tokens should be > 0 ");
                    continue;
                }
                return tokens;
            } catch (Exception e) {
                System.out.println("Invalid token value." +e.getMessage());
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

    private static void readMsgUntilEnd(BufferedReader input) throws Exception{
        String line;
        while((line = input.readLine())!=null){
            if(line.equals("END")) break;
            System.out.println(line);
        }
    }

    private static String readValidUsername(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String username = scanner.nextLine().trim();

            if (!username.isEmpty()) {
                return username;
            }

            System.out.println("Username is empty! Choose a valid username!");
        }
    }

    private static List<SearchResult> readAndStoreSearchResults(BufferedReader input) throws Exception {
        List<SearchResult> results = new ArrayList<>();

        String ln;
        while((ln = input.readLine())!=null){
            if(ln.equals("END")) break;

            ln = ln.trim();
            if(ln.isBlank()) continue;

            if(ln.startsWith("GAME|")){
                String[] parts = ln.split("\\|");
                if(parts.length == 10){
                    // correct line
                    SearchResult result = new SearchResult(
                            parts[1].trim(), // gameName
                            parts[2].trim(), // providerName
                            parts[3].trim(), // stars
                            parts[4].trim(), // noOfVotes
                            parts[5].trim(), // betCategory
                            parts[6].trim(), // risk
                            parts[7].trim(), // minBet
                            parts[8].trim(),  // maxBet
                            parts[9].trim() // jackpot
                    );
                    results.add(result);
                }else{
                    // wrong line error msg => print it
                    System.out.println("Invalid GAME line: "+ln);
                }
            }else{
                // print error / timeouts from masterserver
                System.out.println(ln);
            }
        }
        return results;
    }

    private static List<SearchResult> readFetchAllAvailableGamesResults(BufferedReader input) throws  Exception{
        List<SearchResult> allAvailableGamesResults = new ArrayList<>();
        boolean noGamesMsgAppeared = false;

        String line;
        while((line = input.readLine()) !=null){
            if(line.equals("END")) break;
            line = line.trim();
            if(line.isBlank()) continue;
            if(line.startsWith("GAME|")){
                SearchResult gameSearchRes = parseGameLineRes(line);
                if(gameSearchRes !=null){
                    allAvailableGamesResults.add(gameSearchRes);
                }
                continue;
            }

            if (line.equalsIgnoreCase("There are no available games yet!")
                    || line.equalsIgnoreCase("No available games yet!")) {
                noGamesMsgAppeared =true;
               continue; //consume till END
            }

            // any other non-GAME| line,
            System.out.println(line);
        }
        if (noGamesMsgAppeared && allAvailableGamesResults.isEmpty()) {
            return new ArrayList<>();
        }

        return allAvailableGamesResults;
    }

    private static SearchResult parseGameLineRes(String line){
        String[] parts = line.split("\\|");
        if(parts.length != 10 ){
            System.out.println("Invalid game line format: +"+line);
            return null;
        }

        return new SearchResult(
                parts[1].trim(), // gameName
                parts[2].trim(), // providerName
                parts[3].trim(), // stars
                parts[4].trim(), // noOfVotes
                parts[5].trim(), // betCategory
                parts[6].trim(), // risk
                parts[7].trim(), // minBet
                parts[8].trim(), // maxBet
                parts[9].trim()  // jackpot
        );
    }

    private static void printAllAvailableGames(List<SearchResult> games){
        System.out.println("\n--- All available games ---");
        for (SearchResult game : games) {
            System.out.println(game.toString());
        }
        System.out.println("------------------------------");
    }

}

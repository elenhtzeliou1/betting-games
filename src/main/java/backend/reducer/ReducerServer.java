package backend.reducer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/*
 * ReducerServer
 * -------------
 * Multithreaded TCP server that acts as the "Reduce" side of our MapReduce pipeline.
 *
 * Design:
 *   - Workers push MAP results to Reducer via TCP.
 *   - Reducer merges partial results in-memory.
 *   - When received == expectedN, Reducer PUSHES final result to Master callback port (5001).
 *
 * Synchronization: only synchronized / wait / notify (no java.util.concurrent).
 */
public class ReducerServer {

    // Where reducer will push back its final results
    private static String masterHost;
    private static int masterCallBackPort;

    // In-memory jobs
    private static final Object JOBS_LOCK = new Object();
    private static final Map<String, SearchJob> searchJobs = new HashMap<>();
    private static final Map<String, ProviderProfitJob> providerJobs = new HashMap<>();
    private static final Map<String, PlayerProfitJob> playerJobs = new HashMap<>();

    // -----------------------------
    // Server starting
    // -----------------------------

    /*
    * Starts the Reducer TCP server
    *
    * if no arg provider, by default the port is 7000
    * */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java backend.reducer.ReducerServer <reducerPort> <masterHost> <masterCallbackPort>");
            System.out.println("Example: java backend.reducer.ReducerServer 7000 192.168.1.10 5001");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
            masterHost = args[1].trim();
            masterCallbackPort = Integer.parseInt(args[2]);
        } catch (Exception e) {
            System.out.println("[ReducerServer] Invalid startup arguments: " + e.getMessage());
            return;
        }

        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("[ReducerServer] Listening on port: "+port);
            System.out.println("[ReducerServer] Master callback target: " + masterHost + ":" + masterCallbackPort);

            // Accept connections forever.
            // Each connection is handle in its own thread (multi-threaded reducer)
            while(true){
                // Reducer is listening and spawns threads
                // can accept multiple worker connections at the same time
                // each workers
                Socket socket = serverSocket.accept();
                new Thread(() -> handleWorkerConnection(socket)).start();
            }
        }catch (Exception e){
            System.out.println("[ReducerServer] Error: "+ e.getMessage());
        }

    }


    /*
     * Handles one TCP connection (line-based protocol).
     * Currently, supports:
     *   MAP_SEARCH <jobId> <expectedN>
     *     GAME|...
     *     GAME|...
     *     END
     */
    private static void handleWorkerConnection(Socket socket){
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true))
        {
            String firstLine = in.readLine();
            if(firstLine==null) return;
            firstLine = firstLine.trim();

            if(firstLine.startsWith("MAP_SEARCH ")){
                handleMapSearch(firstLine,in,out);
                return;
            }
            else if(firstLine.startsWith("MAP_PROVIDER_PROFIT ")){
                handleMapProviderProfit(firstLine,in,out);
                return;
            }
            else if(firstLine.startsWith("MAP_PLAYER_PROFIT ")){
                handleMapPlayerProfit(firstLine,in,out);
                return;

            }
            out.println("ERROR Unknown reducer command: "+ firstLine);
            out.println("END");

        } catch (Exception e) {
            System.out.println("[ReducerServer] Connection error: " + e.getMessage());
        }
    }

    /*
    * Receives MAP output for a SEARCH job from ONE worker
    *
    * Incoming format:
    * MAP_SEARCH <jobId> <expectedN>
    * GAME|...
    * GAME|...
    * END
    *
    * Working Process (STEPS):
    *   1) Parse jobId && expectedN
    *   2) Read all GAME lines until END (this is worker's partial result)
    *   3) Find/Create the SearchJob in memory
    *   4) Merge partial results into the job (reduce by gameName)
    *   5) ACK back to the worker
    * */
    private static void handleMapSearch(String firstLine, BufferedReader in, PrintWriter out) throws IOException {
        // example header: "MAP_SEARCH 3f...-jobId 3"
        String[] header = firstLine.split("\\s+");
        if(header.length !=3){
            out.println("ERROR bad MAP_SEARCH header:Expected: MAP_SEARCH <jobId> <expectedN>");
            out.println("END");
            return;
        }
        String jobId =header[1].trim();
        if (jobId.isBlank()) {
            out.println("ERROR MAP_SEARCH jobId is empty");
            out.println("END");
            return;
        }

        int expectedN;
        try {
            expectedN = Integer.parseInt(header[2].trim());
        } catch (NumberFormatException e) {
            out.println("ERROR MAP_SEARCH expectedN must be an integer");
            out.println("END");
            return;
        }

        if (expectedN <= 0) {
            out.println("ERROR MAP_SEARCH expectedN must be > 0");
            out.println("END");
            return;
        }

        // Read worker's partial lines until END
        List<String> gameLines = new ArrayList<>();
        String line;
        while((line = in.readLine())!=null){
            line = line.trim();
            if("END".equals(line)) break;
            if(!line.isEmpty()) gameLines.add(line);
        }
        // Find or create job stat
        SearchJob job;
        synchronized (JOBS_LOCK){
            job = searchJobs.get(jobId);
            if(job ==null){
                job = new SearchJob(jobId,expectedN, masterHost, masterCallBackPort);
                searchJobs.put(jobId,job);

                // Notify any waiting GET_SEARCH threads that the job now exists.
                JOBS_LOCK.notifyAll();
            }
        }

        // Merge this worker's partial output into the job state.
        // SearchJob is synchronized internally to protect its counters + merged map.
        job.addPartialResults(gameLines);

        // Tell worker we got it
        out.println("ACK");
    }


    private static void   handleMapProviderProfit(String firstLine, BufferedReader in, PrintWriter out) throws IOException {
        // Header we expect:
        // MAP_PROVIDER_PROFIT <jobId> <providerName> <expectedN>

        String[] header = firstLine.trim().split("\\s+");
        if (header.length != 4) {
            out.println("ERROR bad MAP_PROVIDER_PROFIT header. Expected: MAP_PROVIDER_PROFIT <jobId> <providerName> <expectedN>");
            out.println("END");
            return;
        }

        String jobId = header[1].trim();
        String providerName = header[2].trim();

        if (jobId.isBlank()) {
            out.println("ERROR MAP_PROVIDER_PROFIT jobId is empty");
            out.println("END");
            return;
        }

        if (providerName.isBlank()) {
            out.println("ERROR MAP_PROVIDER_PROFIT providerName is empty");
            out.println("END");
            return;
        }

        int expectedN;
        try {
            expectedN = Integer.parseInt(header[3].trim());
        } catch (NumberFormatException e) {
            out.println("ERROR MAP_PROVIDER_PROFIT expectedN must be an integer");
            out.println("END");
            return;
        }

        if (expectedN <= 0) {
            out.println("ERROR MAP_PROVIDER_PROFIT expectedN must be > 0");
            out.println("END");
            return;
        }

        // Read worker partial lines until END
        List<String> partialLines = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if ("END".equals(line)) {
                break;
            }
            if (!line.isEmpty()) {
                partialLines.add(line);
            }
        }

        ProviderProfitJob job;
        synchronized (JOBS_LOCK) {
            job = providerJobs.get(jobId);
            if (job == null) {
                job = new ProviderProfitJob(jobId, providerName, expectedN, masterHost, masterCallBackPort);
                providerJobs.put(jobId, job);

                // Notify any waiting threads that this job now exists
                JOBS_LOCK.notifyAll();
            }
        }

        // Merge this worker's partial result into reducer state
        job.addPartialResults(partialLines);

        // Acknowledge worker
        out.println("ACK");
    }


    private static void handleMapPlayerProfit(String firstLine,
                                              BufferedReader in, PrintWriter out) throws IOException{

        // EXPECTED: MAP_PLAYER_PROFIT <jobId> <playerId> <expectedN>

        String[] header = firstLine.trim().split("\\s+");
        if (header.length != 4) {
            out.println("ERROR bad MAP_PLAYER_PROFIT header. Expected: MAP_PLAYER_PROFIT <jobId> <playerId> <expectedN>");
            out.println("END");
            return;
        }

        String jobId = header[1].trim();
        String playerId = header[2].trim();

        if (jobId.isBlank()) {
            out.println("ERROR MAP_PLAYER_PROFIT jobId is empty");
            out.println("END");
            return;
        }

        if (playerId.isBlank()) {
            out.println("ERROR MAP_PLAYER_PROFIT playerId is empty");
            out.println("END");
            return;
        }

        int expectedN;
        try {
            expectedN = Integer.parseInt(header[3].trim());
        } catch (NumberFormatException e) {
            out.println("ERROR MAP_PLAYER_PROFIT expectedN must be an integer");
            out.println("END");
            return;
        }

        if (expectedN <= 0) {
            out.println("ERROR MAP_PLAYER_PROFIT expectedN must be > 0");
            out.println("END");
            return;
        }

        List<String> partialLines = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if ("END".equals(line)) break;
            if (!line.isEmpty()) partialLines.add(line);
        }

        PlayerProfitJob job;
        synchronized (JOBS_LOCK) {
            job = playerJobs.get(jobId);
            if (job == null) {
                job = new PlayerProfitJob(jobId, playerId, expectedN, masterHost, masterCallBackPort);
                playerJobs.put(jobId, job);
                JOBS_LOCK.notifyAll();
            }
        }

        job.addPartialResults(partialLines);
        out.println("ACK");
    }
}

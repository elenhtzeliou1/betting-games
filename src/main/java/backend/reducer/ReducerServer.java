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
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_CALLBACK_PORT = 5001;

    // In-memory jobs
    private static final Object JOBS_LOCK = new Object();
    private static final Map<String, SearchJob> searchJobs = new HashMap<>();
    private static final Map<String, ProviderProfitJob> providerJobs = new HashMap<>();

    // -----------------------------
    // Server starting
    // -----------------------------

    /*
    * Starts the Reducer TCP server
    *
    * if no arg provider, by default the port is 7000
    * */
    public static void main(String[] args) {
        int port;
        if(args.length ==1){
            port = Integer.parseInt(args[0]);
        }else{
            port = 7000;
        }

        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("[ReducerServer] Listening on port: "+port);

            // Accept connections forever.
            // Each connection is handle in its own thread (multi-threaded reducer)
            while(true){
                // Reducer is listening and spawns threads
                // can accept multiple worker connections at the same time
                // each workers
                Socket socket = serverSocket.accept();
                new Thread(() -> handleConnection(socket)).start();
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
    private static void handleConnection(Socket socket){
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
            //
            //
            //
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
        String jobId =header[1];
        int expectedN = Integer.parseInt(header[2]);


        // Read worker's partial lines until END
        List<String> gameLines = new ArrayList<>();
        String line;
        while((line = in.readLine())!=null){
            if("END".equals(line)) break;
            line = line.trim();
            if(!line.isEmpty()) gameLines.add(line);
        }
        // Find or create job stat
        SearchJob job;
        synchronized (JOBS_LOCK){
            job = searchJobs.get(jobId);
            if(job==null){
                job = new SearchJob(jobId,expectedN, MASTER_HOST, MASTER_CALLBACK_PORT);
                searchJobs.put(jobId,job);

                // Notify any waiting GET_SEARCH threads that the job now exists.
                JOBS_LOCK.notifyAll();
            }
        }

        // Merge this worker's partial output into the job state.
        // SearchJob is synchronized internally to protect its counters + merged map.
        job.addPartial(gameLines);

        // Tell worker we got it
        out.println("ACK");
    }




}

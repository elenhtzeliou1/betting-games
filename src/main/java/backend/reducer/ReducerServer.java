package backend.reducer;

import javax.print.attribute.standard.JobState;
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
   // private static final Map<String, ProviderProfitJob> providerJobs = new HashMap<>();

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
                // can accept multiple worker connections at the same thime
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
                job = new SearchJob(jobId,expectedN);
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
        out.println("END");
    }

    /*
     * Job state for one SEARCH MapReduce job.
     * Reduce logic:
     *  - Deduplicate games by gameName (key2 = normalized gameName)
     *  - Count worker submissions (received++)
     *  - When received == expectedN => push final merged list to Master callback port
     */
    private static class SearchJob{
        private final String jobId;
        private final int expectedN;
        private int received = 0;

        // key2 = normalized gameName, value2 = full GAME|.. line
        private final LinkedHashMap<String, String> uniqueByGame = new LinkedHashMap<>();

        // to ensure we push only once
        private boolean pushed = false;

        SearchJob(String jobId,int expectedN){
            this.jobId = jobId;
            this.expectedN = expectedN;
        }

        /*
         * Reduce merge:
         * - Deduplicate by gameName
         * - Count worker submissions
         * - When complete -> push to Master callback port
         */
        synchronized void addPartial(List<String> partialLines) {
            for (String ln : partialLines) {
                if (!ln.startsWith("GAME|")) continue;
                String[] p = ln.split("\\|");
                if (p.length < 2) continue;

                String gameKey = p[1].trim().toLowerCase();
                uniqueByGame.putIfAbsent(gameKey, ln);
            }

            received++;

            // If all workers have submitted => finalize and push exactly once
            if (received >= expectedN && !pushed) {
                pushed = true;

                // Snapshot (copy) final result while synchronized
                List<String> finalList = buildFinalListLocked();

                // Push in a new thread so reducer handler threads remain responsive
                new Thread(() -> pushFinalSearchToMaster(jobId, finalList)).start();
            }

            notifyAll();
        }

        // Build sorted snapshot of final merged list
        private List<String> buildFinalListLocked() {
            List<String> list = new ArrayList<>(uniqueByGame.values());

            // Optional sorting: stars DESC, then name ASC
            list.sort((a, b) -> {
                int sa = safeStars(a);
                int sb = safeStars(b);
                if (sa != sb) return Integer.compare(sb, sa);
                return safeName(a).compareToIgnoreCase(safeName(b));
            });

            return list;
        }

        // Extract "gameName" key from "GAME|gameName|..."
        private String extractGameKey(String line) {
            if (line == null) return null;
            if (!line.startsWith("GAME|")) return null;
            String[] p = line.split("\\|");
            if (p.length < 2) return null;
            return p[1].trim().toLowerCase();
        }

        // Parse stars from "GAME|name|provider|stars|..."
        private int safeStars(String line) {
            try {
                String[] p = line.split("\\|");
                return Integer.parseInt(p[3].trim());
            } catch (Exception e) {
                return 0;
            }
        }

        // Parse name from "GAME|name|..."
        private String safeName(String line) {
            try {
                String[] p = line.split("\\|");
                return p[1].trim();
            } catch (Exception e) {
                return "";
            }
        }
    }

    /*
     * PUSH final SEARCH result to MasterServer callback server (port 5001).
     *
     * used protocol:
     *   REDUCE_SEARCH_RESULT <jobId>
     *   GAME|...
     *   GAME|...
     *   END
     */
    private static void pushFinalSearchToMaster(String jobId, List<String> lines) {
        try (Socket s = new Socket(MASTER_HOST, MASTER_CALLBACK_PORT);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.println("REDUCE_SEARCH_RESULT " + jobId);
            for (String ln : lines) out.println(ln);
            out.println("END");

        } catch (Exception e) {
            System.out.println("[ReducerServer] push to Master failed: " + e.getMessage());
        }
    }





}

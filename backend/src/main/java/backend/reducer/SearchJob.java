package backend.reducer;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/*
 * Job state for one SEARCH MapReduce job.
 * Reduce logic:
 *  - Deduplicate games by gameName (key2 = normalized gameName)
 *  - Count worker submissions (received++)
 *  - When received == expectedN => push final merged list to Master callback port
 */
public class SearchJob extends Job{

    // Collected game lines — no deduplication needed because the Worker layer
    // guarantees each game is reported by exactly one worker.
    private final List<String> gameLines = new ArrayList<>();

    public SearchJob(String jobId, int expectedN, String masterHost, int masterCallbackPort){
       super(jobId,expectedN,masterHost,masterCallbackPort);
    }

    /*
     * Reduce merge:
     * - Accumulate GAME lines
     * - Count worker submissions
     * - When complete -> push to Master callback port
     */
    @Override
    public synchronized void addPartialResults(List<String> partialLines) {
        for (String ln : partialLines) {
            if (ln.startsWith("GAME|")) {
                gameLines.add(ln);
            }
        }

        increaseReceivedWorkers();

        if (isJobCompleteAndNotPushed()){
            markComplete();
            List<String> finalList = buildFinalListLocked();
            new Thread(()-> pushFinalSearchResultToMaster(finalList)).start();
        }
        notifyAll();
    }

    // Build sorted snapshot of final merged list
    private List<String> buildFinalListLocked() {
        List<String> list = new ArrayList<>(gameLines);

        // Sorting: stars DESC, then name ASC
        list.sort((a, b) -> {
            double sa = parseStars(a);
            double sb = parseStars(b);
            if (sa != sb) return Double.compare(sb, sa);
            return parseName(a).compareToIgnoreCase(parseName(b));
        });

        return list;
    }

    // Parse stars from "GAME|name|provider|stars|..."
    private double parseStars(String line) {
        try {
            String[] p = line.split("\\|");
            return Double.parseDouble(p[3].trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    // Parse name from "GAME|name|..."
    private String parseName(String line) {
        try {
            String[] p = line.split("\\|");
            return p[1].trim();
        } catch (Exception e) {
            return "";
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
    private void pushFinalSearchResultToMaster( List<String> lines) {
        try (Socket s = new Socket(masterHost, masterCallbackPort);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.println("REDUCE_SEARCH_RESULT " + jobId);
            for (String ln : lines) out.println(ln);
            out.println("END");

        } catch (Exception e) {
            System.out.println("[ReducerServer] push to Master failed: " + e.getMessage());
        }
    }

}
package backend.reducer;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserRatingsJob extends Job {

    private final String playerId;
    // gameName (lowercase) -> stars  (deduplicates replicas naturally)
    private final LinkedHashMap<String, Integer> ratings = new LinkedHashMap<>();

    public UserRatingsJob(String jobId, String playerId, int expectedN,
                          String masterHost, int masterCallbackPort) {
        super(jobId, expectedN, masterHost, masterCallbackPort);
        this.playerId = playerId;
    }

    @Override
    public synchronized void addPartialResults(List<String> partialLines) {
        for (String ln : partialLines) {
            if (ln == null) continue;
            ln = ln.trim();
            if (ln.isBlank() || ln.equals("END")) continue;

            // Expected: RATING|gameName|stars
            if (!ln.startsWith("RATING|")) continue;
            String[] parts = ln.split("\\|");
            if (parts.length != 3) continue;

            String gameName = parts[1].trim().toLowerCase();
            int stars;
            try {
                stars = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            // First writer wins — primary worker reports first, replica is a no-op
            ratings.putIfAbsent(gameName, stars);
        }

        increaseReceivedWorkers();
        if (isJobCompleteAndNotPushed()) {
            markComplete();
            List<String> finalLines = buildFinalLines();
            new Thread(() -> pushToMaster(finalLines)).start();
        }
        notifyAll();
    }

    private List<String> buildFinalLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : ratings.entrySet()) {
            lines.add("RATING|" + e.getKey() + "|" + e.getValue());
        }
        return lines;
    }

    private void pushToMaster(List<String> lines) {
        try (Socket s = new Socket(masterHost, masterCallbackPort);
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.println("REDUCE_USER_RATINGS_RESULT " + jobId + "|" + playerId);
            for (String ln : lines) out.println(ln);
            out.println("END");

        } catch (Exception e) {
            System.out.println("[ReducerServer] Push user ratings to Master failed: " + e.getMessage());
        }
    }
}
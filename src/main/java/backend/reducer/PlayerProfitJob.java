package backend.reducer;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class PlayerProfitJob extends Job{

    private final String playerId;
    private BigDecimal totalPlayerProfitLoss = BigDecimal.ZERO;


    public PlayerProfitJob(String jobId, String playerId, int expectedN, String masterHost, int masterCallbackPort){
        super(jobId,expectedN,masterHost,masterCallbackPort);
        this.playerId = playerId;
    }

    @Override
    public synchronized void addPartialResults(List<String> partialLines){
        for(String ln: partialLines){
            if(ln==null) continue;

            ln =ln.trim();
            if(ln.isBlank())continue;
            if (ln.equals("END")) continue;

            // reminder, worker sends: playerDelta
            try{
                BigDecimal playerDelta = new BigDecimal(ln);
                totalPlayerProfitLoss = totalPlayerProfitLoss.add(playerDelta);
            }catch (NumberFormatException e){
                // ignore wrong lines
                continue;
            }

        }
        increaseReceivedWorkers();
        if(isJobCompleteAndNotPushed()){
            markComplete();
            List<String> finalLines = buildFinalLinesLocked();
            new Thread(()-> pushFinalProviderProfitResultsToMaster(finalLines)).start();
        }
        notifyAll();
    }
    private List<String> buildFinalLinesLocked() {
        List<String> lines = new ArrayList<>();
        lines.add("TOTAL_PLAYER_PROFIT|" + playerId + "|" + totalPlayerProfitLoss);
        return lines;
    }


    private void pushFinalProviderProfitResultsToMaster(List<String> lines) {
        try (Socket s = new Socket(masterHost, masterCallbackPort);
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.println("REDUCE_PLAYER_PROFIT_RESULT " + jobId + "|" + playerId);
            for (String ln : lines) {
                out.println(ln);
            }
            out.println("END");

        } catch (Exception e) {
            System.out.println("[ReducerServer] Push provider's profit to Master failed: " + e.getMessage());
        }
    }


}

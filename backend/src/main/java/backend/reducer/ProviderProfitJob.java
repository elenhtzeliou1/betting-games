package backend.reducer;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProviderProfitJob extends Job{

    private final String providerName;
    private final LinkedHashMap<String, BigDecimal> perGameProfitLoss = new LinkedHashMap<>();


    public ProviderProfitJob(String jobId, String providerName, int expectedN, String masterHost, int masterCallbackPort ){
        super(jobId,expectedN,masterHost,masterCallbackPort);
        this.providerName = providerName;
    }

    @Override
    public synchronized void addPartialResults(List<String> partialLines){

        for(String ln : partialLines){
           if(ln==null) continue;

           ln =ln.trim();
           if(ln.isBlank())continue;
           if (ln.equals("END")) continue;

           // reminder, worker sends: gameName|profitLoss
            String[] parts = ln.split("\\|");
            if(parts.length!=2)continue;

            String gameKey = parts[0].trim().toLowerCase();
            if(gameKey.isBlank()) continue;
            BigDecimal profitLoss;

            try{
                profitLoss = new BigDecimal(parts[1].trim());
            }catch (NumberFormatException e){
                continue;
            }

            perGameProfitLoss.merge(gameKey,profitLoss, BigDecimal::add);

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
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(perGameProfitLoss.entrySet());

        // Sort by profit DESC, then game name ASC
        entries.sort((a, b) -> {
            int compute = b.getValue().compareTo(a.getValue());
            if (compute != 0) return compute;
            return a.getKey().compareToIgnoreCase(b.getKey());
        });

        List<String> lines = new ArrayList<>();
        BigDecimal totalProviderProfit = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> e : entries) {
            String gameName = e.getKey();
            BigDecimal profitLoss = e.getValue();
            totalProviderProfit = totalProviderProfit.add(profitLoss);

            lines.add("GAME_PROFIT|" + gameName + "|" + profitLoss);
        }

        lines.add("TOTAL_PROVIDER_PROFIT|" + providerName + "|" + totalProviderProfit);
        return lines;
    }

    private void pushFinalProviderProfitResultsToMaster(List<String> lines) {
        try (Socket s = new Socket(masterHost, masterCallbackPort);
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.println("REDUCE_PROVIDER_PROFIT_RESULT " + jobId + "|" + providerName);
            for (String ln : lines) {
                out.println(ln);
            }
            out.println("END");

        } catch (Exception e) {
            System.out.println("[ReducerServer] Push provider's profit to Master failed: " + e.getMessage());
        }
    }


}

package backend.reducer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

public class ProviderProfitJob extends Job{

    private final String providerName;
    private final LinkedHashMap<String, BigDecimal> perGame = new LinkedHashMap<>();


    public ProviderProfitJob(String jobId, String providerName, int expectedN, String masterHost, int masterCallbackPort ){
        super(jobId,expectedN,masterHost,masterCallbackPort);
        this.providerName = providerName;
    }

    @Override
    public synchronized void addPartialResults(List<String> partialLines){

        for(String ln : partialLines){
            String[] parts = ln.split("\\t");
            if(parts.length !=2)continue;


        }

    }

}

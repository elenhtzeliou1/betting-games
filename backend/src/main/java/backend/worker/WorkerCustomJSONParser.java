package backend.worker;

import backend.common.Game;
import backend.common.RiskLevel;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.math.BigDecimal;

public class WorkerCustomJSONParser {

    public static Game parseGameJSON(String json) throws Exception{
        JSONParser parser = new JSONParser();
        JSONObject obj = (JSONObject) parser.parse(json);

        String gameName = readStringValue(obj, "GameName");
        String providerName = readStringValue(obj,"ProviderName");

        double stars = readDoubleValue(obj, "Stars");
        int noOfVotes = readIntValue(obj, "NoOfVotes");

        String gameLogo =  readStringValue(obj, "GameLogo");
        BigDecimal minBet = readBigDecimalValue(obj,"MinBet");
        BigDecimal maxBet = readBigDecimalValue(obj,"MaxBet");

        String riskLevelStr =  readStringValue(obj, "RiskLevel");
        String hashKey =  readStringValue(obj, "HashKey");

        // basic validations
        if(gameName.isBlank()) throw new IllegalArgumentException("GameName is missing!");
        if(providerName.isBlank()) throw new IllegalArgumentException("ProviderName is missing!");
        if(hashKey.isBlank()) throw new IllegalArgumentException("HashKey is missing!");
        if(gameLogo.isBlank()) throw new IllegalArgumentException("GameLogo is missing!");
        if(stars < 0) throw new IllegalArgumentException("Stars must be >=0");
        if(minBet.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("MinBet must be >0!");
        if(maxBet.compareTo(BigDecimal.ZERO)<=0 ) throw new IllegalArgumentException("MaxBet must be >0!");
        if(minBet.compareTo(maxBet) > 0) throw new IllegalArgumentException("MaxBet should be greater than MinBet");

        RiskLevel riskLevel;

        try{
            riskLevel = RiskLevel.parse(riskLevelStr);
        }catch (Exception e){
            throw new IllegalArgumentException("Invalid Risk level. Allowed values: low || medium || high");
        }


        return new Game(gameName,providerName,stars,noOfVotes,gameLogo
        ,minBet,maxBet,riskLevel,hashKey);
    }



    private static String readStringValue(JSONObject obj, String key) {
        Object raw = obj.get(key);
        if (raw == null) return  "";
        return raw.toString();
    }

    private static double readDoubleValue(JSONObject obj, String key) {
        Object raw = obj.get(key);
        if (raw == null) return 0.0;

        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        // if it's a String with something like "37.99"
        return Double.parseDouble(raw.toString());
    }

    private static int readIntValue(JSONObject obj, String key) {
        Object raw = obj.get(key);
        if (raw == null) return 0;

        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        return Integer.parseInt(raw.toString());
    }

    private static BigDecimal readBigDecimalValue(JSONObject obj, String key) {
        Object raw = obj.get(key);
        if (raw == null) {
            return BigDecimal.ZERO;
        }

        if (raw instanceof Number) {
            return new BigDecimal(raw.toString());
        }

        return new BigDecimal(raw.toString().trim());
    }

}

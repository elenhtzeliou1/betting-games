package backend.worker;

import backend.common.Game;
import backend.common.RiskLevel;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class WorkerCustomJSONParser {

    public static Game parseGameJSON(String json) throws Exception{
        JSONParser parser = new JSONParser();
        JSONObject obj = (JSONObject) parser.parse(json);

        String gameName = getString(obj, "GameName");
        String providerName = getString(obj,"ProviderName");

        int stars = getInt(obj, "Stars");
        int noOfVotes = getInt(obj, "NoOfVotes");

        String gameLogo = getString(obj, "GameLogo");
        double minBet = getDouble(obj,"MinBet");
        double maxBet = getDouble(obj,"MaxBet");

        String riskLevelStr = getString(obj, "RiskLevel");
        String hashKey = getString(obj, "HashKey");

        //basic validations
        if(gameName.isBlank()) throw new IllegalArgumentException("GameName is missing!");
        if(providerName.isBlank()) throw new IllegalArgumentException("ProviderName is missing!");
        if (hashKey.isBlank()) throw new IllegalArgumentException("HashKey is missing!");
        if(minBet<0) throw new IllegalArgumentException("MinBet must be >0!");
        if(maxBet<0) throw new IllegalArgumentException("MaxBet must be >0!");
        if(minBet>maxBet) throw new IllegalArgumentException("MaxBet should be greater than MinBet");

        RiskLevel riskLevel;

        try{
            riskLevel = RiskLevel.parse(riskLevelStr);
        }catch (Exception e){
            throw new IllegalArgumentException("Invalid Risk level. Allowed: low || medium || high");
        }


        return new Game(gameName,providerName,stars,noOfVotes,gameLogo
        ,minBet,maxBet,riskLevel,hashKey);
    }



    private static String getString(JSONObject obj, String key) {
        Object v = obj.get(key);
        return (v == null) ? "" : v.toString();
    }

    private static double getDouble(JSONObject obj, String key) {
        Object v = obj.get(key);
        if (v == null) return 0.0;

        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        // if it's a String with something like "37.99"
        return Double.parseDouble(v.toString());
    }

    private static int getInt(JSONObject obj, String key) {
        Object v = obj.get(key);
        if (v == null) return 0;

        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return Integer.parseInt(v.toString());
    }

}

package backend.worker;

import java.math.BigDecimal;


// Player's Payout Calculator used by Worker's
public final class PayoutCalculator {

    private static final BigDecimal[] LOW ={
        new BigDecimal("0.0"), new BigDecimal("0.0"),new BigDecimal("0.0"),new BigDecimal("0.1"),new BigDecimal("0.5"),new BigDecimal("1.0"),new BigDecimal("1.1"),new BigDecimal("1.3"),new BigDecimal("2.0"),new BigDecimal("2.5")
    };

    private static final BigDecimal[] MEDIUM = {
            new BigDecimal("0.0"), new BigDecimal("0.0"),new BigDecimal("0.0"),new BigDecimal("0.0"),new BigDecimal("0.0"),new BigDecimal("0.5"),new BigDecimal("1.0"),new BigDecimal("1.5"),new BigDecimal("2.5"),new BigDecimal("3.5")
    };
    private static final BigDecimal[] HIGH = {
            new BigDecimal("0.0"), new BigDecimal("0.0"),new BigDecimal("0.0"),new BigDecimal("0.0"),new BigDecimal("0.0"),new BigDecimal("0.0"),new BigDecimal("0.0"),new BigDecimal("1.0"),new BigDecimal("2.0"),new BigDecimal("6.5")
    };

    public static BigDecimal calculatePayout(String riskLevel, BigDecimal bet, int randomNumber){
        int result = Math.floorMod(randomNumber,100);

        if(result == 0){
            BigDecimal jackpotMultiplierValue = jackpotCalculator(riskLevel);
            BigDecimal wins = bet.multiply(jackpotMultiplierValue);
            return wins;
        }
        int k = Math.floorMod(randomNumber,10);
        BigDecimal simpleBetMultiplier = getTableForRiskLevel(riskLevel)[k];
        BigDecimal wins = bet.multiply(simpleBetMultiplier);
        return wins;
    }

    private static BigDecimal jackpotCalculator(String riskLevel){
        riskLevel = riskLevel.toLowerCase();
        BigDecimal jackpot;
        return switch (riskLevel) {
            case "low" -> {
                jackpot = new BigDecimal("10");
                yield jackpot;
            }
            case "medium" -> {
                jackpot = new BigDecimal("20");
                yield jackpot;
            }
            case "high" -> {
                jackpot = new BigDecimal("40");
                yield jackpot;
            }
            default -> throw new IllegalArgumentException("Unknown riskLevel: " + riskLevel);
        };
    }


    private static BigDecimal[] getTableForRiskLevel(String riskLevel){
        riskLevel = riskLevel.toLowerCase();
        return switch (riskLevel) {
            case "low" -> LOW;
            case "medium" -> MEDIUM;
            case "high" -> HIGH;
            default -> throw new IllegalArgumentException("Unknown riskLevel: " + riskLevel);
        };
    }

}

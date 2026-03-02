package backend.common;

public enum RiskLevel {

    LOW,MEDIUM,HIGH;

    public static RiskLevel parse(String input){
        if (input == null) throw new IllegalArgumentException("risk is null!");
        return RiskLevel.valueOf(input.trim().toUpperCase()
        );
    }


}

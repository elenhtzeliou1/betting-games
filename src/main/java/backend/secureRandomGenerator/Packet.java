package backend.secureRandomGenerator;

public class Packet {

    private final int number;
    private final String hashKey;

    public Packet(int number, String hashKey){
        this.number = number;
        this.hashKey = hashKey;
    }

    // Getters
    public int getNumber(){
        return this.number;
    }
    public String getHashKey(){
        return this.hashKey;
    }

}

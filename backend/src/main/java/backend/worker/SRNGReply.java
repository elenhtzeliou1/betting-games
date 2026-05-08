package backend.worker;

// Helping class for constructing SRNG Reply inside WorkerServer
public class SRNGReply {
    private final int number;
    private final String hash;

    public SRNGReply(int number, String hash){
        this.number = number;
        this.hash=hash;
    }

    // Getters
    public int getNumber(){
        return this.number;
    }
    public String getHash(){
        return this.hash;
    }
}

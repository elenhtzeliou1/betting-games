package backend.worker;

public class Worker {

    private final String host;
    private final int port;

    public Worker(String host, int port){
        this.host = host;
        this.port = port;
    }


    @Override
    public String toString(){return this.host+ ":"+this.port;}

    //Getters
    public String getHost(){
        return this.host;
    }
    public int getPort(){
        return this.port;
    }

}

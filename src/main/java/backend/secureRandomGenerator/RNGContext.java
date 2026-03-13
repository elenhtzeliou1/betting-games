package backend.secureRandomGenerator;

public class RNGContext {
    private final String secret;
    private final Buffer buffer;
    private final Producer producer;
    private final Thread producerThread;

    public RNGContext(String gameName ,String secret, int bufferCapacity){
        this.secret =secret;
        this.buffer = new Buffer(bufferCapacity);
        this.producer  = new Producer(buffer);
        this.producerThread = new Thread(producer, "producer-"+gameName );
        this.producerThread.start();
    }

    public String getSecret(){
        return this.secret;
    }

    public int getNUmber() throws InterruptedException{
        return buffer.getNumber();
    }
    public void stop(){
        producer.stopRunning();
        producerThread.interrupt();
    }
}

package backend.secureRandomGenerator;

// Queue that container only Random Number Integers
public class Buffer {

    private final int[] integerQueue;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public Buffer(int capacity){
        if(capacity<=0) throw new IllegalArgumentException("Buffer Capacity should be > 0.");

        this.integerQueue = new int[capacity];
    }


    public synchronized void addNumber(int number) throws InterruptedException{
        while (integerQueue.length == size){
            // Buffer full
            // cannot add any other number now
            // tell them to wait
            wait();
        }
        integerQueue[tail] = number;
        // kyklikaaa
        tail = (tail+1) % integerQueue.length;
        size++;
        notifyAll();
    }

    public synchronized int getNumber() throws InterruptedException{
        while (size==0){
            // Buffer empty nothing to get wait
            wait();
        }
        int number = integerQueue[head];
        head = (head+1) %  integerQueue.length;
        size--;
        notifyAll();
        return number;
    }




    // Worker Requests and get the first Random number of the Queue
}

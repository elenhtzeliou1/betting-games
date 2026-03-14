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

            // -------

            // [DEBUB ONLY]
            System.out.println("[BUFFER | DEBUG] Buffer Full -> Producer is waiting | Size: "+size);


            wait();
        }
        int previousTail = tail; // [DEBUG]
        integerQueue[tail] = number;
        // kyklikaaa
        tail = (tail+1) % integerQueue.length;
        size++;

        // -------
        // [DEBUG ONLY]
        System.out.println("[BUFFER | DEBUG] ADD | number="+number+
                " | slot="+previousTail+
                " | head="+head+
                " | tail="+tail+
                " | size="+size
        );


        notifyAll();
    }

    // Worker Requests and get the first Random number of the Queue
    public synchronized int getNumber() throws InterruptedException{
        while (size==0){
            // [DEBUG ONLY]
            System.out.println("[BUFFER | DEBUG] Buffer Empty -> Consumer waiting | size="+size);

            // Buffer empty nothing to get wait
            wait();
        }
        int previousHead = head; // [DEBUG]
        int number = integerQueue[head];
        head = (head+1) %  integerQueue.length;
        size--;

        // -----
        // [DEBUG]

        System.out.println(
                "[BUFFER] GET    | number=" + number +
                        " | slot=" + previousHead +
                        " | head=" + head +
                        " | tail=" + tail +
                        " | size=" + size
        );

        notifyAll();
        return number;

    }

}

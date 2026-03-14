package backend.secureRandomGenerator;

import java.security.SecureRandom;

public class Producer implements Runnable {

    private final Buffer buffer;
    private boolean running = true;

    public Producer(Buffer buffer){
        this.buffer = buffer;
    }

    public synchronized void stopRunning(){
        running =false;
    }

    public synchronized boolean isRunning(){
        return running;
    }

    @Override
    public void run(){
        SecureRandom random = new SecureRandom();

        while (isRunning()){
            try {
                int newRN = random.nextInt(Integer.MAX_VALUE);
                // [DEBUG]
                System.out.println("[PRODUCER | DEBUG]" + Thread.currentThread().getName() + " generated=" + newRN);

                buffer.addNumber(newRN);
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();

                // [DEBUG]
                System.out.println("[PRODUCER | DEBUG]" + Thread.currentThread().getName() + "interrupted -> stopping" );

                break;
            }catch (Exception e){
                System.out.println("Producer error: "+ e.getMessage());
            }

            System.out.println("[PRODUCER | DEBUG] " + Thread.currentThread().getName() + " stopped");
        }
    }


}

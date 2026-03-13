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
                buffer.addNumber(newRN);
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
                break;
            }catch (Exception e){
                System.out.println("Producer error: "+ e.getMessage());
            }
        }
    }




}

package backend.reducer;

import java.util.List;

public abstract class Job {

    protected final String jobId;
    protected final int expectedN;
    protected int received=0;
    protected boolean pushed = false;

    protected final String masterHost;
    protected final int masterCallbackPort;

    protected Job(String jobId, int expectedN, String masterHost, int masterCallbackPort) {
        this.jobId = jobId;
        this.expectedN = expectedN;
        this.masterHost = masterHost;
        this.masterCallbackPort = masterCallbackPort;
    }

    protected synchronized void increaseReceivedWorkers(){
        received++;
    }

    protected synchronized boolean isJobCompleteAndNotPushed(){
        return received >=expectedN && !pushed;
    }

    protected synchronized void markComplete(){
        pushed = true;
    }

    public abstract void addPartialResults(List<String> partialResults);

}

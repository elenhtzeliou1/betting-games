package com.example.bettingapp.network;

import android.app.Activity;

import java.util.List;

public class SocketTask extends Thread {
    private final MasterConnection masterConnection;
    private final String command;
    private final SocketCallback socketCallback;
    private final Activity activity;

    public SocketTask(MasterConnection masterConnection, String command, Activity activity, SocketCallback socketCallback){
        this.masterConnection = masterConnection;
        this.command = command;
        this.activity = activity;
        this.socketCallback = socketCallback;
    }

    @Override
    public void run(){
        try{
            List<String> result = masterConnection.sendCommand(command);
            activity.runOnUiThread(() -> socketCallback.onSuccess(result));
        } catch (Exception e) {
            activity.runOnUiThread(() -> socketCallback.onError(e.getMessage()));
        }
    }

}

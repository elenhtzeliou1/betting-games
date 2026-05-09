package com.example.bettingapp.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;

import com.example.bettingapp.network.MasterConnection;

public class AppViewModel extends AndroidViewModel {

    private static MasterConnection sharedConnection;
    private static String sharedPlayerId;

    public AppViewModel(Application app) {
        super(app);
    }

    public synchronized MasterConnection getConnection() {
        if (sharedConnection == null) {
            sharedConnection = new MasterConnection();
        }
        return sharedConnection;
    }

    public void setPlayerId(String id) {
        if (id == null) {
            sharedPlayerId = null;
        } else {
            sharedPlayerId = id.trim().toLowerCase();
        }
    }

    public String getPlayerId() {
        return sharedPlayerId;
    }

    public synchronized void clearSession() {
        if (sharedConnection != null) {
            sharedConnection.disconnect();
            sharedConnection = null;
        }
        sharedPlayerId = null;
    }
}
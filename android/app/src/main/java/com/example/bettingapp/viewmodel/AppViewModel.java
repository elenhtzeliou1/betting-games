package com.example.bettingapp.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;

import com.example.bettingapp.network.MasterConnection;

public class AppViewModel extends AndroidViewModel {
    private MasterConnection connection;
    private String playerId;

    public AppViewModel(Application app){ super(app); }

    public MasterConnection getConnection(){
        if(connection == null) connection = new MasterConnection();
        return connection;
    }

    public void setPlayerId(String id) {this.playerId = id; }
    public String getPlayerId(){return playerId;}

}

package com.example.bettingapp.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;

import com.example.bettingapp.network.MasterConnection;

import java.util.HashMap;
import java.util.Map;

public class AppViewModel extends AndroidViewModel {

    private static MasterConnection sharedConnection;
    private static String sharedPlayerId;

    private static final Map<String, Integer> userRatings = new HashMap<>();

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

    // Called after GET_USER_RATING returns from server
    public void setUserRatings(Map<String, Integer> ratings){
        userRatings.clear();
        userRatings.putAll(ratings);
    }

    // This is called immediately after a successfull RATE submission
    public void saveRating(String gameName, int stars){
        userRatings.put(gameName.trim().toLowerCase(), stars);
    }

    // Returns 0 if this game has not been rated yet
    public int getRating(String gameName){
        Integer r = userRatings.get(gameName.trim().toLowerCase());
        return r != null ? r : 0;
    }

    public synchronized void clearSession() {
        if (sharedConnection != null) {
            sharedConnection.disconnect();
            sharedConnection = null;
        }
        sharedPlayerId = null;
        userRatings.clear();
    }

}
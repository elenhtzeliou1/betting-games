package com.example.bettingapp.network;

public class MasterProtocol {

    public static final String ROLE = "PLAYER";
    public static final String END_MARKER = "END";
    public static final String GAME_LINE_PREFIX = "GAME|";


    public static String fetchAllGames(){
        return "FETCH_ALL_AVAILABLE_GAMES";
    }
    public static String search(String playerId, int minStars, String betCategory, String risk) {
        // "SEARCH user123|3|$|low"
        return "SEARCH " + playerId + "|" + minStars + "|" + betCategory + "|" + risk;
    }
    public static String play(String playerId, String gameName, String bet) {
        // "PLAY user123|poker|5.0"
        return "PLAY " + playerId + "|" + gameName + "|" + bet;
    }
    public static String rate(String playerId, String gameName, int stars) {
        return "RATE " + playerId + "|" + gameName + "|" + stars;
    }

    public static String getUserRatings(String playerId){
        return "GET_USER_RATINGS " + playerId;
    }
    public static String addBalance(String userId, String tokens) {
        return "ADD_BALANCE " + userId + "|" + tokens;
    }
    public static String viewBalance(String userId) {
        return "VIEW_BALANCE " + userId;
    }

}

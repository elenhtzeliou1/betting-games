package com.example.bettingapp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bettingapp.model.SearchResult;
import com.example.bettingapp.network.MasterConnection;
import com.example.bettingapp.network.MasterProtocol;

import java.util.ArrayList;
import java.util.List;

public class GamesViewModel extends ViewModel {

    private final MutableLiveData<List<SearchResult>> games = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private MasterConnection connection;

    public void init(MasterConnection connection) {
        this.connection = connection;
        if (games.getValue() == null) {
            fetchGames();
        }
    }

    public void fetchGames() {
        loading.setValue(true);
        error.setValue(null);
        new Thread(() -> {
            try {
                List<String> lines = connection.sendCommand(MasterProtocol.fetchAllGames());
                List<SearchResult> result = parseLines(lines);
                loading.postValue(false);
                games.postValue(result);
            } catch (Exception e) {
                loading.postValue(false);
                error.postValue(e.getMessage() != null ? e.getMessage() : "Failed to load games");
            }
        }).start();
    }

    public LiveData<List<SearchResult>> getGames() { return games; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    private List<SearchResult> parseLines(List<String> lines) throws Exception {
        List<SearchResult> result = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith(MasterProtocol.GAME_LINE_PREFIX)) {
                SearchResult game = parseLine(line);
                if (game != null) result.add(game);
            } else if (line.startsWith("ERROR")) {
                throw new Exception(line);
            }
        }
        return result;
    }

    private SearchResult parseLine(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 10) return null;
        try {
            String logo = parts.length >= 11 ? parts[10].trim() : "";
            return new SearchResult(
                    parts[1].trim(),
                    parts[2].trim(),
                    parts[3].trim(),
                    parts[4].trim(),
                    parts[5].trim(),
                    parts[6].trim(),
                    parts[7].trim(),
                    parts[8].trim(),
                    parts[9].trim(),
                    logo
            );
        } catch (Exception e) {
            return null;
        }
    }
}

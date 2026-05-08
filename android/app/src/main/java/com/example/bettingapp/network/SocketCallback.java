package com.example.bettingapp.network;

import java.util.List;

public interface SocketCallback {
    void onSuccess(List<String> lines);
    void onError(String message);
}

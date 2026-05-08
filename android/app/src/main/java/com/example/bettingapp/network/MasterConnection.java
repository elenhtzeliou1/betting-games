package com.example.bettingapp.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MasterConnection {
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private boolean connected = false;

    public void connect() throws Exception{
        socket = new Socket(AppConfig.MASTER_HOST, AppConfig.MASTER_PORT);
        input  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new PrintWriter(socket.getOutputStream(), true);

        input.readLine(); // maybe remove it we dont need it
        output.println("PLAYER");
        input.readLine();
        connected = true;
    }

    public synchronized List<String> sendCommand(String command) throws Exception{
        output.println(command);
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = input.readLine()) != null) {
            if ("END".equals(line)) break;
            if (!line.isBlank()) lines.add(line);
        }
        return lines;
    }

    public void disconnect(){
        try {
            if (output != null) output.println("exit");
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
        connected = false;
    }

    public boolean isConnected() { return connected; }
}


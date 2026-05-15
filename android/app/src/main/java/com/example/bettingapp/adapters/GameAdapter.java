package com.example.bettingapp.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bettingapp.R;
import com.example.bettingapp.model.SearchResult;

import java.util.ArrayList;
import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameViewHolder> {

    public interface OnGameClickListener {
        void onGameClick(SearchResult game);
    }

    private List<SearchResult> games = new ArrayList<>();
    private OnGameClickListener clickListener;

    public void setOnGameClickListener(OnGameClickListener listener) {
        this.clickListener = listener;
    }

    public void setGames(List<SearchResult> games) {
        this.games = games;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(GameViewHolder holder, int position) {
        SearchResult game = games.get(position);

        holder.gameName.setText(game.getGameName());

        int color;
        switch (game.getRisk().toLowerCase()) {
            case "high":   color = Color.parseColor("#FF4444"); break;
            case "medium": color = Color.parseColor("#FFA500"); break;
            default:       color = Color.parseColor("#44BB44"); break;
        }
        holder.gameLogo.setColorFilter(color);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onGameClick(game);
        });
    }

    @Override
    public int getItemCount() { return games.size(); }
}

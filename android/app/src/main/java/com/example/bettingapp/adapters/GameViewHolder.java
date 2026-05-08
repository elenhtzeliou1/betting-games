package com.example.bettingapp.adapters;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bettingapp.R;

public class GameViewHolder extends RecyclerView.ViewHolder {
    public ImageView gameLogo;
    public TextView gameName;

    public GameViewHolder(View itemView) {
        super(itemView);
        gameLogo = itemView.findViewById(R.id.gameLogo);
        gameName = itemView.findViewById(R.id.gameName);
    }
}
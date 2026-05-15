package com.example.bettingapp.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.bettingapp.R;
import com.example.bettingapp.adapters.GameAdapter;
import com.example.bettingapp.viewmodel.AppViewModel;
import com.example.bettingapp.viewmodel.GamesViewModel;

public class GamesFragment extends Fragment {

    private GameAdapter gameAdapter;
    private GamesViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_games, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        RecyclerView recyclerView = view.findViewById(R.id.gamesRecyclerView);
        TextView emptyMessage = view.findViewById(R.id.emptyMessage);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        gameAdapter = new GameAdapter();
        gameAdapter.setOnGameClickListener(game -> {
            GameBottomSheetFragment sheet = GameBottomSheetFragment.newInstance(
                    game.getGameName(),
                    game.getProviderName(),
                    game.getMinBet(),
                    game.getMaxBet(),
                    game.getJackpot(),
                    game.getRisk(),
                    game.getBetCategory(),
                    game.getStars(),
                    game.getNoOfVotes(),
                    game.getGameLogo()
            );
            sheet.show(getChildFragmentManager(), GameBottomSheetFragment.TAG);
        });
        recyclerView.setAdapter(gameAdapter);

        viewModel = new ViewModelProvider(requireActivity()).get(GamesViewModel.class);
        AppViewModel appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        viewModel.init(appViewModel.getConnection());

        viewModel.getLoading().observe(getViewLifecycleOwner(),
                isLoading -> swipeRefresh.setRefreshing(isLoading));

        viewModel.getGames().observe(getViewLifecycleOwner(), games -> {
            gameAdapter.setGames(games);
            emptyMessage.setText(R.string.no_games_available);
            emptyMessage.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null) {
                emptyMessage.setText(errorMsg);
                emptyMessage.setVisibility(View.VISIBLE);
            }
        });

        swipeRefresh.setOnRefreshListener(() -> viewModel.fetchGames());
    }
}
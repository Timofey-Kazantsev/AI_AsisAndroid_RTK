package com.example.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
    private List<User> users;

    public UserAdapter(List<User> users) {
        this.users = users;
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView emailTextView, nameTextView;

        public UserViewHolder(View view) {
            super(view);
            emailTextView = view.findViewById(R.id.emailTextView);
            nameTextView = view.findViewById(R.id.nameTextView);
        }
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.user_item, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.emailTextView.setText(user.getEmail());
        holder.nameTextView.setText(user.getDisplayName());
    }

    @Override
    public int getItemCount() {
        return users.size();
    }
}

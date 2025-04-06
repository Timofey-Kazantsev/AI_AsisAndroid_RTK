package com.example.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialogActivity extends AppCompatActivity {
    private RecyclerView dialogRecyclerView;
    private DialogAdapter dialogAdapter;
    private FirebaseDatabase db = FirebaseDatabase.getInstance();
    private List<User> usersList = new ArrayList<>();
    private Map<String, String> speakerMapping = new HashMap<>();
    private List<DialogueItem> dialogueItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_panel_info);

        dialogRecyclerView = findViewById(R.id.dialogRecyclerView);
        dialogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        dialogAdapter = new DialogAdapter(dialogueItems, usersList, speakerMapping);
        dialogRecyclerView.setAdapter(dialogAdapter);

        Button summaryButton = findViewById(R.id.button_summary);
        Button tasksButton = findViewById(R.id.button_tasks);

        summaryButton.setOnClickListener(v -> new SummaryDialogFragment().show(getSupportFragmentManager(), "SummaryDialog"));
        tasksButton.setOnClickListener(v -> startActivity(new Intent(this, TasksActivity.class)));

        SharedPreferences preferences = getSharedPreferences("AudioCache", MODE_PRIVATE);
        String firstJson = preferences.getString("first_json", null);
        if (firstJson != null) {
            loadDialogue(firstJson);
        }

        loadUsers();
    }

    private void loadDialogue(String firstJson) {
        try {
            JSONObject jsonObject = new JSONObject(firstJson);
            String speakersPhrases = jsonObject.getString("speakers_phrases");

            dialogueItems.clear();
            String[] lines = speakersPhrases.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                // Разделяем строку на спикера и текст
                String[] parts = line.split(":\\s*", 2); // Разделяем по первому вхождению ":"
                if (parts.length < 2) {
                    Log.w("DialogActivity", "⚠️ Некорректная строка: " + line);
                    continue;
                }
                String speaker = parts[0].trim(); // Например, "SPEAKER_1"
                String text = parts[1].trim();    // Например, "[0:00:00] Миша сделает telegram бота, Настя сделает дизайн."
                dialogueItems.add(new DialogueItem(speaker, text));
            }
            dialogAdapter.notifyDataSetChanged();
        } catch (JSONException e) {
            Log.e("DialogActivity", "❌ Ошибка разбора JSON: " + e.getMessage());
        }
    }

    private void loadUsers() {
        DatabaseReference ref = db.getReference("users");
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                usersList.clear();
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    User user = userSnapshot.getValue(User.class);
                    if (user != null) {
                        usersList.add(user);
                        Log.d("DialogActivity", "Загружен пользователь: " + user.getDisplayName() + ", " + user.getEmail());
                    }
                }
                Log.d("DialogActivity", "✅ Загружено " + usersList.size() + " пользователей");
                if (usersList.isEmpty()) {
                    Log.w("DialogActivity", "⚠️ Список пользователей пуст!");
                }
                dialogAdapter.setUsers(usersList);
                dialogAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("DialogActivity", "❌ Ошибка загрузки: " + error.getMessage());
            }
        });
    }

    private static class DialogueItem {
        String speaker;
        String text;

        DialogueItem(String speaker, String text) {
            this.speaker = speaker;
            this.text = text;
        }
    }

    private class DialogAdapter extends RecyclerView.Adapter<DialogAdapter.ViewHolder> {
        private List<DialogueItem> items;
        private List<User> users;
        private Map<String, String> speakerMapping;

        DialogAdapter(List<DialogueItem> items, List<User> users, Map<String, String> speakerMapping) {
            this.items = items;
            this.users = users;
            this.speakerMapping = speakerMapping;
        }

        public void setUsers(List<User> users) {
            this.users = users;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.dialog_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DialogueItem item = items.get(position);
            String mappedSpeaker = speakerMapping.getOrDefault(item.speaker, item.speaker);
            holder.speakerText.setText(mappedSpeaker);
            holder.dialogueText.setText(item.text);

            if (!users.isEmpty()) {
                holder.speakerText.setOnClickListener(v -> {
                    Log.d("DialogActivity", "Клик на " + item.speaker + ", пользователей: " + users.size());
                    showUserSelectionDialog(item, holder);
                });
            } else {
                holder.speakerText.setOnClickListener(null);
                Log.w("DialogActivity", "⚠️ Нет пользователей для выбора в момент привязки!");
            }
        }

        private void showUserSelectionDialog(DialogueItem item, ViewHolder holder) {
            String[] userNames = new String[users.size()];
            for (int i = 0; i < users.size(); i++) {
                userNames[i] = users.get(i).getDisplayName();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(DialogActivity.this);
            builder.setTitle("Выберите спикера для " + item.speaker);
            builder.setItems(userNames, (dialog, which) -> {
                User selectedUser = users.get(which);
                String displayName = selectedUser.getDisplayName() != null ? selectedUser.getDisplayName() : "Без имени";
                Log.d("DialogActivity", "Текущий текст: " + holder.speakerText.getText() + ", Новый текст: " + displayName + ", позиция: " + which);
                speakerMapping.put(item.speaker, displayName);
                holder.speakerText.setText(displayName);
                notifyItemChanged(holder.getAdapterPosition());
            });
            builder.setNegativeButton("Отмена", null);
            builder.show();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView speakerText;
            TextView dialogueText;

            ViewHolder(View itemView) {
                super(itemView);
                speakerText = itemView.findViewById(R.id.speakerText);
                dialogueText = itemView.findViewById(R.id.dialogueText);
            }
        }
    }
}
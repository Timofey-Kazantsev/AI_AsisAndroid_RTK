package com.example.assistant;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TasksActivity extends AppCompatActivity {
    private LinearLayout spinnerContainer;
    private FirebaseDatabase db = FirebaseDatabase.getInstance();
    private List<User> usersList = new ArrayList<>();
    private List<Task> tasksList = new ArrayList<>();
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        spinnerContainer = findViewById(R.id.spinnerContainer);
        Button sendEmailButton = findViewById(R.id.button11);
        mainHandler = new Handler(Looper.getMainLooper());

        sendEmailButton.setOnClickListener(v -> sendTasksToEmail());

        SharedPreferences preferences = getSharedPreferences("AudioCache", MODE_PRIVATE);
        String thirdJson = preferences.getString("third_json", null);
        if (thirdJson != null) {
            loadTasks(thirdJson);
        }

        loadUsers();
    }

    private void loadTasks(String thirdJson) {
        try {
            JSONObject jsonObject = new JSONObject(thirdJson);
            JSONArray tasksArray = jsonObject.getJSONArray("tasks");
            tasksList.clear();
            for (int i = 0; i < tasksArray.length(); i++) {
                String taskDescription = tasksArray.getString(i);
                tasksList.add(new Task(taskDescription));
            }
            updateTasksUI();
        } catch (JSONException e) {
            Log.e("TasksActivity", "❌ Ошибка разбора JSON: " + e.getMessage());
        }
    }

    private void updateTasksUI() {
        spinnerContainer.removeAllViews();
        for (Task task : tasksList) {
            LinearLayout taskLayout = new LinearLayout(this);
            taskLayout.setOrientation(LinearLayout.VERTICAL);
            taskLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView taskText = new TextView(this);
            taskText.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            taskText.setText(task.getDescription());
            taskText.setPadding(0, 0, 0, 8);

            Spinner emailSpinner = new Spinner(this);
            ArrayAdapter<User> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, usersList);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            emailSpinner.setAdapter(adapter);
            if (task.getAssignedEmail() != null) {
                for (int i = 0; i < usersList.size(); i++) {
                    if (usersList.get(i).getEmail().equals(task.getAssignedEmail())) {
                        emailSpinner.setSelection(i);
                        break;
                    }
                }
            }
            emailSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    task.setAssignedEmail(usersList.get(pos).getEmail());
                    Log.d("TasksActivity", "Задача '" + task.getDescription() + "' назначена на " + task.getAssignedEmail());
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            taskLayout.addView(taskText);
            taskLayout.addView(emailSpinner);
            spinnerContainer.addView(taskLayout);
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
                    }
                }
                Log.d("TasksActivity", "✅ Загружено " + usersList.size() + " пользователей");
                SharedPreferences preferences = getSharedPreferences("AudioCache", MODE_PRIVATE);
                String thirdJson = preferences.getString("third_json", null);
                if (thirdJson != null) {
                    loadTasks(thirdJson);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("TasksActivity", "❌ Ошибка загрузки: " + error.getMessage());
            }
        });
    }

    private void sendTasksToEmail() {
        boolean hasTasksToSend = false;

        for (Task task : tasksList) {
            String assignedEmail = task.getAssignedEmail();
            if (assignedEmail != null && !assignedEmail.isEmpty()) {
                hasTasksToSend = true;
                String subject = "Новая задача из приложения";
                String message = "Задача: " + task.getDescription();

                new Thread(() -> {
                    try {
                        EmailSender.sendEmail(assignedEmail, subject, message);
                        Log.d("TasksActivity", "✅ Задача '" + task.getDescription() + "' отправлена на " + assignedEmail);
                        mainHandler.post(() -> {
                            Toast.makeText(TasksActivity.this, "Задача отправлена на " + assignedEmail, Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        Log.e("TasksActivity", "❌ Ошибка отправки задачи на " + assignedEmail + ": " + e.getMessage());
                        mainHandler.post(() -> {
                            Toast.makeText(TasksActivity.this, "Ошибка отправки на " + assignedEmail + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }).start();
            } else {
                Log.w("TasksActivity", "⚠️ Задача '" + task.getDescription() + "' не отправлена: email не выбран");
            }
        }

        if (!hasTasksToSend) {
            Toast.makeText(this, "Выберите пользователей для отправки задач", Toast.LENGTH_SHORT).show();
        }
    }

    private static class Task {
        private String description;
        private String assignedEmail;

        Task(String description) {
            this.description = description;
        }

        String getDescription() {
            return description;
        }

        String getAssignedEmail() {
            return assignedEmail;
        }

        void setAssignedEmail(String email) {
            this.assignedEmail = email;
        }
    }
}
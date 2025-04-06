package com.example.assistant;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Tasks extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tasks);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });



        // Контейнер для динамических элементов
        LinearLayout spinnerContainer = findViewById(R.id.spinnerContainer); // Контейнер для динамических элементов
        int numberOfSpinners = 5; // Число Spinner для теста
        String[] items = {"Опция 1", "Опция 2", "Опция 3"};
        String[] tasks = {"Реплика 1", "Реплика 2", "Реплика 3", "Реплика 4", "Реплика 5"}; // Пример текста для заданий

        for (int i = 0; i < numberOfSpinners; i++) {
            // Горизонтальный контейнер для Spinner и TextView
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);

            // Создаем Spinner
            Spinner spinner = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            rowLayout.addView(spinner);

            // Создаем TextView для задания
            TextView taskView = new TextView(this);
            taskView.setText(tasks[i]); // Пример текста задания
            taskView.setTextSize(16);
            taskView.setPadding(16, 0, 0, 0);
            rowLayout.addView(taskView);

            // Добавляем строку в контейнер
            spinnerContainer.addView(rowLayout);
        }
    }
}

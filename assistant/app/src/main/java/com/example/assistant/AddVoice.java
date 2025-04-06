package com.example.assistant;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AddVoice extends AppCompatActivity {
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private ActivityResultLauncher<String> filePickerLauncher;
    private ActivityResultLauncher<Uri> videoCaptureLauncher;
    private File audioFile;
    private File videoFile;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_voice);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestStoragePermissions();

        Button recordAudioButton = findViewById(R.id.button_record_voice);
        Button stopRecordButton = findViewById(R.id.button_stop_record_voice);
        Button recordVideoButton = findViewById(R.id.button_record_video);
        Button addFileButton = findViewById(R.id.button_add_voice_video);

        recordAudioButton.setOnClickListener(v -> startRecording());
        stopRecordButton.setOnClickListener(v -> stopRecording());

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                Log.d("FilePicker", "✅ Файл выбран: " + uri.toString());
                sendFileToServerAsync(getFileFromUri(uri));
            } else {
                Log.e("FilePicker", "❌ Файл не был выбран");
            }
        });
        addFileButton.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

        videoCaptureLauncher = registerForActivityResult(new ActivityResultContracts.CaptureVideo(), success -> {
            if (success) {
                Log.d("VideoRecorder", "✅ Видео записано!");
                sendFileToServerAsync(videoFile);
            } else {
                Log.e("VideoRecorder", "❌ Ошибка записи видео");
            }
        });

        recordVideoButton.setOnClickListener(v -> {
            Uri videoUri = getVideoFileUri();
            videoCaptureLauncher.launch(videoUri);
        });
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    1);
        }
    }

    private Uri getVideoFileUri() {
        videoFile = new File(getExternalFilesDir(null), "saved_video.mp4");
        return FileProvider.getUriForFile(this, "com.example.assistant.fileprovider", videoFile);
    }

    private void startRecording() {
        Log.d("AudioRecord", "✅ Метод startRecording() вызван");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AudioRecord", "❌ Нет разрешения на запись аудио!");
            return;
        }

        int sampleRate = 16000;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2);

        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            Log.d("AudioRecord", "✅ AudioRecord успешно инициализирован");
            audioFile = new File(getExternalFilesDir(null), "saved_audio.raw");
            isRecording = true;

            new Thread(() -> {
                try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                    byte[] buffer = new byte[bufferSize];
                    audioRecord.startRecording();
                    Log.d("AudioRecord", "✅ Запись началась в потоке");
                    while (isRecording) {
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            fos.write(buffer, 0, read);
                            Log.d("AudioRecord", "Записано " + read + " байт");
                        } else if (read < 0) {
                            Log.e("AudioRecord", "Ошибка чтения AudioRecord: " + read);
                            break;
                        }
                    }
                    fos.flush();
                    Log.d("AudioRecord", "✅ Поток записи завершён, размер файла: " + audioFile.length() + " байт");
                } catch (IOException e) {
                    Log.e("AudioRecord", "❌ Ошибка записи аудио: " + e.getMessage());
                }
            }).start();
        } else {
            Log.e("AudioRecord", "❌ Ошибка: AudioRecord не инициализирован");
        }
    }

    private void stopRecording() {
        Log.d("AudioRecord", "✅ Метод stopRecording() вызван");

        if (audioRecord != null && isRecording) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            Log.d("AudioRecord", "✅ Запись завершена");

            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                File mp3File = new File(getExternalFilesDir(null), "saved_audio.mp3");
                convertPcmToMp3(audioFile, mp3File, () -> sendFileToServerAsync(mp3File));
            } else {
                Log.e("AudioRecord", "❌ Файл записи пуст или не существует, размер: " + (audioFile != null ? audioFile.length() : -1));
            }
        } else {
            Log.e("AudioRecord", "❌ Ошибка: AudioRecord не инициализирован или запись не запущена");
        }
    }

    private void convertPcmToMp3(File pcmFile, File mp3File, Runnable onComplete) {
        try {
            // Формируем команду как строку
            String command = String.format(
                    "-f s16le -ar 16000 -ac 1 -i \"%s\" -b:a 128k \"%s\"",
                    pcmFile.getAbsolutePath(),
                    mp3File.getAbsolutePath()
            );

            FFmpegKit.executeAsync(command, session -> {
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    Log.d("AudioConvert", "✅ Конвертация завершена, размер MP3: " + mp3File.length() + " байт");
                    handler.post(onComplete); // Выполняем отправку в главном потоке
                } else {
                    Log.e("AudioConvert", "❌ Ошибка конвертации: " + session.getFailStackTrace());
                }
            }, log -> {
                // Логирование процесса (опционально)
                Log.d("FFmpegLog", log.getMessage());
            }, statistics -> {
                // Статистика выполнения (опционально)
            });
        } catch (Exception e) {
            Log.e("AudioConvert", "❌ Ошибка при запуске конвертации: " + e.getMessage());
        }
    }

    private File getAudioFile() {
        return new File(getExternalFilesDir(null), "saved_audio.mp3");
    }

    private File getFileFromUri(Uri uri) {
        if (uri == null) return null;

        File file = null;
        try {
            ContentResolver contentResolver = getContentResolver();
            String fileName = getFileNameFromUri(uri);
            if (fileName == null) {
                fileName = "temp_file_" + System.currentTimeMillis();
            }

            file = new File(getCacheDir(), fileName);
            InputStream inputStream = contentResolver.openInputStream(uri);
            if (inputStream != null) {
                FileOutputStream outputStream = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();
            }
        } catch (IOException e) {
            Log.e("FileUtil", "❌ Ошибка при конвертации Uri в File: " + e.getMessage());
            return null;
        }
        return file;
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex);
            }
            cursor.close();
        }
        return fileName;
    }

    private void sendFileToServerAsync(File file) {
        if (file == null || !file.exists()) {
            Log.e("FileUpload", "❌ Файл не найден, отправка невозможна.");
            return;
        }

        if (file.length() == 0) {
            Log.e("FileUpload", "❌ Файл пустой, отправка невозможна.");
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork == null || !activeNetwork.isConnected()) {
            Log.e("FileUpload", "❌ Нет подключения к интернету");
            return;
        }

        new Thread(() -> {
            Log.d("FileUpload", "🚀 Отправка файла: " + file.getAbsolutePath() + ", размер: " + file.length() + " байт");
            HttpURLConnection connection = null;
            try {
                // Первый запрос: отправка файла
                URL serverUrl = new URL("http://hack.t-donstu.ru:8000/transcribe"); // Убедитесь, что URL правильный
                connection = (HttpURLConnection) serverUrl.openConnection();
                connection.setRequestMethod("POST");
                String boundary = "---------------------------14737809831466499882746641449";
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                String mimeType = getMimeType(file);
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }
                Log.d("FileUpload", "MIME-тип файла: " + mimeType);

                DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n");
                dos.writeBytes("Content-Type: " + mimeType + "\r\n");
                dos.writeBytes("\r\n");

                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                fis.close();

                dos.writeBytes("\r\n");
                dos.writeBytes("--" + boundary + "--\r\n");
                dos.flush();
                dos.close();

                // Получение ответа
                int responseCode = connection.getResponseCode();
                String responseMessage;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    responseMessage = readStreamToString(inputStream);
                    inputStream.close();
                    handler.post(() -> Log.d("FileUpload", "✅ Файл успешно отправлен, код ответа: " + responseCode + ", ответ: " + responseMessage));

                    // Пересылка JSON на другой API
                    forwardJsonToAnotherApi(responseMessage);
                } else {
                    InputStream errorStream = connection.getErrorStream();
                    if (errorStream != null) {
                        responseMessage = readStreamToString(errorStream);
                        errorStream.close();
                    } else {
                        responseMessage = connection.getResponseMessage();
                    }
                    String finalResponseMessage = responseMessage;
                    handler.post(() -> Log.e("FileUpload", "❌ Ошибка сервера, код ответа: " + responseCode + ", сообщение: " + finalResponseMessage));
                }

            } catch (IOException e) {
                handler.post(() -> Log.e("FileUpload", "❌ Ошибка отправки файла: " + e.getMessage()));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    // Метод для пересылки JSON на другой API
    private void forwardJsonToAnotherApi(String jsonResponse) {
        HttpURLConnection connection = null;
        try {
            // Замените URL на ваш второй API
            URL apiUrl = new URL("http://hack.t-donstu.ru:8000/extract-tasks"); // Укажите правильный URL
            connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            // Отправка JSON
            try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream())) {
                dos.write(jsonResponse.getBytes("UTF-8"));
                dos.flush();
            }

            // Получение ответа от второго API
            int responseCode = connection.getResponseCode();
            String responseMessage;
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                InputStream inputStream = connection.getInputStream();
                responseMessage = readStreamToString(inputStream);
                inputStream.close();
                handler.post(() -> Log.d("JsonForward", "✅ JSON успешно переслан, код ответа: " + responseCode + ", ответ: " + responseMessage));
            } else {
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    responseMessage = readStreamToString(errorStream);
                    errorStream.close();
                } else {
                    responseMessage = connection.getResponseMessage();
                }
                String finalResponseMessage = responseMessage;
                handler.post(() -> Log.e("JsonForward", "❌ Ошибка пересылки JSON, код ответа: " + responseCode + ", сообщение: " + finalResponseMessage));
            }

        } catch (IOException e) {
            handler.post(() -> Log.e("JsonForward", "❌ Ошибка пересылки JSON: " + e.getMessage()));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // Вспомогательные методы
    private String readStreamToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }

    private String getMimeType(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (fileName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (fileName.endsWith(".wav")) {
            return "audio/wav";
        } else if (fileName.endsWith(".m4a")) {
            return "audio/mp4";
        } else if (fileName.endsWith(".ogg")) {
            return "audio/ogg";
        } else {
            return null;
        }
    }
}
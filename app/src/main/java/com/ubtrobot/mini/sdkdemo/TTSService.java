package com.ubtrobot.mini.sdkdemo;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class TTSService extends Service implements RecognitionListener{
    private static final String TAG = "SpeechToTextService";
    private Model model;
    private SpeechService speechService;
    private TextToSpeech textToSpeech;
    private String result;
    private String lastRecognizedText = "";
    private static final String CHANNEL_ID = "TTSService";

    @Override
    public void onCreate() {
        super.onCreate();
        // Create a notification channel for Android Oreo and higher

        Toast.makeText(this, "My Service TTS Started", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onStart");

        createNotificationChannel();
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if (i != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.ITALY);
                }
            }
        });

        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            // If permission is not granted, show a notification to request it
            showPermissionNotification();
        } else {
            // Permission is granted, proceed with initialization
            initModel();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_ID,
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void showPermissionNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Permission Required")
                .setContentText("Please grant RECORD_AUDIO permission")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .build();

        startForeground(1, notification);

        // You might want to launch an activity from the notification to handle permission request
        // or use some other mechanism to inform the user about the permission request.
        // Note: Starting an activity from a background service might not be a good UX practice.
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void initModel() {
        StorageService.unpack(this, "model-it", "model",
                (model) -> {
                    this.model = model;
                    Toast.makeText(this, "Sono pronto", Toast.LENGTH_SHORT);
                    speakText("Sono pronto");
                    recognizeMicrophone();
                },
                (exception) -> {
                    Log.e(TAG, "Failed to unpack the model: " + exception.getMessage());
                }
        );
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        } else {
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening((RecognitionListener) this);
            } catch (IOException e) {
                Log.e(TAG, "Error initializing recognizer: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    private void speakText(String text) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    private void sendTextToServer(String text) {
        AgentRequest request = new AgentRequest();
        request.setRobot_id("ROBOT-0001");
        request.setAuth_id("ALPHA-MINI-10F5-PRWE-U9YV-ADUQ");
        request.setText(text);

        new SendPostRequestTask().execute(request);
    }

    public AgentResponse sendPostRequest(AgentRequest requestObj) {
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL("https://alpha-mini.azurewebsites.net/agent");
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setDoOutput(true);

            try(OutputStream os = urlConnection.getOutputStream()) {
                byte[] input = new Gson().toJson(requestObj).getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            try(BufferedReader br = new BufferedReader(
                    new InputStreamReader(urlConnection.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return new Gson().fromJson(response.toString(), AgentResponse.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return null;
    }

    @Override
    public void onPartialResult(String s) {

    }

    @Override
    public void onResult(String s) {
        try {
            JSONObject jsonResult = new JSONObject(s);
            String textValue = jsonResult.optString("text", "").trim();

            if (!textValue.isEmpty() && !textValue.equals(lastRecognizedText)) {

                result += textValue + "\n";
                speakText(result);

                Log.e(TAG, "Risultato parziale: " + result);

                speechService.stop();
                speechService = null;

                AgentRequest request = new AgentRequest();

                request.setRobot_id("ROBOT-0001");

                request.setAuth_id("ALPHA-MINI-10F5-PRWE-U9YV-ADUQ"); // Sostituisci con il tuo ID autenticazione
                request.setText(result);

                new SendPostRequestTask().execute(request);

                result = "";

                //textToSpeech.speak(textValue, TextToSpeech.QUEUE_FLUSH, null);

                lastRecognizedText = textValue;
            }

        } catch (JSONException e) {
            Log.e(TAG, "Errore durante il parsing JSON: " + e.getMessage());
        }
    }

    @Override
    public void onFinalResult(String s) {

    }

    @Override
    public void onError(Exception e) {

    }

    @Override
    public void onTimeout() {

    }

    // Classe AsyncTask per inviare richieste POST al server
    private class SendPostRequestTask extends AsyncTask<AgentRequest, Void, AgentResponse> {
        @Override
        protected AgentResponse doInBackground(AgentRequest... params) {
            return sendPostRequest(params[0]);
        }

        @Override
        protected void onPostExecute(AgentResponse result) {
            if (result != null) {
                Log.e(TAG, "Risposta dall'API: " + result.getAnswer());
            }
        }
    }

    public class AgentRequest {
        String auth_id;
        String robot_id;
        String text;

        public String getAuth_id() {
            return auth_id;
        }

        public void setAuth_id(String auth_id) {
            this.auth_id = auth_id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getRobot_id() {
            return robot_id;
        }

        public void setRobot_id(String robot_id) {
            this.robot_id = robot_id;
        }
    }

    public class AgentResponse {
        String action;
        String answer;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }

}

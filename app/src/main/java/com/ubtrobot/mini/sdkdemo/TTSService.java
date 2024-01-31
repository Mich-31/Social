package com.ubtrobot.mini.sdkdemo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    // private String result;
    private String lastRecognizedText = "";
    private static final String CHANNEL_ID = "TTSService";
    private AudioRecord audioRecord;
    private boolean isRecording;
    private String audioFilePath = "app/audio"; // Definisci il percorso del file qui
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

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
                    speakText("Sono pronto");
                    recognizeMicrophone();
                    startAudioRecording();
                },
                (exception) -> {
                    Log.e(TAG, "Failed to unpack the model: " + exception.getMessage());
                }
        );
    }

    private void startAudioRecording() {
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        audioRecord.startRecording();
        isRecording = true;

        Thread recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private void stopAudioRecording() {
        if (audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void writeAudioDataToFile() {
        byte data[] = new byte[bufferSize];
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(audioFilePath);
            while (isRecording) {
                int read = audioRecord.read(data, 0, bufferSize);
                if (read != AudioRecord.ERROR_INVALID_OPERATION) {
                    os.write(data, 0, read);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
    public void onPartialResult(String s) {

    }

    @Override
    public void onResult(String s) {
        try {
            JSONObject jsonResult = new JSONObject(s);
            String textValue = jsonResult.optString("text", "").trim();

            if (!textValue.equals(lastRecognizedText) && textValue.contains("ehy mario")){

                stopAudioRecording();
                speechService.stop();
                speechService = null;

                speakText("Ricevuto");

                //AgentRequest request = new AgentRequest();
                //request.setRobot_id("ROBOT-0001");
                //request.setAuth_id("ALPHA-MINI-10F5-PRWE-U9YV-ADUQ"); // Sostituisci con il tuo ID autenticazione

                new SendAudioFileTask().execute(audioFilePath);

                lastRecognizedText = textValue;

                /*
                result += textValue + "\n";
                speakText(result);

                Log.e(TAG, "Risultato parziale: " + result);

                speechService.stop();
                speechService = null;
                AgentRequest request = new AgentRequest();
                request.setRobot_id("ROBOT-0001");
                request.setAuth_id("ALPHA-MINI-10F5-PRWE-U9YV-ADUQ"); // Sostituisci con il tuo ID autenticazione
                request.setText(result);
                speakText("Ricevuto");
                new SendPostRequestTask().execute(request);
                result = "";
                //textToSpeech.speak(textValue, TextToSpeech.QUEUE_FLUSH, null);
                lastRecognizedText = textValue;
                */
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

    public AgentResponse sendAudioFileToServer(String audioFilePath) {
        HttpURLConnection urlConnection = null;
        DataOutputStream dos = null;
        FileInputStream fis = null;
        try {
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****";

            URL url = new URL("https://alpha-mini.azurewebsites.net/upload-audio");
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setUseCaches(false);
            urlConnection.setRequestProperty("Connection", "Keep-Alive");
            urlConnection.setRequestProperty("ENCTYPE", "multipart/form-data");
            urlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            urlConnection.setRequestProperty("file", audioFilePath);

            dos = new DataOutputStream(urlConnection.getOutputStream());

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"auth_id\"" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes("ALPHA-MINI-10F5-PRWE-U9YV-ADUQ"); // Sostituisci con il tuo AUTH_ID
            dos.writeBytes(lineEnd);

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"" + audioFilePath + "\"" + lineEnd);
            dos.writeBytes(lineEnd);

            fis = new FileInputStream(new File(audioFilePath));
            int bytesAvailable = fis.available();
            int bufferSize = Math.min(bytesAvailable, 1 * 1024 * 1024);
            byte[] buffer = new byte[bufferSize];

            int bytesRead = fis.read(buffer, 0, bufferSize);
            while (bytesRead > 0) {
                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fis.available();
                bufferSize = Math.min(bytesAvailable, 1 * 1024 * 1024);
                bytesRead = fis.read(buffer, 0, bufferSize);
            }

            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            // Ottieni la risposta dal server
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Converti la risposta in oggetto AgentResponse
                return new Gson().fromJson(response.toString(), AgentResponse.class);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) fis.close();
                if (dos != null) dos.flush();
                if (dos != null) dos.close();
                if (urlConnection != null) urlConnection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
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

    // Classe AsyncTask per inviare file audio al server
    private class SendAudioFileTask extends AsyncTask<String, Void, AgentResponse> {
        @Override
        protected AgentResponse doInBackground(String... params) {
            // Invia un file audio
            return sendAudioFileToServer(params[0]);
        }

        @Override
        protected void onPostExecute(AgentResponse result) {
            if (result != null) {
                // Aggiorna l'interfaccia utente con la risposta qui
                Log.e(TAG, "Risposta dall'API: " + result.getAnswer());
                recognizeMicrophone();
                startAudioRecording();
            }
        }
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
                // Aggiorna l'interfaccia utente con la risposta qui
                Log.e(TAG, "Risposta dall'API: " + result.getAnswer());
                // Ad esempio, potresti voler dire la risposta
                textToSpeech.speak(result.getAnswer(), TextToSpeech.QUEUE_FLUSH, null);
                while(textToSpeech.isSpeaking()){

                }
                recognizeMicrophone();
                startAudioRecording();
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

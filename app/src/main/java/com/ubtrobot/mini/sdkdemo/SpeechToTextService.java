package com.ubtrobot.mini.sdkdemo;

import android.Manifest;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
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

public class SpeechToTextService extends IntentService implements RecognitionListener {

    private static final String TAG = "SpeechToTextService";

    private Model model;
    private SpeechService speechService;
    private TextToSpeech textToSpeech;

    public SpeechToTextService() {
        super("SpeechToTextService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            //if (checkRecordAudioPermission()) {
                initModel();
            //}
        }
    }

    private void initModel() {
        StorageService.unpack(this, "model-it", "model",
                (model) -> {
                    this.model = model;
                    Toast.makeText(this, "Sono pronto", Toast.LENGTH_SHORT);
                    textToSpeech.speak("Sono pronto", TextToSpeech.QUEUE_FLUSH, null);
                    recognizeMicrophone();
                },
                (exception) -> {
                    Log.e(TAG, "Failed to unpack the model: " + exception.getMessage());
                }
        );
    }

//    private boolean checkRecordAudioPermission() {
//        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
//    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        } else {
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
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

    @Override
    public void onPartialResult(String s) {

    }

    @Override
    public void onResult(String hypothesis) {
        try {
            JSONObject jsonResult = new JSONObject(hypothesis);
            String textValue = jsonResult.optString("text", "").trim();

            if (!textValue.isEmpty()) {
                // Invia il testo al server
                sendTextToServer(textValue);
                // Leggi il testo ad alta voce
                speakText(textValue);
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

    private void speakText(String text) {
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if (i != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.ITALY);
                    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        });
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

    // Resto del codice rimane invariato...
}

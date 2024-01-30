package com.ubtrobot.mini.sdkdemo;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class SpeechToText extends Activity implements RecognitionListener {

    EditText Text;
    Button btnText;
    TextToSpeech textToSpeech;

    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private String result;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.speech_to_text);

        Text = findViewById(R.id.Text);
        btnText = findViewById(R.id.btnText);
        result = "";

        resultView = findViewById(R.id.result_text2);

        // create an object textToSpeech and adding features into it
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {

                // if No error is found then only it will run
                if(i!=TextToSpeech.ERROR){
                    // To Choose language of speech
                    textToSpeech.setLanguage(Locale.ITALY);
                    textToSpeech.speak(Text.getText().toString(),TextToSpeech.QUEUE_FLUSH,null);
                }
            }
        });

        // Adding OnClickListener
        btnText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textToSpeech.speak(Text.getText().toString(),TextToSpeech.QUEUE_FLUSH,null);
            }
        });

        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }

    }

    private void initModel() {
        StorageService.unpack(this, "model-it", "model",
                (model) -> {
                    this.model = model;
                    //setUiState(STATE_READY);
                    resultView.setText("Sono pronto!");
                    recognizeMicrophone();
                },
                (exception) -> {
                    Log.e(TAG, "Failed to unpack the model: " + exception.getMessage());
                    //setErrorState("Failed to unpack the model " + exception.getMessage());
                }
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                initModel();
            } else {
                finish();
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

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    private String lastRecognizedText = "";

    @Override
    public void onResult(String hypothesis) {
        try {
            JSONObject jsonResult = new JSONObject(hypothesis);
            String textValue = jsonResult.optString("text", "").trim();

            if (!textValue.isEmpty() && !textValue.equals(lastRecognizedText)) {

                result += textValue + "\n";
                resultView.setText(result);

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
            }
        }
    }

    @Override
    public void onFinalResult(String hypothesis) {
    }

    @Override
    public void onPartialResult(String hypothesis) {
    }

    @Override
    public void onError(Exception e) {
        //setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        //setUiState(STATE_DONE);
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            //setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            //etUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                //setErrorState(e.getMessage());
            }
        }
    }

}
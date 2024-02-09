package com.ubtrobot.mini.sdkdemo;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class TTSActivity extends AppCompatActivity implements RecognitionListener {
    private static final String TAG = "TTS";
    private TextToSpeech textToSpeech;
    private Model model;
    private SpeechService speechService;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    private String lastRecognizedText = "";
    private TextView risultato;
    private TextView pronto;
    private MediaRecorder mediaRecorder;
    private String audioFilePath;

    private void startRecording() {
        if (!permissionToRecordAccepted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        File audioFile = null;
        try {
            audioFile = createAudioFile();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create audio file: " + e.getMessage());
        }

        if (audioFile != null) {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());

            try {
                mediaRecorder.prepare();
                mediaRecorder.start();
                audioFilePath = audioFile.getAbsolutePath();
            } catch (IOException e) {
                Log.e(TAG, "prepare() failed: " + e.getMessage());
            }
        }
    }

    private void stopRecordingAndSaveFile() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Log.d(TAG, "Audio saved to: " + audioFilePath);
        }
    }

    private File createAudioFile() throws IOException {
        String fileName = "AUDIO_" + System.currentTimeMillis();
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File audio = File.createTempFile(
                fileName,  /* prefix */
                ".3gp",         /* suffix */
                storageDir      /* directory */
        );
        return audio;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts);
        risultato = findViewById(R.id.esito);
        pronto = findViewById(R.id.pronto);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.ITALY);
            }
        });
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (permissionToRecordAccepted) {
                initModel();
            } else {
                finish();
            }
        }
    }

    private void initModel() {
        StorageService.unpack(this, "model-it", "model",
                (model) -> {
                    this.model = model;
                    recognizeMicrophone();
                    pronto.setText("si può parlare!");
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
    public void onPartialResult(String s) {

    }

    @Override
    public void onResult(String s) {
        try {
            JSONObject jsonResult = new JSONObject(s);
            String textValue = jsonResult.optString("text", "").trim();

            if (!textValue.equals(lastRecognizedText) && textValue.contains("ehi mario")){

                risultato.setText(textValue);
                speechService.stop();
                speechService = null;
                // Crea un ToneGenerator passando il tipo di stream e il volume
                ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                // Suona il tono per 150 ms
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150);
                textToSpeech.speak("Ciao, come posso aiutarti?", TextToSpeech.QUEUE_FLUSH, null);
                while(textToSpeech.isSpeaking()){

                }
                startRecording();

                stopRecordingAndSaveFile();
                
                new SendAudioFileTask().execute(audioFilePath);

                lastRecognizedText = textValue;
            }

        } catch (JSONException e) {
            Log.e(TAG, "Errore durante il parsing JSON: " + e.getMessage());
        ;}
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

    private class SendAudioFileTask extends AsyncTask<String, Void, AgentResponse> {
        @Override
        protected AgentResponse doInBackground(String... params) {
            return sendAudioFileToServer(params[0]);
        }

        @Override
        protected void onPostExecute(AgentResponse result) {
            if (result != null) {
                // Aggiorna l'interfaccia utente con la risposta qui
                Log.e(TAG, "Risposta dall'API: " + result.getAnswer());
                risultato.setText(result.getAnswer());
                textToSpeech.speak(result.getAnswer(), TextToSpeech.QUEUE_FLUSH, null);
                while(textToSpeech.isSpeaking()){

                }
                recognizeMicrophone();
            }
        }
    }

    public AgentResponse sendAudioFileToServer(String audioFilePath) {
        HttpURLConnection urlConnection = null;
        DataOutputStream dos = null;
        FileInputStream fis = null;
        try {
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****";

            URL url = new URL("http://10.0.2.2:8000/upload-audio");
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
}

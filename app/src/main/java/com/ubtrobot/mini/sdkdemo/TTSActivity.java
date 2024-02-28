package com.ubtrobot.mini.sdkdemo;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TTSActivity extends AppCompatActivity implements RecognitionListener {
    private static final String TAG = "TTS";
    private TextToSpeech textToSpeech;
    private Model model;
    private SpeechService speechService;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    private String lastRecognizedText = "";
    private AudioRecord audioRecord;
    private String audioFilePath;
    private boolean isRecording = false;
    private double silenceThreshold = 2.0234091650872004E-4;
    private double energy;
    private int bufferSize = 0;
    private Thread recordingThread;
    private ByteArrayOutputStream audioDataBuffer = new ByteArrayOutputStream();
    private boolean first = true;
    private boolean isSpeaking = false;
    private final OkHttpClient client = new OkHttpClient();
    private int counter = 0;

    public void startRecording() {
        bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);

        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.startRecording();
            isRecording = true;

            Log.d("Registrazione:", "ON");

            processAudioData();
            detectSilence();

        } else {
            Log.e("SilenceDetection", "Failed to initialize AudioRecord");
        }
    }

    private void processAudioData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                short[] audioBuffer = new short[bufferSize];
                byte[] byteBuffer = new byte[bufferSize * 2]; // due byte per ogni short

                while (isRecording) {
                    int bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                    if (bytesRead > 0) {
                        // Converti shorts in bytes
                        ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(audioBuffer);

                        audioDataBuffer.write(byteBuffer, 0, bytesRead * 2); // * 2 perché ogni short è due byte
                        energy = calculateEnergy(audioBuffer, bytesRead);
                        Log.d("SilenceDetection", "Energy: " + energy);
//                Log.d("SilenceDetection", "Energy: " + energy);
//                if (energy < silenceThreshold) {
//                    // Silence detected, stop recording
//
//
//                    break;
//                }
                    }
                }
            }
        }).start();
    }

    public void stopRecording() {
        if (isRecording) {
            isRecording = false;
            first = true;
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                try {
                    Log.d("Registrazione:", "OFF");
                    saveRecording();
                } catch (IOException e) {
                    Log.e("AudioRecorder", "Failed to save recording", e);
                }
            }
            recordingThread = null;
        }
    }

    private void saveRecording() throws IOException {
        File file = createAudioFile(); // Ora crea un file .wav
        byte[] audioBytes = audioDataBuffer.toByteArray();

        try (FileOutputStream out = new FileOutputStream(file)) {
            // Scrivi l'intestazione WAV
            writeWavHeader(out, (short) 1, 44100, (short) 16, audioBytes.length);

            // Scrivi i dati audio PCM
            out.write(audioBytes);
        }
        audioDataBuffer.reset(); // Clear the buffer after saving

        // Dopo aver salvato il file, prova ad inviarlo al server
        if (isDeviceConnectedToInternet()) {
            sendAudioFileToServer(audioFilePath);
        } else {
            Log.e(TAG, "Dispositivo non connesso a Internet");
        }
    }

    private void writeWavHeader(FileOutputStream out, short channels, int sampleRate, short bitDepth, int dataSize) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(44);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + dataSize); // Dimensione del file meno l'intestazione
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16); // Sub-chunk size, 16 for PCM
        buffer.putShort((short) 1); // Audio format, 1 for PCM
        buffer.putShort(channels);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * channels * (bitDepth / 8)); // Byte rate
        buffer.putShort((short) (channels * (bitDepth / 8))); // Block align
        buffer.putShort(bitDepth);
        buffer.put("data".getBytes());
        buffer.putInt(dataSize); // Data chunk size

        out.write(buffer.array());
    }

    private File createAudioFile() throws IOException {
        counter++;
        String fileName = "AUDIO_" + counter + ".wav"; // Modificato per usare l'estensione .wav

        File mediaStorageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "MyAudioApp");
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + mediaStorageDir.getAbsolutePath());
        }

        File audioFile = new File(mediaStorageDir, fileName);
        audioFilePath = audioFile.getAbsolutePath();
        Log.d("AudioRecorder", "File path: " + audioFilePath);
        return audioFile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts);

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.ITALY);
                }
            }
        });
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Called when the TTS starts speaking
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS finished speaking. Attempting to restart speech recognition.");
                runOnUiThread(() -> {
                    if (speechService != null) {
                        Log.d(TAG, "Stopping existing SpeechService before restarting.");
                        speechService.stop();
                        speechService = null;
                    }
                    recognizeMicrophone();
                });
            }

            @Override
            public void onError(String utteranceId) {
            }
        });

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        OfflineDialogClass.loadQuestionAnswer(this);
    }

    // In qualche punto del tuo codice, quando vuoi far parlare il TTS e poi continuare con il riconoscimento vocale:
    public void speakAndListen(String text) {
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");

        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        // Non c'è bisogno di chiamare qui recognizeMicrophone(); verrà chiamato automaticamente quando TTS finisce di parlare.
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

    public boolean isDeviceConnectedToInternet() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    private void initModel() {
        StorageService.unpack(this, "model-it", "model",
                (model) -> {
                    this.model = model;
                    textToSpeech.speak("Sono pronto!", TextToSpeech.QUEUE_FLUSH, null);
                    while (textToSpeech.isSpeaking()) {

                    }
                    recognizeMicrophone();
                },
                (exception) -> {
                    Log.e(TAG, "Failed to unpack the model: " + exception.getMessage());
                }
        );
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            Log.d(TAG, "SpeechService is already initialized, stopping it.");
            speechService.stop();
            speechService = null;
        }
        try {
            Log.d(TAG, "Initializing new SpeechService for recognition.");
            Recognizer rec = new Recognizer(model, 16000.0f);
            speechService = new SpeechService(rec, 16000.0f);
            speechService.startListening(this);
            Log.d("Riconoscimento Vocale", "SpeechService started listening.");
        } catch (IOException e) {
            Log.e(TAG, "Error initializing recognizer: " + e.getMessage(), e);
        }
    }

    private void detectSilence() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                short[] audioBuffer = new short[1024];
                while (isRecording) {
                    try {
                        if(first) {
                            Thread.sleep(4000);
                            first = false;
                        } else {
                            Thread.sleep(1000);
                        }
                        Log.d("jgisdljgdlfkjg", "gjdkfjgfdkl");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (energy < silenceThreshold) {
                        // Silence detected, stop recording
                        stopRecording();
                        Log.d("SilenceDetection", "Silence detected");
                        break;
                    }
                }
            }
        }).start();
    }

    private double calculateEnergy(short[] audioData, int length) {
        double energy = 0.0;
        for (int i = 0; i < length; i++) {
            energy += Math.pow(audioData[i] / 32768.0, 2); // Normalize and square each sample
        }
        return energy / length;
    }

    @Override
    public void onPartialResult(String s) {

    }

    @Override
    public void onResult(String s) {
        try {
            JSONObject jsonResult = new JSONObject(s);
            String textValue = jsonResult.optString("text", "").trim();

            Log.d("Riconoscimento Vocale", "Testo riconosciuto: " + textValue);

            if (/*!textValue.equals(lastRecognizedText) && */ textValue.contains("ehi mario")){
                if(isDeviceConnectedToInternet()) {
                    speechService.stop();
                    speechService = null;
                    ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150);
                    //lastRecognizedText = "";
                    startRecording();
                } else {
                    String res = OfflineDialogClass.getResponse(textValue);
                    textToSpeech.speak(res, TextToSpeech.QUEUE_FLUSH, null);
                }
                //lastRecognizedText = textValue;
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

    public void sendAudioFileToServer(String audioFilePath) {
        File audioFile = new File(audioFilePath);
        String url = "http://172.20.10.6:8000/upload-audio"; // Inserisci l'indirizzo IP del PC in cui è in esecuzione il server

        // Prepara il corpo della richiesta
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("auth_id", "ALPHA-MINI-10F5-PRWE-U9YV-ADUQ")
                .addFormDataPart("file", audioFile.getName(),
                        RequestBody.create(MediaType.parse("audio/wav"), audioFile))
                .build();

        // Crea la richiesta
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        // Invia la richiesta in modo asincrono
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                // Gestisci il fallimento qui
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Risposta non riuscita: " + response.body().string());
                } else {
                    String responseData = response.body().string();
                    Log.d(TAG, "Risposta ricevuta: " + responseData);

                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        JSONObject responseObj = jsonResponse.optJSONObject("risposta");
                        String output = responseObj != null ? responseObj.optString("output", "Nessuna risposta trovata.") : "Nessuna risposta trovata.";

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                speakAndListen(output); // sostituisci "output" con la stringa che vuoi far leggere al TTS
                                // recognizeMicrophone verrà riavviato nel onDone del UtteranceProgressListener
                            }
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Errore nell'elaborazione della risposta.", Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
    }

/*
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
 */
}

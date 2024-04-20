package com.ubtrobot.mini.sdkdemo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.gson.Gson;
import com.ubtechinc.skill.SkillHelper;
import com.ubtrobot.transport.message.CallException;
import com.ubtrobot.transport.message.ResponseCallback;

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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TTSService extends Service implements RecognitionListener {
    private static final String CHANNEL_ID = "SpeechService";
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
    private ByteArrayOutputStream audioDataBuffer = new ByteArrayOutputStream();
    private boolean first = true;
    private boolean isSpeaking = false;
    private final OkHttpClient client = new OkHttpClient();
    private int counter = 0;
    private Context context = this;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
            Thread recordingThread = null;
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
    public void onCreate() {
        super.onCreate();

        startForeground(1, createNotification());

        Toast.makeText(this, "TTS Service", Toast.LENGTH_SHORT);

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

                Thread th = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (speechService != null) {
                            Log.d(TAG, "Stopping existing SpeechService before restarting.");
                            speechService.stop();
                            speechService = null;
                        }
                        recognizeMicrophone();
                    }
                });

                th.start();
            }

            @Override
            public void onError(String utteranceId) {
            }
        });

        //ActivityCompat.requestPermissions(context, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        OfflineDialogClass.loadQuestionAnswer(this);
        initModel();
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

    private Notification createNotification() {
        // Create a notification channel for Android Oreo and above
        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Your Service Title")
                .setContentText("Your Service is running")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // You can customize this further based on your needs

        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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

    public void speakAndListen(String text) {
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");

        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        // Non c'è bisogno di chiamare qui recognizeMicrophone(); verrà chiamato automaticamente quando TTS finisce di parlare.
    }

    public boolean isDeviceConnectedToInternet() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    @Override
    public void onPartialResult(String s) {

    }

    @NonNull private ResponseCallback getListener() {
        return new ResponseCallback() {
            @Override public void onResponse(com.ubtrobot.transport.message.Request request, com.ubtrobot.transport.message.Response response) {
                Log.i(TAG, "start success.");
            }

            @Override public void onFailure(com.ubtrobot.transport.message.Request request, CallException e) {
                Log.i(TAG, e.getMessage());
            }
        };
    }

    public void dancing() {
        SkillHelper.startSkillByIntent("dancing", null, getListener());
    }

    @Override
    public void onResult(String s) {
        ActivityReader.readActivitiesFromFile(this);

        try {
            JSONObject jsonResult = new JSONObject(s);
            String textValue = jsonResult.optString("text", "").trim();

            Log.d("Riconoscimento Vocale", "Testo riconosciuto: " + textValue);

            if (textValue.contains("ehi mario")) {

                String action = ActivityReader.getAction(textValue);

                if(action.compareTo("") != 0) {
                    SkillHelper.startSkillByIntent(action, null, getListener());
                    Log.d("fjdskgdfgfd", "fgsddfgfdgfgfdgfdgdfgfdfsd");
                } else {
                    if (isDeviceConnectedToInternet()) {
                        speechService.stop();
                        speechService = null;
                        ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                        toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150);
                        //lastRecognizedText = "";
                        startRecording();
                    } else {
                        if (!textValue.equals("ehi mario")) {
                            String res = OfflineDialogClass.getResponse(textValue);
                            textToSpeech.speak(res, TextToSpeech.QUEUE_FLUSH, null);
                        }
                    }
                }
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

    public void sendAudioFileToServer(String audioFilePath) {
        File audioFile = new File(audioFilePath);
        String url = "http://192.168.245.198:8000/upload-audio"; // Inserisci l'indirizzo IP del PC in cui è in esecuzione il server

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
                Log.d(TAG, "Non ho ricevuto nulla.");

                Thread th = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        speakAndListen("Non riesco a rispondere");
                    }
                });

                th.start();
                Thread th2 = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (speechService != null) {
                            Log.d(TAG, "Stopping existing SpeechService before restarting.");
                            speechService.stop();
                            speechService = null;
                        }
                        recognizeMicrophone();
                    }
                });

                th2.start();

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

                        Thread th = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                speakAndListen(output);
                            }
                        });

                        th.start();

//                        mainHandler.post(() -> {
//                            speakAndListen(output); // sostituisci "output" con la stringa che vuoi far leggere al TTS
//                            // recognizeMicrophone verrà riavviato nel onDone del UtteranceProgressListener
//                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                        mainHandler.post(() -> Toast.makeText(getApplicationContext(), "Errore nell'elaborazione della risposta.", Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
    }
}

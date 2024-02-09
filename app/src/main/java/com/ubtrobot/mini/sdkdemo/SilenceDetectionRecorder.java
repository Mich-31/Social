package com.ubtrobot.mini.sdkdemo;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class SilenceDetectionRecorder extends AppCompatActivity {

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private double silenceThreshold = 0.1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        startRecording();
    }

    public void startRecording() {
        int bufferSize = AudioRecord.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );

        audioRecord.startRecording();
        isRecording = true;

        // Start silence detection loop
        detectSilence();
    }

    public void stopRecording() {
        if (audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void detectSilence() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                short[] audioBuffer = new short[1024];
                while (isRecording) {
                    int bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                    if (bytesRead > 0) {
                        double energy = calculateEnergy(audioBuffer, bytesRead);
                        Log.d("SilenceDetection", "Energy: " + energy);
                        if (energy < silenceThreshold) {
                            // Silence detected, stop recording
                            stopRecording();
                            Log.d("SilenceDetection", "Silence detected");
                            break;
                        }
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
}

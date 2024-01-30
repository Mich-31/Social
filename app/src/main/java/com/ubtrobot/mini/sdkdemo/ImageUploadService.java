package com.ubtrobot.mini.sdkdemo;

import android.Manifest;
import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

public class ImageUploadService extends IntentService {

    private static final String TAG = "ImageUploadService";

    public ImageUploadService() {
        super("ImageUploadService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            // Verifica le autorizzazioni
            if (checkCameraPermissions()) {
                // Inizializza la fotocamera
                Camera camera = getCameraInstance();
                if (camera != null) {
                    try {
                        // Cattura la foto
                        camera.takePicture(null, null, pictureCallback);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // Rilascia la fotocamera
                        camera.release();
                    }
                }
            }
        }
    }

    private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            // Crea un file per la foto
            File pictureFile = getOutputMediaFile();
            if (pictureFile != null) {
                try {
                    // Salva la foto sul file
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();

                    // Invia l'immagine al server
                    sendImageToServer(pictureFile);

                } catch (IOException e) {
                    Log.e(TAG, "Error saving picture: " + e.getMessage());
                }
            }

            // Rilancia la preview per consentire la cattura di un'altra foto
            camera.startPreview();
        }
    };

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CameraApp");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
                return null;
            }
        }

        return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + System.currentTimeMillis() + ".jpg");
    }

    private void sendImageToServer(File imageFile) {
        AgentRequest request = new AgentRequest();
        request.setRobot_id("ROBOT-0001");
        request.setAuth_id("ALPHA-MINI-10F5-PRWE-U9YV-ADUQ");

        try {
            // Converti l'immagine in byte array
            byte[] imageData = new byte[0];
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                imageData = Files.readAllBytes(imageFile.toPath());
                request.setImage(imageData);

                // Invia la richiesta al server
                sendPostRequest(request);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendPostRequest(AgentRequest requestObj) {
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL("https://alpha-mini.azurewebsites.net/upload-image");
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "image/jpeg");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setDoOutput(true);

            // Invia l'immagine
            try (OutputStream os = urlConnection.getOutputStream()) {
                os.write(requestObj.getImage());
                os.flush();
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(urlConnection.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                // Gestisci la risposta del server se necessario
                Log.e(TAG, "Risposta dall'API: " + response.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open();
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera: " + e.getMessage());
        }
        return c;
    }

    private boolean checkCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permissions not granted");
            return false;
        }
        return true;
    }

    public class AgentRequest {
        String auth_id;
        String robot_id;
        byte[] image;

        public String getAuth_id() {
            return auth_id;
        }

        public void setAuth_id(String auth_id) {
            this.auth_id = auth_id;
        }

        public byte[] getImage() {
            return image;
        }

        public void setImage(byte[] image) {
            this.image = image;
        }

        public String getRobot_id() {
            return robot_id;
        }

        public void setRobot_id(String robot_id) {
            this.robot_id = robot_id;
        }
    }
}

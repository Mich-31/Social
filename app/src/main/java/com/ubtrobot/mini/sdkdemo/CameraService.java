package com.ubtrobot.mini.sdkdemo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Locale;

public class CameraService extends Service {
    private static final String TAG = "MyService";
    private static final String CHANNEL_ID = "CameraService";
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    @Override
    public void onCreate() {
        super.onCreate();
        // Create a notification channel for Android Oreo and higher

        Toast.makeText(this, "My Service Camera Started", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onStart");

        createNotificationChannel();

        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            // If permission is not granted, show a notification to request it
            showPermissionNotification();
        } else {
            // Permission is granted, proceed with initialization
            camera = getCameraInstance();

            //surfaceHolder = surfaceView.getHolder();
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // Deprecated in API level 11, but needed for older devices
            if (camera != null) {
                try {
                    Log.d("hgjdfgfdge", "gfgdfd");
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

//    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
//        @Override
//        public void surfaceCreated(SurfaceHolder holder) {
//            try {
//                camera.setPreviewDisplay(holder);
//                camera.startPreview();
//            } catch (IOException e) {
//                Log.e(TAG, "Error setting up preview: " + e.getMessage());
//            }
//        }
//
//
//        @Override
//        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
//
//        }
//
//        @Override
//        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
//            if (camera != null) {
//                camera.stopPreview();
//                camera.release();
//                camera = null;
//            }
//        }
//    }


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
                .setContentText("Please grant CAMERA permission")
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
    public void onDestroy() {
        Toast.makeText(this, "My Service Stopped", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onDestroy");
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

    private Camera.PictureCallback pictureCallback = (data, camera) -> {
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

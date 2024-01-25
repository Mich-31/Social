package com.ubtrobot.mini.sdkdemo;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Base64;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private Camera camera;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private int counter;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cam_activity);

        surfaceView = findViewById(R.id.surfaceView);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
        } else {
            initializeCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to use the camera.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initializeCamera() {
        camera = getCameraInstance();
        if (camera == null) {
            Toast.makeText(this, "Unable to access the camera.", Toast.LENGTH_SHORT).show();
            finish();
        }

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(surfaceCallback);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // Deprecated in API level 11, but needed for older devices
    }

    private Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // Attempt to get a Camera instance
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera: " + e.getMessage());
        }
        return c; // Returns null if the camera is unavailable
    }

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            } catch (IOException e) {
                Log.e(TAG, "Error setting up preview: " + e.getMessage());
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // No need to handle surface changes for this example
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Release the camera when the surface is destroyed
            if (camera != null) {
                camera.stopPreview();
                camera.release();
                camera = null;
            }
        }
    };

    // Call this method to capture a photo
    public void capturePhoto(View view) {
        if (camera != null) {
            camera.takePicture(null, null, pictureCallback);
        }
    }

    private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File pictureFile = getOutputMediaFile();
            if (pictureFile == null) {
                Log.e(TAG, "Error creating media file, check storage permissions.");
                return;
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    byte[] imageData = Files.readAllBytes(pictureFile.toPath());
                    sendImageToServer(imageData);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                Toast.makeText(CameraActivity.this, "Photo saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "Error saving picture: " + e.getMessage());
            }

            // Restart the preview to allow for another photo to be taken
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

        counter++;
        return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + counter + ".jpg");
    }

    private void sendImageToServer(byte[] imageData) {
        AgentRequest request = new AgentRequest();
        request.setRobot_id("ROBOT-0001");
        request.setAuth_id("ALPHA-MINI-10F5-PRWE-U9YV-ADUQ");

        request.setImage(imageData);


        // Invia la richiesta al server
        new SendPostRequestTask().execute(request);
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
                Log.e(TAG, "Risposta dall'API: " + result.getAnswer());
            }
        }
    }
}


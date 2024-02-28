package com.ubtrobot.mini.sdkdemo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

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
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class ImageCaptureActivity extends AppCompatActivity {

    private static final String TAG = "ImageCaptureActivity";
    private CameraManager cameraManager;
    private Handler handler;
    private String imageFilePath;
    private String cameraId;
    private Context context = this;
    private Timer captureTimer;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private static final long CAPTURE_INTERVAL = 10000; // 10 seconds

    private int counter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_capture); // Replace with your layout file

        // Check for camera permissions
        if (checkCameraPermissions()) {
            // Initialize the capture timer
            captureTimer = new Timer();
            captureTimer.schedule(new CaptureTask(), 0, CAPTURE_INTERVAL);
        } else {
            // Handle permission request or inform the user
            Toast.makeText(this, "Camera permissions not granted", Toast.LENGTH_SHORT).show();
        }
    }

    private class CaptureTask extends TimerTask {
        @Override
        public void run() {
            // Check for camera permissions
            if (checkCameraPermissions()) {
                // Initialize the camera
                cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (cameraManager != null) {
                    try {
                        // Open the camera if not already open
                        if (cameraDevice == null) {
                            cameraId = getCameraId();
                            HandlerThread handlerThread = new HandlerThread("BackgroundThread");
                            handlerThread.start();
                            Looper looper = handlerThread.getLooper();
                            handler = new Handler(looper);

                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    != PackageManager.PERMISSION_GRANTED) {
                                // TODO: Handle permission request
                                return;
                            }

                            // Open the camera and keep it open for subsequent captures
                            cameraManager.openCamera(cameraId, cameraStateCallback, handler);
                        } else {
                            // Camera is already open, proceed to capture the photo
                            createCaptureSessionAndStartCapture();
                        }
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private boolean checkCameraPermissions() {
        // Check and request camera permissions if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Request camera permissions
            // (You should handle the permission request and callback)
            return false;
        }
        return true;
    }

    private String getCameraId() throws CameraAccessException {
        // Get the list of available cameras
        String[] cameraIds = cameraManager.getCameraIdList();

        // Choose the desired camera (you may need to implement logic for this)
        if (cameraIds.length > 0) {
            return cameraIds[0]; // Choose the first available camera
        } else {
            throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "No cameras available");
        }
    }

    // Other methods (checkCameraPermissions, getCameraId, cameraStateCallback, createCaptureSessionAndStartCapture, etc.) remain the same as in the service.

    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // Camera opened successfully, save the reference
            ImageCaptureActivity.this.cameraDevice = cameraDevice;

            // Log for verifying that the camera is opened successfully
            Log.d(TAG, "Camera opened successfully");

            // Initialize the capture session and start capturing
            createCaptureSessionAndStartCapture();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            // Handle camera disconnection
            cameraDevice.close();
            ImageCaptureActivity.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            // Handle camera errors
            cameraDevice.close();
            ImageCaptureActivity.this.cameraDevice = null;

            // Log for viewing the error
            Log.e(TAG, "Camera error: " + error);
        }
    };

    private void createCaptureSessionAndStartCapture() {
        try {
            // Create an ImageReader to get the image data
            ImageReader imageReader = ImageReader.newInstance(
                    512, 512, ImageFormat.JPEG, 1);

            // Create a CaptureRequest for still capture
            CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            // Create a file to save the captured photo
            imageFilePath = getOutputMediaFile().getAbsolutePath();
            counter++;

            // Set the image capture callback
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    // Save the image data to a file
                    saveImageToFile(image);

                    // Log for verifying that the image is saved successfully
                    Log.d(TAG, "Image saved successfully");

                    // Close the image reader
                    imageReader.close();


                }
            }, handler);

            // Reconfigure the capture session for the still capture
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                // Start the capture session
                                session.capture(captureBuilder.build(), null, null);
                                // Keep the capture session reference for later use
                                captureSession = session;
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            // Handle configuration failure
                            // Log for viewing the configuration failure
                            Log.e(TAG, "Capture session configuration failed");
                        }
                    }, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "CameraApp");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
                return null;
            }
        }

        return new File(mediaStorageDir.getPath() + File.separator +
                "IMG_" + counter + ".jpg");
    }

    private void saveImageToFile(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        try (OutputStream output = new FileOutputStream(imageFilePath)) {
            output.write(bytes);
            Log.d(TAG, "Image saved to: " + imageFilePath);

            new SendImageFileTask().execute(imageFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class SendImageFileTask extends AsyncTask<String, Void, AgentResponse> {
        @Override
        protected AgentResponse doInBackground(String... params) {
            // Implementa la logica di invio del file qui
            return sendImageFileToServer(params[0]);
        }

        @Override
        protected void onPostExecute(AgentResponse result) {
            // Gestisci qualsiasi azione post-invio, come mostrare un messaggio all'utente
            Log.d("Risposta server", result.getAnswer());
        }
    }

    public AgentResponse sendImageFileToServer(String imagePath) {
        HttpURLConnection urlConnection = null;
        DataOutputStream dos = null;
        FileInputStream fis = null;
        try {
            URL url = new URL("http://172.20.10.6:8000/upload-image");
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setUseCaches(false);
            urlConnection.setRequestProperty("Connection", "Keep-Alive");
            urlConnection.setRequestProperty("ENCTYPE", "multipart/form-data");

            String boundary = "*****";
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            urlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

            dos = new DataOutputStream(urlConnection.getOutputStream());

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"auth_id\"" + lineEnd + lineEnd + "ALPHA-MINI-10F5-PRWE-U9YV-ADUQ" + lineEnd);
            dos.writeBytes(twoHyphens + boundary + lineEnd);

            // Nome del file
            String fileName = new File(imagePath).getName();

            // Header del file
            dos.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"" + fileName + "\"" + lineEnd);
            dos.writeBytes("Content-Type: " + URLConnection.guessContentTypeFromName(fileName) + lineEnd + lineEnd);

            // Prepara il file da inviare
            fis = new FileInputStream(imagePath);
            int bytesAvailable = fis.available();
            int bufferSize = Math.min(bytesAvailable, 1 * 1024 * 1024);
            byte[] buffer = new byte[bufferSize];

            // Leggi il file e scrivilo nella richiesta
            int bytesRead = fis.read(buffer, 0, bufferSize);
            while (bytesRead > 0) {
                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fis.available();
                bufferSize = Math.min(bytesAvailable, 1 * 1024 * 1024);
                bytesRead = fis.read(buffer, 0, bufferSize);
            }
            dos.writeBytes(lineEnd);

            // Fine parti
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            // Ottieni la risposta
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Converti la risposta in oggetto
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel the timer when the activity is destroyed
        if (captureTimer != null) {
            captureTimer.cancel();
            captureTimer.purge();
        }
    }

    // Other lifecycle methods (onResume, onPause) can be implemented based on your requirements.

}

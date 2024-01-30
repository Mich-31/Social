package com.ubtrobot.mini.sdkdemo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.JobIntentService;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class ImageCaptureJobService extends Service {
    private static final String TAG = "ImageCaptureJobService";
    private CameraManager cameraManager;
    private Handler handler;
    private String imageFilePath;
    private String cameraId;
    private Context context = this;
    private Timer captureTimer;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private static final String CHANNEL_ID = "CameraService";
    private static final long CAPTURE_INTERVAL = 5000; // 1 second

    @Override
    public void onCreate() {
        // Check for camera permissions
            // ... your existing code ...

            // Initialize the capture timer
            captureTimer = new Timer();
            captureTimer.schedule(new CaptureTask(), 0, CAPTURE_INTERVAL);

            // Start the service as a foreground service
            startForeground(2, createNotification());
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Your Channel Name";
            String description = "Your Channel Description";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
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
                            handlerThread.start(); // This will start the thread and its looper
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Cancel the timer when the service is destroyed
        if (captureTimer != null) {
            captureTimer.cancel();
            captureTimer.purge();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
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

    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // Camera opened successfully, save the reference
            ImageCaptureJobService.this.cameraDevice = cameraDevice;

            // Log for verifying that the camera is opened successfully
            Log.d(TAG, "Camera opened successfully");

            // Initialize the capture session and start capturing
            createCaptureSessionAndStartCapture();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            // Handle camera disconnection
            cameraDevice.close();
            ImageCaptureJobService.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            // Handle camera errors
            cameraDevice.close();
            ImageCaptureJobService.this.cameraDevice = null;

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
                "IMG_" + System.currentTimeMillis() + ".jpg");
    }

    private void saveImageToFile(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        try (OutputStream output = new FileOutputStream(imageFilePath)) {
            output.write(bytes);
            Log.d(TAG, "Image saved to: " + imageFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
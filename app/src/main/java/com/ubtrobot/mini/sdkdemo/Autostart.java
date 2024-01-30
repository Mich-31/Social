package com.ubtrobot.mini.sdkdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class Autostart extends BroadcastReceiver
{
    public void onReceive(Context context, Intent arg1)
    {
        Intent intent_tts = new Intent(context, TTSService.class);
        Intent intent_camera = new Intent(context, ImageCaptureJobService.class);
        //Intent intent_camera = new Intent(context, CameraService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent_tts);
            context.startForegroundService(intent_camera);
        } else {
            context.startService(intent_tts);
            context.startService(intent_camera);
        }
        Log.i("Autostart", "started");
    }
}
package com.ubtrobot.mini.sdkdemo;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import java.util.List;

/**
 * Created by lulin.wu on 2018/6/19.
 */

public class MainActivity extends Activity {
    private static final String TAG = DemoApp.DEBUG_TAG;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        // This is the code to find the running services
        /*ActivityManager am = (ActivityManager)this.getSystemService(Activity.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> rs = am.getRunningServices(50);

        for (int i=0; i<rs.size(); i++) {
            ActivityManager.RunningServiceInfo rsi = rs.get(i);
            Log.i("Service", "Process " + rsi.process + " with component " + rsi.service.getClassName());
        }

        //am.killBackgroundProcesses("com.ubtechinc.alphamini.speech");

        Intent intents = new Intent(getBaseContext(),SpeechToText.class);
        intents.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intents);*/

    }

    public void TTS(View view){
        Intent intent = new Intent();
        intent.setClass(this, TTSActivity.class);
        startActivity(intent);
    }

    public void actionApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, ActionApiActivity.class);
        startActivity(intent);
    }

    public void expressApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, ExpressApiActivity.class);
        startActivity(intent);
    }

    public void motorApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, MotorApiActivity.class);
        startActivity(intent);
    }

    public void faceApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, ImageCaptureActivity.class);
        startActivity(intent);
    }

    public void objectDetectApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, SilenceDetectionRecorder.class);
        startActivity(intent);
    }

    public void takePicApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, TakePicApiActivity.class);
        startActivity(intent);
    }

    public void mouthLedApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, MouthLedApiActivity.class);
        startActivity(intent);
    }

    public void sysEventApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, SysEventApiActivity.class);
        startActivity(intent);
    }

    public void sysApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, SysApiActivity.class);
        startActivity(intent);
    }

    public void standupApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, StandupApiActivity.class);
        startActivity(intent);
    }

    public void OnRobotInit(View view){
        Intent intent = new Intent(this,RobotInitActivity.class);
        startActivity(intent);
    }

    public void skillApiTest(View view) {
        startActivity(new Intent(this, SkillApiActivity.class));
    }

    public void powerApiTest(View view) {
        startActivity(new Intent(this, PowerApiActivity.class));
    }


    public void onStartLanTest(View view){
        startActivity(new Intent(this,DiscorverActivity.class));
    }

    public void phoneCallTest(View view) {
        startActivity(new Intent(this, PhoneCallApiActivity.class));
    }

    public void speechApiTest(View view) {
        startActivity(new Intent(this, SpeechToText.class));
    }

    public void voskApiTest(View view) {
        startActivity(new Intent(this, VoskActivity.class));
    }

    public void ApkInstallTest(View view) {
        startActivity(new Intent(this, ApkSilentInstallerApiActivity.class));
    }

    public void BuiltInSkillTest(View view) {
        startActivity(new Intent(this, BuiltInSkillCallTestActivity.class));
    }
}

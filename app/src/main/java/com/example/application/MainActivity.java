package com.example.application;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.application.Services.BackgroundService;

import java.util.ArrayList;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String MyPREFERENCES = "MyPrefs";
    private SharedPreferences sharedpreferences;

    private BroadcastReceiver updateGUI = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<String> samples = intent.getStringArrayListExtra("samples");
            ArrayList<String> timestamps = intent.getStringArrayListExtra("timestamps");
            if (!samples.isEmpty() && !timestamps.isEmpty()) {
                showDialog(samples, timestamps);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button monitoringButton = findViewById(R.id.monitoringButton);
        registerReceiver(updateGUI, new IntentFilter("BROADCAST"));

        sharedpreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);
        if (sharedpreferences.contains("State")) {
            String state = sharedpreferences.getString("State", null);
            if (state != null) {
                if (state.equals("Start")) {
                    monitoringButton.setText("Start");
                } else {
                    monitoringButton.setText("Stop");
                }
            }
        }

        Intent intent = new Intent(getApplicationContext(), BackgroundService.class);
        intent.addCategory(BackgroundService.TAG);

        monitoringButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                String actualState = (String) monitoringButton.getText();

                if (actualState.equals("Start")) {
                    editor.putString("State", "Stop");
                    monitoringButton.setText("Stop");
                    if (!isMyServiceRunning()) {
                        startService(intent);
                    }


                } else {
                    editor.putString("State", "Start");
                    monitoringButton.setText("Start");
                    if (isMyServiceRunning()) {
                        stopService(intent);
                    }
                }

                editor.commit();
            }
        });

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(updateGUI);
    }


    private void showDialog(ArrayList<String> samples, ArrayList<String> timestamps) {
        String s = "PREDICTED: ";
        String t = "AT TIME: ";
        for (int i = 0; i < samples.size(); i++) {
            String sample = samples.get(i);
            s += sample + ", ";
            String timestamp = timestamps.get(i);
            t += timestamp + ", ";
        }
        String message = s + "\n" + t;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("EVENTS");
        builder.setMessage(message);
        // builder.setMessage(det);
        builder.setNeutralButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.create().show();
    }

    private boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (BackgroundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }



}
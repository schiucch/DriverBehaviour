package com.example.application.Services;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.example.application.MainActivity;
import com.example.application.R;
import com.example.application.Types.Monitor;
import com.example.application.Utilities.SensorFusion;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

public class BackgroundService extends Service {

    private static final String CHANNEL_DEFAULT_IMPORTANCE = "ForegroundServiceChannel";
    public static final String TAG = "BackgroundService";

    private TimerTask monitor;
    private ArrayList<String> samples = new ArrayList<String>();
    private ArrayList<String> timestamps = new ArrayList<String>();

    IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public BackgroundService getServerInstance() {
            return BackgroundService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Interpreter modelAcc = new Interpreter(loadModelFile("modelAcc.tflite"));
            Interpreter modelDec = new Interpreter(loadModelFile("modelDec.tflite"));
            Interpreter modelRight = new Interpreter(loadModelFile("modelRight.tflite"));
            Interpreter modelLeft = new Interpreter(loadModelFile("modelLeft.tflite"));
            monitor = new Monitor(getApplicationContext(), new SensorFusion(getApplicationContext()),
                    samples, timestamps, modelAcc, modelDec, modelLeft, modelRight);

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        startAppForeground();
        if (monitor != null) {
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(monitor, 0, 100);
        }
        return START_STICKY;
    }

    private void  startAppForeground() {
        NotificationManager notificationManager;
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext(), CHANNEL_DEFAULT_IMPORTANCE);
        Intent i = new Intent(getApplicationContext(), MainActivity.class);
        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, i, 0);
        builder.setSmallIcon(R.drawable.ic_launcher_background);
        builder.setContentIntent(pendingIntent);
        builder.setContentTitle("Sta monitorando");
        builder.setContentText("Monitoring");
        builder.setPriority(Notification.PRIORITY_DEFAULT);
        notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "ForegroundID";
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Monitoring",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(channelId);
        }

        startForeground(1, builder.build());

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (monitor != null) {
            monitor.cancel();
            Intent broadcast = new Intent("BROADCAST");
            broadcast.putStringArrayListExtra("samples", samples);
            broadcast.putStringArrayListExtra("timestamps", timestamps);
            sendBroadcast(broadcast);
        }
    }

    private MappedByteBuffer loadModelFile(String fileName) throws IOException {
        MappedByteBuffer model;
        try (AssetFileDescriptor fileDescriptor = getAssets().openFd(fileName)) {
            try (FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
                try (FileChannel fileChannel = inputStream.getChannel()) {
                    long startOffset = fileDescriptor.getStartOffset();
                    long declaredLength = fileDescriptor.getDeclaredLength();
                    model = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
                }
            }
        }
        return model;
    }
}
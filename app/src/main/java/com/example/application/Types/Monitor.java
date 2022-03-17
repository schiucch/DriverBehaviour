package com.example.application.Types;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.application.MainActivity;
import com.example.application.R;
import com.example.application.Utilities.SensorFusion;

import org.tensorflow.lite.Interpreter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import androidx.core.app.NotificationCompat;


public class Monitor extends TimerTask {

    private SensorFusion sensorFusion;

    private BigDecimal time = new BigDecimal("0.0");
    private boolean detected = false;

    private ArrayList<String> samples;
    private ArrayList<String> timestamps;


    private List<float[]> sample = new ArrayList<>();
    private String label = "";
    private int sampleLength = 15;

    private float ydelta = 0.0f;
    private float xdelta = 0.0f;

    private int accel = 0;
    private int dec = 0;
    private int left = 0;
    private int right = 0;

    private List<float[]> consecutiveAcc = new ArrayList<>();
    private List<float[]> consecutiveDec = new ArrayList<>();
    private List<float[]> consecutiveLeft = new ArrayList<>();
    private List<float[]> consecutiveRight = new ArrayList<>();

    private static final String CHANNEL_DEFAULT_IMPORTANCE = "MonitorChannel";

    private Context context;

    private Interpreter modelAcc;
    private Interpreter modelDec;
    private Interpreter modelLeft;
    private Interpreter modelRight;

    public Monitor(Context context, SensorFusion sensorFusion, ArrayList<String> samples,
                   ArrayList<String> timestamps, Interpreter modelAcc, Interpreter modelDec,
                   Interpreter modelLeft, Interpreter modelRight) {

        this.context = context;
        this.sensorFusion = sensorFusion;
        this.sensorFusion.initListener();
        this.samples = samples;
        this.timestamps = timestamps;

        if (modelAcc != null && modelDec != null && modelLeft != null && modelRight != null) {
            this.modelAcc = modelAcc;
            this.modelDec = modelDec;
            this.modelLeft = modelLeft;
            this.modelRight = modelRight;
        }
    }

    @Override
    public void run() {
        if (time.floatValue() >= 1.0) {
            sensorFusion.calculateFusedOrientation();

            float yacc = sensorFusion.getAccelY();
            float xacc = sensorFusion.getAccelX();

            if (time.equals(new BigDecimal("1.0"))) {
                ydelta = yacc;
                xdelta = xacc;
            }

            float pitch = sensorFusion.getPitch();
            float yaw = sensorFusion.getYaw();

            if (!detected) {
                checkAcceleration(true, yacc, pitch, xacc, yaw);
                checkAcceleration(false, yacc, pitch, xacc, yaw);
                checkOrientation(true, xacc, yaw, yacc, pitch);
                checkOrientation(false, xacc, yaw, yacc, pitch);
                String event = eventDetected();

                if (!"".equals(event)) {
                    label = event;
                }
            } else {
                createSample();
                storeSample(xacc, yacc, yaw, pitch);
            }
        }
        time = time.add(new BigDecimal("0.1"));
    }

    private String eventDetected() {
        String event = "";
        if (accel == 5 && dec < 5 && left < 5 && right < 5) {
            event = "Acceleration";
            detected = true;
            resetCounters(event);
        } else {
            if (accel < 5 && dec == 5 && left < 5 && right < 5) {
                event = "Deceleration";
                detected = true;
                resetCounters(event);
            } else {
                if (accel < 5 && dec < 5 && left == 5 && right < 5) {
                    event = "Left";
                    detected = true;
                    resetCounters(event);
                } else {
                    if (accel < 5 && dec < 5 && left < 5 && right == 5) {
                        event = "Right";
                        detected = true;
                        resetCounters(event);
                    }
                }
            }
        }
        return event;
    }

    private void checkOrientation(boolean ifLeft, float xacc, float yaw, float yacc, float pitch) {
        float[] sensors = new float[]{yaw, pitch, xacc, yacc};
        if (ifLeft) {
            if (xacc < -1.5 + xdelta && yaw > -0.05) {
                left += 1;
                consecutiveLeft.add(sensors);
            }  else {
                left = 0;
                consecutiveLeft.clear();
            }
        } else {
            if (xacc > 1.5 + xdelta && yaw < 0.1) {
                right += 1;
                consecutiveRight.add(sensors);
            }  else {
                right = 0;
                consecutiveRight.clear();
            }
        }
    }

    private void checkAcceleration(boolean ifAccel, float yacc, float pitch, float xacc, float yaw) {
        float[] sensors = new float[]{yaw, pitch, xacc, yacc};
        if (ifAccel) {
            if (yacc > 1.5 + ydelta && pitch > 0) {
                accel += 1;
                consecutiveAcc.add(sensors);
            } else {
                accel = 0;
                consecutiveAcc.clear();
            }
        } else {
            if (yacc < -1.5 + ydelta && pitch < 0) {
                dec += 1;
                consecutiveDec.add(sensors);
            } else {
                dec = 0;
                consecutiveDec.clear();
            }
        }
    }

    private float[][] createMatrix() {
        float[][] matrix = new float[20][4];
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = sample.get(i);
        }
        return matrix;
    }

    private void storeSample(float xacc, float yacc, float yaw, float pitch) {
        if (sampleLength > 0) {
            float[] sensors = new float[]{yaw, pitch, xacc, yacc};
            sample.add(sensors);
            sampleLength -= 1;
        } else {
            float[][] matrixSample = createMatrix();
            if (modelAcc != null && modelDec != null && modelLeft != null && modelRight != null) {
                Interpreter model = getModelFromPredictedType(label);
                float[] behaviour = predictBehaviour(matrixSample, model);
                String predictedBehaviour = predictionBehaviour(behaviour);
                if ("Harsh".equals(predictedBehaviour)) {
                    samples.add(predictedBehaviour + " " + label);
                    timestamps.add(time.toString());

                    sendNotification(label, predictedBehaviour);
                }
            }
            sampleLength = 15;
            detected = false;
            resetCounters("");
        }
    }


    private void createSample() {
        if (!consecutiveAcc.isEmpty()) {
            sample.addAll(consecutiveAcc);
            consecutiveAcc.clear();

        } else {
            if (!consecutiveDec.isEmpty()) {
                sample.addAll(consecutiveDec);
                consecutiveDec.clear();
            } else {
                if (!consecutiveLeft.isEmpty()) {
                    sample.addAll(consecutiveLeft);
                    consecutiveLeft.clear();
                } else {
                    if (!consecutiveRight.isEmpty()) {
                        sample.addAll(consecutiveRight);
                        consecutiveRight.clear();
                    }
                }
            }
        }
    }

    private void resetCounters(String except) {
        if ("Acceleration".equals(except)) {
            dec = 0;
            left = 0;
            right = 0;

            consecutiveDec.clear();
            consecutiveLeft.clear();
            consecutiveRight.clear();
        } else {
            if ("Deceleration".equals(except)) {
                accel = 0;
                left = 0;
                right = 0;

                consecutiveAcc.clear();
                consecutiveLeft.clear();
                consecutiveRight.clear();
            } else {
                if ("Left".equals(except)) {
                    accel = 0;
                    dec = 0;
                    right = 0;

                    consecutiveAcc.clear();
                    consecutiveDec.clear();
                    consecutiveRight.clear();
                } else {
                    if ("Right".equals(except)) {
                        accel = 0;
                        dec = 0;
                        left = 0;

                        consecutiveAcc.clear();
                        consecutiveDec.clear();
                        consecutiveLeft.clear();
                    } else {
                        accel = 0;
                        dec = 0;
                        left = 0;
                        right = 0;

                        consecutiveAcc.clear();
                        consecutiveDec.clear();
                        consecutiveLeft.clear();
                        consecutiveRight.clear();

                        sample.clear();
                    }
                }
            }
        }

    }

    private void sendNotification(String event, String prediction) {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context.getApplicationContext(), CHANNEL_DEFAULT_IMPORTANCE);
        Intent i = new Intent(context.getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, i, 0);

        builder.setSmallIcon(R.drawable.ic_launcher_background);
        builder.setContentIntent(pendingIntent);
        builder.setContentTitle(event);
        builder.setContentText(prediction);
        builder.setPriority(Notification.PRIORITY_DEFAULT);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "MonitoringID";
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Monitoring",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(channelId);

        }

        notificationManager.notify(2, builder.build());

    }


    private Interpreter getModelFromPredictedType(String prediction) {
        if ("Acceleration".equals(prediction)) {
            return modelAcc;
        } else {
            if ("Deceleration".equals(prediction)) {
                return modelDec;
            } else {
                if ("Left".equals(prediction)) {
                    return modelLeft;
                } else {
                    return modelRight;
                }
            }
        }
    }

    private String predictionBehaviour(float[] prediction) {
        float max = -1.0f;
        int indexMax = 0;

        for (int i = 0; i < prediction.length; i++) {
            float probability = prediction[i];
            if (probability > max) {
                max = probability;
                indexMax = i;
            }
        }
        if (indexMax == 0) {
            return "Harsh";
        }
        if (indexMax == 1) {
            return "Safe";
        }
        return "";
    }

    private float[] predictBehaviour(float[][] sample, Interpreter model) {
        float[][][] input = new float[][][]{sample};
        float[][] output = new float[1][2];
        model.run(input, output);
        return output[0];
    }

}

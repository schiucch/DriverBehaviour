package com.example.application.Utilities;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class SensorFusion implements SensorEventListener {

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor magnetometer;
    private final Sensor gyroscope;
    private final MatrixOperator matrixOperation = new MatrixOperator();

    /* CONSTANTS */
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float filter_coefficient = 0.85f;
    private static final float oneMinusCoeff = 1.0f - filter_coefficient;
    private final DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ENGLISH);


    /* ACCELEROMETER PARAMETERS */
    private float[] gravity = new float[3];
    private float[] accel = new float[3];

    private float accelX;
    private float accelY;

    // accelerometer calibration
    private boolean initAccel = false;
    private int overX = 0;
    private int overY = 0;

    private float prevAccX;
    private float prevAccY;

    private float accX;
    private float accY;

    /* MAGNETOMETER PARAMETERS */
    private float[] magneticField;
    private float[] magnet = new float[3];


    /* GYROSCOPE PARAMETERS */
    final float[] gyroSpeed = new float[3]; // angular speed of gyroscope
    final float[] gyroOrientation = {0,0,0}; // and orientation
    float[] gyroRotation = new float[9]; // rotation of gyroscope

    // gyroscope calibration
    private boolean initGyro = true;
    private float timestamp;


    /* FUSED ORIENTATION PARAMETER */
    final float[] rotationMatrix = new float[9]; // accelerometer and magnetometer based rotation matrix
    final float[] accMagOrientation = new float[3]; // orientation angles from accel and magnet
    float[] fusedOrientation = new float[3];

    // PITCH, YAW AND ROLL

    private String pitch = "";
    private String roll = "";
    private String yaw = "";

    private int overYaw = 0;
    private int overPitch = 0; // counter for sensor fusion

    private int overYawQ = 0;
    private int overPitchQ = 0; //counter for quaternion

    private float getPitch = 0f;
    private float getRoll = 0f;
    private float getYaw = 0f;

    private float getPitchQ = 0f;
    private float getYawQ = 0f;

    private float newPitchOut;
    private float newRollOut;
    private float newYawOut;

    private float newPitchOutQ;
    private float newYawOutQ;

    private float mpitch;
    private float myaw;


    public SensorFusion(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    public void initListener() {
        if (accelerometer != null) {
            sensorManager.registerListener(this,
                accelerometer,
                SensorManager.SENSOR_DELAY_FASTEST);
        }

        if (gyroscope != null) {
            sensorManager.registerListener(this,
                gyroscope,
                SensorManager.SENSOR_DELAY_FASTEST);
        }

        if (magnetometer != null) {
            sensorManager.registerListener(this,
                magnetometer,
                SensorManager.SENSOR_DELAY_FASTEST);
        }

    }

    public void unregisterListener() {
        if (accelerometer != null) {
            sensorManager.unregisterListener(this, accelerometer);
        }

        if (magnetometer != null) {
            sensorManager.unregisterListener(this, magnetometer);
        }

        if (gyroscope != null) {
            sensorManager.unregisterListener(this, gyroscope);
        }
    }

    public float getPitch() {
        return newPitchOut;
    }


    public float getRoll() {
        return newRollOut;
    }


    public float getYaw() {
        return newYawOut;
    }

    public float getAccelX() {
        return accel[0];
    }

    public float getAccelY() {
        return accel[1];
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        updateValues();
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                gravity = sensorEvent.values;
                accelX = sensorEvent.values[0];
                accelY = sensorEvent.values[1];
                calibrateAccelerometer();
                System.arraycopy(sensorEvent.values, 0, accel, 0, 3);
                calculateAccMagOrientation();
                break;
            case Sensor.TYPE_GYROSCOPE:
                calibrateGyroscope(sensorEvent);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magneticField = sensorEvent.values;
                System.arraycopy(sensorEvent.values, 0, magnet, 0, 3);
                break;
            default:
                break;
        }
        quaternion();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        Log.e("Changed: ", sensor.getName());
    }

    public void calibrateAccelerometer() {
        if (!initAccel) {
            prevAccX = accelX;
            prevAccY = accelY;
            initAccel = true;
        } else {
            accX = prevAccX - accelX;
            accY = prevAccY - accelY;
            prevAccX = accelX;
            prevAccY = accelY;
        }
    }

    public void calibrateGyroscope(SensorEvent sensorEvent) {
        // don't start until first accelerometer/magnetometer orientation has been acquired
        if (accMagOrientation != null) {
            if (initGyro) {
                float[] initMatrix = matrixOperation.getRotationMatrixFromOrientation(accMagOrientation);
                float[] test = new float[3];
                SensorManager.getOrientation(initMatrix, test);
                gyroRotation = matrixOperation.matrixMultiplication(gyroRotation, initMatrix);
                initGyro = false;
            }

            float[] deltaVector = new float[4];

            if (timestamp != 0) {
                final float dT = (sensorEvent.timestamp - timestamp) * NS2S;
                System.arraycopy(sensorEvent.values, 0, gyroSpeed, 0, 3);
                matrixOperation.getRotationVectorFromGyro(gyroSpeed, deltaVector, dT / 2.0f);
            }

            timestamp = sensorEvent.timestamp;
            float[] deltaMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

            gyroRotation = matrixOperation.matrixMultiplication(gyroRotation, deltaMatrix);
            SensorManager.getOrientation(gyroRotation, gyroOrientation);


        }

    }

    public void calculateAccMagOrientation() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
            SensorManager.getOrientation(rotationMatrix, accMagOrientation);
        }
    }


    public void updateValues() {
        if (! "".equals(pitch) && ! "".equals(roll) && ! "".equals(yaw)) {

            if (newYawOut > .30 || newYawOut < -.30) {
                overYaw = overYaw + 1;
            }

            if (newPitchOut > .12 || newPitchOut < -.12) {
                overPitch = overPitch + 1;
            }

            if (newYawOutQ > .30 || newYawOutQ < -.30) {
                overYawQ = overYawQ + 1;
            }

            if (newPitchOutQ > .12 || newPitchOutQ < -.12) {
                overPitchQ = overPitchQ + 1;
            }

            if (accX > 3 || accX < -3) {
                overX = overX + 1;
            }

            if (accY > 2.5 || accY < -2.5) {
                overY = overY + 1;
            }
        }
    }


    public void quaternion() {
        float[] r = new float[9];
        float[] i = new float[9];
        if (magneticField != null && gravity != null) {
            boolean success = SensorManager.getRotationMatrix(r, i, gravity, magneticField);
            if (success) {
                float[] morientation = new float[3];
                float[] mquaternion = new float[4];
                SensorManager.getOrientation(r, morientation);
                SensorManager.getQuaternionFromVector(mquaternion, morientation);

                myaw = mquaternion[1]; // orientation contains: azimuth(yaw), pitch and Roll
                mpitch = mquaternion[2];

                newPitchOutQ = getPitchQ - mpitch;
                newYawOutQ = getYawQ - myaw;

                getPitchQ = mpitch;
                getYawQ = myaw;
            }
        }
    }

    public void calculateFusedOrientation() {
        // Azimuth
        if (gyroOrientation[0] < -0.5 * Math.PI && accMagOrientation[0] > 0.0) {
            fusedOrientation[0] = (float) (filter_coefficient * (gyroOrientation[0] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[0]);
            fusedOrientation[0] -= fusedOrientation[0] > Math.PI ? 2.0 * Math.PI : 0;
        } else {

            if (accMagOrientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0) {
                fusedOrientation[0] = (float) (filter_coefficient * gyroOrientation[0] + oneMinusCoeff * (accMagOrientation[0] + 2.0 * Math.PI));
                fusedOrientation[0] -= fusedOrientation[0] > Math.PI ? 2.0 * Math.PI : 0;
            } else {
                fusedOrientation[0] = filter_coefficient * gyroOrientation[0] + oneMinusCoeff * accMagOrientation[0];
            }
                }

        // Pitch
        if (gyroOrientation[1] < -0.5 * Math.PI && accMagOrientation[1] > 0.0) {
            fusedOrientation[1] = (float) (filter_coefficient * (gyroOrientation[1] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[1]);
            fusedOrientation[1] -= fusedOrientation[1] > Math.PI ? 2.0 * Math.PI : 0;
        } else {

            if (accMagOrientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0) {
                fusedOrientation[1] = (float) (filter_coefficient * gyroOrientation[1] + oneMinusCoeff * (accMagOrientation[1] + 2.0 * Math.PI));
                fusedOrientation[1] -= fusedOrientation[1] > Math.PI ? 2.0 * Math.PI : 0;
            } else {
                fusedOrientation[1] = filter_coefficient * gyroOrientation[1] + oneMinusCoeff * accMagOrientation[1];
            }
                  }

        // Roll
        if (gyroOrientation[2] < -0.5 * Math.PI && accMagOrientation[2] > 0.0) {
            fusedOrientation[2] = (float) (filter_coefficient * (gyroOrientation[2] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[2]);
            fusedOrientation[2] -= fusedOrientation[2] > Math.PI ? 2.0 * Math.PI : 0;
        } else {

            if (accMagOrientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0) {
                fusedOrientation[2] = (float) (filter_coefficient * gyroOrientation[2] + oneMinusCoeff * (accMagOrientation[2] + 2.0 * Math.PI));
                fusedOrientation[2] -= fusedOrientation[2] > Math.PI ? 2.0 * Math.PI : 0;
            } else {
                fusedOrientation[2] = filter_coefficient * gyroOrientation[2] + oneMinusCoeff * accMagOrientation[2];
            }
                }

        // Overwrite gyro matrix and orientation with fused orientation to comensate gyro drift
        gyroRotation = matrixOperation.getRotationMatrixFromOrientation(fusedOrientation);
        System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);

        float pitchOut = fusedOrientation[1];
        float rollOut = fusedOrientation[2];
        float yawOut = fusedOrientation[0];

        newPitchOut = getPitch - pitchOut;
        newRollOut = getRoll - rollOut;
        newYawOut = getYaw - yawOut;

        pitch = decimalFormat.format(newPitchOut * 180 / Math.PI) + "degrees / " + decimalFormat.format(newPitchOut) + " rad/s";
        roll = decimalFormat.format(newRollOut * 180 / Math.PI) + "degrees / " + decimalFormat.format(newRollOut) + " rad/s";
        yaw = decimalFormat.format(newYawOut * 180 / Math.PI) + "degrees / " + decimalFormat.format(newYawOut) + " rad/s";

        getPitch = pitchOut;
        getRoll = rollOut;
        getYaw = yawOut;


    }

}


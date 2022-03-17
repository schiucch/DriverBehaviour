package com.example.application.Utilities;

public class MatrixOperator {
    public static final float EPSILON = 0.000000001f;


    public float[] getRotationMatrixFromOrientation(float[] o) {
        float[] x = new float[9];
        float[] y = new float[9];
        float[] z = new float[9];

        float sinX = (float) Math.sin(o[1]);
        float cosX = (float) Math.cos(o[1]);
        float sinY = (float) Math.sin(o[2]);
        float cosY = (float) Math.cos(o[2]);
        float sinZ = (float) Math.sin(o[0]);
        float cosZ = (float) Math.cos(o[0]);

        // rotation about x-axis (displayPitch)
        x[0] = 1.0f;
        x[1] = 0.0f;
        x[2] = 0.0f;
        x[3] = 0.0f;
        x[4] = cosX;
        x[5] = sinX;
        x[6] = 0.0f;
        x[7] = -sinX;
        x[8] = cosX;

        // rotation about y-axis (displayRoll)
        y[0] = cosY;
        y[1] = 0.0f;
        y[2] = sinY;
        y[3] = 0.0f;
        y[4] = 1.0f;
        y[5] = 0.0f;
        y[6] = -sinY;
        y[7] = 0.0f;
        y[8] = cosY;

        // rotation about z-axis (azimuth)
        z[0] = cosZ;
        z[1] = sinZ;
        z[2] = 0.0f;
        z[3] = -sinZ;
        z[4] = cosZ;
        z[5] = 0.0f;
        z[6] = 0.0f;
        z[7] = 0.0f;
        z[8] = 1.0f;

        // rotation order is y, x, z (displayRoll, displayPitch, azimuth)
        float[] resultMatrix = matrixMultiplication(x, y);
        return matrixMultiplication(z, resultMatrix);
    }

    public float[] matrixMultiplication(float[] a, float[] b) {
        float[] result = new float[9];

        result[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
        result[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
        result[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];

        result[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
        result[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
        result[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];

        result[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
        result[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
        result[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];

        return result;
    }

    public void getRotationVectorFromGyro(float[] gyroValues,
                                          float[] deltaRotationVector,
                                          float timeFactor) {
        float[] normValues = new float[3];

        // Calculate the angular speed of the sample
        float omegaMagnitude =
                (float) Math.sqrt(gyroValues[0] * gyroValues[0] +
                        gyroValues[1] * gyroValues[1] +
                        gyroValues[2] * gyroValues[2]);

        // Normalize the rotation vector if it's big enough to get the axis
        if (omegaMagnitude > EPSILON) {
            normValues[0] = gyroValues[0] / omegaMagnitude;
            normValues[1] = gyroValues[1] / omegaMagnitude;
            normValues[2] = gyroValues[2] / omegaMagnitude;
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        float thetaOverTwo = omegaMagnitude * timeFactor;
        float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;
    }
}

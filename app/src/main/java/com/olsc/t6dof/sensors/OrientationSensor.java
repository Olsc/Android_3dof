package com.olsc.t6dof.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.view.Surface;

/**
 * 负责处理设备传感器数据以计算设备的 3D 姿态。
 * 优先使用旋转矢量传感器（Rotation Vector），不可用时回退到加速度计 + 磁力计方案。
 */
public class OrientationSensor implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private boolean useRotationVector = false;
    private final float[] rotationMatrix = new float[16];
    private final float[] orientation = new float[3];

    private final float[] gravity = new float[3];
    private final float[] geomagnetic = new float[3];
    private boolean hasGravity = false;
    private boolean hasGeomagnetic = false;

    private boolean ready = false; // 传感器是否已生成有效的姿态数据

    public OrientationSensor() {
        // 初始化旋转矩阵为单位矩阵
        Matrix.setIdentityM(rotationMatrix, 0);
    }

    /**
     * 注册传感器监听器并开始获取数据。
     */
    public void start(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // 优先尝试获取旋转矢量传感器（系统融合方案，精度最高且无漂移）
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (rotationSensor != null) {
            useRotationVector = true;
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            // 如果不可用，则回退到使用加速度计和磁力计的手动计算方案
            useRotationVector = false;
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
            if (magnetometer != null) {
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    /**
     * 注销监听器并释放资源。
     */
    public void stop() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    /**
     * 检查传感器是否已经产生了第一个有效读数。
     */
    public boolean isReady() {
        return ready;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // 直接获取旋转矢量映射的矩阵
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            if (!ready) {
                ready = true;
                android.util.Log.d("OrientationSensor", "传感器就绪（旋转矢量模式）");
            }
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            hasGravity = true;
            updateRotationMatrix();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, 3);
            hasGeomagnetic = true;
            updateRotationMatrix();
        }
    }

    /**
     * 当同时拥有重力和地磁数据时，手动更新旋转矩阵。
     */
    private void updateRotationMatrix() {
        if (hasGravity && hasGeomagnetic) {
            float[] R = new float[16];
            float[] I = new float[16];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                System.arraycopy(R, 0, rotationMatrix, 0, 16);
                if (!ready) {
                    ready = true;
                    android.util.Log.d("OrientationSensor", "传感器就绪（加速度+磁力计模式）");
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 暂时无需处理精度变化
    }

    /**
     * 获取当前的旋转矩阵。
     */
    public float[] getRotationMatrix() {
        return rotationMatrix;
    }

    /**
     * 获取当前设备的欧拉角 [方位角, 俯仰角, 滚转角]。
     */
    public float[] getOrientation() {
        SensorManager.getOrientation(rotationMatrix, orientation);
        return orientation;
    }

    /**
     * 根据当前屏幕旋转角度（横屏/竖屏）调整传感器旋转矩阵。
     * 备注：此方法在 Java 层处理坐标变换，若需高性能可在 Native 层处理。
     * 
     * @param rotation Surface.ROTATION_0/90/180/270
     */
    public float[] getAdjustedRotationMatrix(int rotation) {
        float[] adjustedMatrix = new float[16];
        System.arraycopy(rotationMatrix, 0, adjustedMatrix, 0, 16);

        switch (rotation) {
            case Surface.ROTATION_0:
                break;
            case Surface.ROTATION_90:
                SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X,
                        adjustedMatrix);
                break;
            case Surface.ROTATION_180:
                SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_X,
                        SensorManager.AXIS_MINUS_Y, adjustedMatrix);
                break;
            case Surface.ROTATION_270:
                SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X,
                        adjustedMatrix);
                break;
        }

        return adjustedMatrix;
    }

    /**
     * 检查设备是否具备运行所需的传感器。
     */
    public boolean hasRequiredSensors(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magnetometer = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        return rotationSensor != null || (accelerometer != null && magnetometer != null);
    }
}

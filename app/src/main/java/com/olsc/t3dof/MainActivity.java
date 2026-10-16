package com.olsc.t3dof;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.Toast;
import com.olsc.t3dof.render.ObjectRenderer;
import com.olsc.t3dof.sensors.OrientationSensor;

/**
 * 应用的主 Activity，负责初始化传感器、GLSurfaceView 以及渲染器。
 *
 * 无论设备传感器是否齐全都会正常启动：缺少硬件时只弹土司提示，
 * 并通过多级回退（旋转矢量 / 游戏旋转矢量 / 加速度+磁力 / 陀螺仪积分）尽力保证基础 3DOF 功能。
 */
public class MainActivity extends Activity {
    private GLSurfaceView glSurfaceView;
    private OrientationSensor orientationSensor;
    private ObjectRenderer renderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化方向传感器
        orientationSensor = new OrientationSensor();

        // 检测可用的姿态来源（多级回退），仅用于提示，不影响启动
        OrientationSensor.Mode mode = orientationSensor.detectMode(this);

        // 初始化 OpenGL 视图
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2);

        // 设置渲染器
        renderer = new ObjectRenderer(this, orientationSensor);
        glSurfaceView.setRenderer(renderer);

        // 设置为持续渲染模式
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        setContentView(glSurfaceView);

        // 只提示，不退出
        showModeToast(mode);
    }

    /**
     * 用土司提示当前使用的姿态模式；若有局限也一并说明。
     */
    private void showModeToast(OrientationSensor.Mode mode) {
        String text;
        if (mode == OrientationSensor.Mode.NONE) {
            text = "未检测到可用于 3DOF 的姿态传感器，场景将保持静止。";
        } else {
            text = "姿态模式：" + orientationSensor.getModeName();
            String notice = orientationSensor.getModeNotice();
            if (notice != null) {
                text = text + "\n" + notice;
            }
        }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
        orientationSensor.start(this); // 启动传感器监听
    }

    @Override
    protected void onPause() {
        orientationSensor.stop(); // 停止传感器监听
        glSurfaceView.onPause();
        super.onPause();
    }
}

package com.olsc.t6dof;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.Toast;
import com.olsc.t6dof.render.ObjectRenderer;
import com.olsc.t6dof.sensors.OrientationSensor;

/**
 * 应用的主 Activity，负责初始化传感器、GLSurfaceView 以及渲染器。
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

        // 检查设备是否具备必要的传感器
        if (!orientationSensor.hasRequiredSensors(this)) {
            Toast.makeText(this, "此设备缺少必要的传感器（陀螺仪或加速度计+磁力计）", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 初始化 OpenGL 视图
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2);

        // 设置渲染器
        renderer = new ObjectRenderer(this, orientationSensor);
        glSurfaceView.setRenderer(renderer);

        // 设置为持续渲染模式
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        setContentView(glSurfaceView);
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

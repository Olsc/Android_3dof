package com.olsc.t3dof;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.Toast;
import com.olsc.t3dof.render.ObjectRenderer;
import com.olsc.t3dof.sensors.OrientationSensor;

// 应用入口：装配姿态传感器与渲染视图，传感器缺失时只提示不退出
public class MainActivity extends Activity {
    private GLSurfaceView glSurfaceView;
    private OrientationSensor orientationSensor;
    private ObjectRenderer renderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        orientationSensor = new OrientationSensor();
        // 检测结果仅用于提示，不影响应用启动
        OrientationSensor.Mode mode = orientationSensor.detectMode(this);

        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2);

        renderer = new ObjectRenderer(this, orientationSensor);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        setContentView(glSurfaceView);

        showModeToast(mode);
    }

    // 提示当前姿态来源及其局限
    private void showModeToast(OrientationSensor.Mode mode) {
        String text;
        if (mode == OrientationSensor.Mode.NONE) {
            text = "无可用姿态传感器，场景静止";
        } else {
            text = "姿态：" + orientationSensor.getModeName();
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
        orientationSensor.start(this);
    }

    @Override
    protected void onPause() {
        orientationSensor.stop();
        glSurfaceView.onPause();
        super.onPause();
    }
}
package com.olsc.t3dof.render;

import android.content.Context;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.WindowManager;
import com.olsc.t3dof.sensors.OrientationSensor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// 使用 OpenGL ES 2.0 绘制立方体，MVP 矩阵由 C++ 层通过 JNI 计算
public class ObjectRenderer implements GLSurfaceView.Renderer {
    static {
        System.loadLibrary("t3dof_native");
    }

    // 计算相机前方 distance 处的物体世界坐标，返回 [x, y, z]
    public native float[] calculateInsertionPoint(float[] rotationMatrix, int rotation, float distance);

    // 计算 MVP 矩阵，ratio 为屏幕宽高比，objectPos 为物体世界坐标
    public native float[] computeMVP(float[] rotationMatrix, int rotation, float ratio, float[] objectPos);

    private boolean mInitialized = false; // 是否已初始化物体坐标
    private float[] mObjectWorldPos = new float[3]; // 物体的世界空间位姿

    private final OrientationSensor orientationSensor;
    private int program; // OpenGL 程序句柄

    private final float[] mvpMatrix = new float[16]; // 最终生成的 MVP 矩阵
    private float mRatio = 1.0f; // 宽高比

    private FloatBuffer vertexBuffer;
    private FloatBuffer colorBuffer;
    private ShortBuffer indexBuffer;

    private static final int CUBE_INDEX_COUNT = 36;
    private final Context context;

    public ObjectRenderer(Context context, OrientationSensor sensor) {
        this.context = context;
        this.orientationSensor = sensor;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 深灰背景
        GLES20.glClearColor(0.1f, 0.15f, 0.2f, 1.0f);

        // 启用深度测试
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        String vertexShaderCode = "uniform mat4 uMVPMatrix;" +
                "attribute vec4 vPosition;" +
                "attribute vec4 vColor;" +
                "varying vec4 fColor;" +
                "void main() {" +
                "  gl_Position = uMVPMatrix * vPosition;" +
                "  fColor = vColor;" +
                "}";

        String fragmentShaderCode = "precision mediump float;" +
                "varying vec4 fColor;" +
                "void main() {" +
                "  gl_FragColor = fColor;" +
                "}";

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        createObject();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mRatio = (float) width / height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (!orientationSensor.isReady()) {
            return;
        }

        // 当前屏幕旋转角度
        int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();

        float[] rotationMatrix = orientationSensor.getRotationMatrix();

        // 延迟初始化物体世界坐标；不能用“矩阵是否为单位阵”判断数据是否有效，
        // 平放时倾斜模式的旋转矩阵本身就是单位阵，会导致物体永远不被初始化
        if (!mInitialized) {
            mObjectWorldPos = calculateInsertionPoint(rotationMatrix, rotation, 2.0f);
            mInitialized = true;
            android.util.Log.d("ObjectRenderer", String.format("物体 world 坐标已初始化: [%.2f, %.2f, %.2f]",
                    mObjectWorldPos[0], mObjectWorldPos[1], mObjectWorldPos[2]));
        }

        if (mInitialized) {
            float[] newMvpMatrix = computeMVP(rotationMatrix, rotation, mRatio, mObjectWorldPos);

            if (newMvpMatrix != null && newMvpMatrix.length == 16) {
                System.arraycopy(newMvpMatrix, 0, mvpMatrix, 0, 16);
                drawObject();
            }
        }
    }

    // 构建立方体的顶点、颜色与索引缓冲区
    private void createObject() {
        float size = 0.25f;

        // 8 个顶点
        float vertices[] = {
                -size, -size, size,
                size, -size, size,
                size, size, size,
                -size, size, size,
                -size, -size, -size,
                size, -size, -size,
                size, size, -size,
                -size, size, -size
        };

        // 基本颜色定义
        float colors[] = {
                1.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f
        };

        // 12 个三角形面，共 36 个顶点索引
        short indices[] = {
                0, 1, 2, 0, 2, 3,
                1, 5, 6, 1, 6, 2,
                5, 4, 7, 5, 7, 6,
                4, 0, 3, 4, 3, 7,
                3, 2, 6, 3, 6, 7,
                4, 5, 1, 4, 1, 0
        };

        // 按面展开颜色，每个面一种颜色
        float expandedColors[] = new float[indices.length * 4];
        int colorIdx = 0;
        float[][] faceColors = {
                { 1.0f, 0.0f, 0.0f, 1.0f }, // 红
                { 0.0f, 1.0f, 0.0f, 1.0f }, // 绿
                { 0.0f, 0.0f, 1.0f, 1.0f }, // 蓝
                { 1.0f, 1.0f, 0.0f, 1.0f }, // 黄
                { 0.0f, 1.0f, 1.0f, 1.0f }, // 青
                { 1.0f, 0.0f, 1.0f, 1.0f } // 品红
        };

        for (float[] color : faceColors) {
            for (int i = 0; i < 6; i++) {
                expandedColors[colorIdx++] = color[0];
                expandedColors[colorIdx++] = color[1];
                expandedColors[colorIdx++] = color[2];
                expandedColors[colorIdx++] = color[3];
            }
        }

        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        vertexBuffer = vbb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        ByteBuffer cbb = ByteBuffer.allocateDirect(expandedColors.length * 4);
        cbb.order(ByteOrder.nativeOrder());
        colorBuffer = cbb.asFloatBuffer();
        colorBuffer.put(expandedColors);
        colorBuffer.position(0);

        ByteBuffer ibb = ByteBuffer.allocateDirect(indices.length * 2);
        ibb.order(ByteOrder.nativeOrder());
        indexBuffer = ibb.asShortBuffer();
        indexBuffer.put(indices);
        indexBuffer.position(0);
    }

    // 提交顶点属性与 MVP 矩阵后绘制立方体
    private void drawObject() {
        GLES20.glUseProgram(program);

        int positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        int colorHandle = GLES20.glGetAttribLocation(program, "vColor");
        int mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glEnableVertexAttribArray(colorHandle);

        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 16, colorBuffer);

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, CUBE_INDEX_COUNT, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
    }

    // 编译 GLSL 着色器并返回句柄
    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
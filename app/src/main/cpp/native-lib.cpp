#include <jni.h>
#include <cmath>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "Native6DOF"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#define PI 3.14159265358979323846f

// 全局状态
static float g_mObjectWorldPos[4] = {0.0f, 0.0f, 0.0f, 1.0f};
static bool g_mInitialized = false;

// 辅助函数
void setIdentityM(float* sm, int smOffset) {
    for (int i=0; i<16; i++) sm[smOffset+i] = 0;
    sm[smOffset+0] = 1; sm[smOffset+5] = 1; sm[smOffset+10] = 1; sm[smOffset+15] = 1;
}

void transposeM(float* mTrans, int mTransOffset, float* m, int mOffset) {
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            mTrans[mTransOffset + i * 4 + j] = m[mOffset + j * 4 + i];
        }
    }
}

void multiplyMM(float* result, int resultOffset, const float* lhs, int lhsOffset, const float* rhs, int rhsOffset) {
    float temp[16];
    for (int i = 0; i < 4; i++) {
        const float lhs0 = lhs[lhsOffset + 0*4 + i];
        const float lhs1 = lhs[lhsOffset + 1*4 + i];
        const float lhs2 = lhs[lhsOffset + 2*4 + i];
        const float lhs3 = lhs[lhsOffset + 3*4 + i];
        for (int j = 0; j < 4; j++) {
            temp[j*4+i] = lhs0 * rhs[rhsOffset + j*4 + 0]
                        + lhs1 * rhs[rhsOffset + j*4 + 1]
                        + lhs2 * rhs[rhsOffset + j*4 + 2]
                        + lhs3 * rhs[rhsOffset + j*4 + 3];
        }
    }
    memcpy(result+resultOffset, temp, 16*sizeof(float));
}

void multiplyMV(float* resultVec, int resultVecOffset, const float* lhsMat, int lhsMatOffset, const float* rhsVec, int rhsVecOffset) {
    float x = rhsVec[rhsVecOffset + 0];
    float y = rhsVec[rhsVecOffset + 1];
    float z = rhsVec[rhsVecOffset + 2];
    float w = rhsVec[rhsVecOffset + 3];
    resultVec[resultVecOffset + 0] = lhsMat[lhsMatOffset + 0]*x + lhsMat[lhsMatOffset + 4]*y + lhsMat[lhsMatOffset + 8]*z + lhsMat[lhsMatOffset + 12]*w;
    resultVec[resultVecOffset + 1] = lhsMat[lhsMatOffset + 1]*x + lhsMat[lhsMatOffset + 5]*y + lhsMat[lhsMatOffset + 9]*z + lhsMat[lhsMatOffset + 13]*w;
    resultVec[resultVecOffset + 2] = lhsMat[lhsMatOffset + 2]*x + lhsMat[lhsMatOffset + 6]*y + lhsMat[lhsMatOffset + 10]*z + lhsMat[lhsMatOffset + 14]*w;
    resultVec[resultVecOffset + 3] = lhsMat[lhsMatOffset + 3]*x + lhsMat[lhsMatOffset + 7]*y + lhsMat[lhsMatOffset + 11]*z + lhsMat[lhsMatOffset + 15]*w;
}

void frustumM(float* m, int offset, float left, float right, float bottom, float top, float near, float far) {
    float r_width  = 1.0f / (right - left);
    float r_height = 1.0f / (top - bottom);
    float r_depth  = 1.0f / (near - far);
    float x = 2.0f * (near * r_width);
    float y = 2.0f * (near * r_height);
    float A = (right + left) * r_width;
    float B = (top + bottom) * r_height;
    float C = (near + far) * r_depth;
    float D = 2.0f * (near * far) * r_depth;
    memset(m+offset, 0, 16*sizeof(float));
    m[offset + 0] = x;
    m[offset + 5] = y;
    m[offset + 8] = A;
    m[offset + 9] = B;
    m[offset + 10] = C;
    m[offset + 11] = -1.0f;
    m[offset + 14] = D;
}

void translateM(float* m, int mOffset, float x, float y, float z) {
    for (int i=0 ; i<4 ; i++) {
        int mi = mOffset + i;
        m[12 + mi] += m[mi] * x + m[4 + mi] * y + m[8 + mi] * z;
    }
}

void rotateM(float* m, int mOffset, float a, float x, float y, float z) {
    float temp[16];
    setIdentityM(temp, 0);
    float rad = a * PI / 180.0f;
    float c = cos(rad);
    float s = sin(rad);
    float nc = 1.0f - c;
    float len = sqrt(x*x + y*y + z*z);
    if(len != 1.0f && len != 0.0f) {
        float rlen = 1.0f / len;
        x *= rlen; y *= rlen; z *= rlen;
    }
    float rm[16];
    setIdentityM(rm, 0);
    rm[0] = x*x*nc + c;   rm[4] = x*y*nc - z*s; rm[8] = x*z*nc + y*s;
    rm[1] = x*y*nc + z*s; rm[5] = y*y*nc + c;   rm[9] = y*z*nc - x*s;
    rm[2] = x*z*nc - y*s; rm[6] = y*z*nc + x*s; rm[10]= z*z*nc + c;
    multiplyMM(temp, 0, m, mOffset, rm, 0);
    memcpy(m+mOffset, temp, 16*sizeof(float));
}

// 坐标系重映射
// 0=0度 (Surface.ROTATION_0), 90=1 (Surface.ROTATION_90), 180=2, 270=3
void getRemappedMatrix(float* outMatrix, float* inMatrix, int rotation) {
    if(rotation == 0) {
        memcpy(outMatrix, inMatrix, 16 * sizeof(float));
        return;
    }
    
    int axisX = 0, axisY = 0;
    int signX = 1, signY = 1;
    
    // 根据屏幕旋转方向映射坐标轴
    if (rotation == 1) { // 90度 (横屏)
        // X -> Y, Y -> -X
        axisX = 1; signX = 1;
        axisY = 0; signY = -1;
    } else if (rotation == 2) { // 180度
        // X -> -X, Y -> -Y
        axisX = 0; signX = -1;
        axisY = 1; signY = -1;
    } else if (rotation == 3) { // 270度 (反向横屏)
        // X -> -Y, Y -> X
        axisX = 1; signX = -1;
        axisY = 0; signY = 1;
    }
    
    // 在这些简单的旋转中 Z 始终是 Z（绕 Z 轴旋转）
    // 构建新的矩阵列，保持平移分量不变
    for (int i = 0; i < 4; i++) {
        outMatrix[0*4+i] = inMatrix[axisX*4+i] * signX;
        outMatrix[1*4+i] = inMatrix[axisY*4+i] * signY;
        outMatrix[2*4+i] = inMatrix[2*4+i]; // Z 轴保持不变
        outMatrix[3*4+i] = inMatrix[3*4+i]; // 保持平移不变
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_olsc_t6dof_render_ObjectRenderer_calculateInsertionPoint(JNIEnv* env, jobject thiz, jfloatArray rotationMatrix, jint rotation, jfloat distance) {
    jsize len = env->GetArrayLength(rotationMatrix);
    float deviceRotation[16];
    
    if (len >= 16) {
        env->GetFloatArrayRegion(rotationMatrix, 0, 16, deviceRotation);
    } else {
        setIdentityM(deviceRotation, 0);
    }

    // 1. 获取根据屏幕旋转调整后的视图矩阵
    float viewMatrix[16];
    getRemappedMatrix(viewMatrix, deviceRotation, rotation);

    // 2. 计算方块在世界空间中的坐标: Inv(View) * (0, 0, -distance)
    // 纯旋转矩阵的逆等于其转置
    float invViewMatrix[16];
    transposeM(invViewMatrix, 0, viewMatrix, 0);

    float targetPosCameraSpace[4] = { 0.0f, 0.0f, -distance, 1.0f };
    float worldPos[4];
    multiplyMV(worldPos, 0, invViewMatrix, 0, targetPosCameraSpace, 0);

    jfloatArray result = env->NewFloatArray(3);
    float resFloats[3] = {worldPos[0], worldPos[1], worldPos[2]};
    env->SetFloatArrayRegion(result, 0, 3, resFloats);
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_olsc_t6dof_render_ObjectRenderer_computeMVP(JNIEnv* env, jobject thiz, jfloatArray rotationMatrix, jint rotation, jfloat ratio, jfloatArray objectPos) {
    // 1. 获取设备旋转数据
    jsize len = env->GetArrayLength(rotationMatrix);
    float deviceRotation[16];
    if (len >= 16) {
        env->GetFloatArrayRegion(rotationMatrix, 0, 16, deviceRotation);
    } else {
        setIdentityM(deviceRotation, 0);
    }

    // 2. 获取物体世界坐标
    float worldPos[3] = {0, 0, 0};
    if (objectPos != NULL) {
        env->GetFloatArrayRegion(objectPos, 0, 3, worldPos);
    }

    // 3. 计算视图矩阵 (根据屏幕旋转重映射)
    float viewMatrix[16];
    getRemappedMatrix(viewMatrix, deviceRotation, rotation);
    
    // 4. 计算投影矩阵
    float projectionMatrix[16];
    frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 1.0f, 100.0f);
    
    // 5. 计算模型矩阵 (平移到指定的世界坐标并添加固定自转)
    float modelMatrix[16];
    setIdentityM(modelMatrix, 0);
    translateM(modelMatrix, 0, worldPos[0], worldPos[1], worldPos[2]);
    rotateM(modelMatrix, 0, 45.0f, 0.0f, 1.0f, 0.0f);
    rotateM(modelMatrix, 0, 30.0f, 1.0f, 0.0f, 0.0f);
        
    // 6. 组合变换: MVP = Projection * View * Model
    float tempMatrix[16];
    multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        
    float mvpMatrix[16];
    multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0);
        
    jfloatArray result = env->NewFloatArray(16);
    env->SetFloatArrayRegion(result, 0, 16, mvpMatrix);
    return result;
}

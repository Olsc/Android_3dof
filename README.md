# 3DOF Android 姿态跟踪 Demo

一个使用 Android 传感器的姿态跟踪可视化示例。应用通过 `GLSurfaceView` 渲染 3D 立方体，实时显示设备的三维姿态（俯仰、偏航、滚转），并在场景中固定显示物体。

**注意：这是一个 3DOF（3自由度）姿态跟踪系统，只能跟踪设备的旋转状态，无法跟踪位置变化。**

## 项目简介
- 实时读取设备传感器数据（优先使用旋转矢量传感器，备用方案为加速度计+磁力计）
- 计算设备的 3D 姿态矩阵（旋转状态）
- OpenGL ES 2.0 渲染：在场景中显示彩色立方体，跟随设备姿态变化
- 支持屏幕旋转自适应和触控交互

## 功能特性
- 姿态跟踪：实时计算设备的 3D 旋转姿态
- 传感器融合：
  - 优先使用系统提供的旋转矢量传感器（精度最高，无累积误差）
  - 备用方案：加速度计 + 磁力计手动计算姿态
- 渲染显示：
  - 彩色立方体在 3D 空间中固定显示
  - 立方体跟随设备姿态实时旋转
  - 深色背景配色，启用深度测试

## 架构说明
- `app/src/main/java/com/olsc/t6dof/MainActivity.java`：应用主入口，负责初始化传感器和 OpenGL 渲染视图
- `app/src/main/java/com/olsc/t6dof/sensors/OrientationSensor.java`：姿态传感器管理，处理旋转矢量传感器或加速度计+磁力计数据
- `app/src/main/java/com/olsc/t6dof/render/ObjectRenderer.java`：OpenGL ES 2.0 渲染器，负责 3D 立方体的渲染和姿态矩阵计算
- `app/src/main/cpp/native-lib.cpp`：JNI 原生库，提供矩阵运算和坐标变换功能

坐标系说明：
- 使用标准的 OpenGL 坐标系（右手坐标系）
- Y 轴向上，X 轴向右，Z 轴指向观察者
- 支持屏幕旋转自适应（0°、90°、180°、270°）

## 运行要求
- Android 8.0+（API 26）
- 支持 OpenGL ES 2.0
- 具备以下**任意一种**姿态来源即可（按优先级自动选择）：
  1. 旋转矢量传感器（推荐，绝对姿态）
  2. 游戏旋转矢量传感器（陀螺仪 + 加速度计，相对姿态）
  3. 加速度计 + 磁力计
  4. 陀螺仪（积分兜底）
- 若以上都没有：应用仍会正常启动，只弹土司提示，场景保持静止。

## 传感器说明
- 采用多级回退，自动选择可用的姿态来源：
  - **旋转矢量**：系统级融合，精度最高且无累积漂移（需要磁力计）
  - **游戏旋转矢量**：陀螺仪 + 加速度计融合，适合无磁力计设备（相对姿态，yaw 会缓慢漂移）
  - **加速度计 + 磁力计**：通过 `getRotationMatrix()` 计算姿态
  - **陀螺仪积分**：最后兜底，姿态会随时间漂移
- 采样率：使用 `SENSOR_DELAY_GAME` 模式，平衡性能和功耗
- 缺少磁力计时无法获得绝对航向，但不影响基础的 3DOF 旋转跟随效果（如 Rokid Glass 3）

## 使用说明
- 打开应用后，系统会自动检测设备传感器支持情况
- 若设备缺少部分传感器，只会弹土司提示，应用不会退出；会尽力使用可用的回退方案保证基础 3DOF 功能
- 移动设备观察立方体如何跟随设备姿态旋转
- 应用会自动适配屏幕旋转方向

## 技术特点
- **无位置跟踪**：本系统仅为 3DOF 姿态跟踪，不涉及位置估计
- **实时性好**：使用系统级旋转矢量传感器，延迟低，精度高
- **稳定性强**：旋转矢量传感器内部已做传感器融合和误差校正
- **兼容性强**：支持多种传感器配置，自动选择最优方案

## 目录结构
- `app/src/main/java/com/olsc/t6dof/`：Java 层源码
  - `MainActivity.java`：应用主入口
  - `sensors/OrientationSensor.java`：姿态传感器管理
  - `render/ObjectRenderer.java`：OpenGL 渲染器
- `app/src/main/cpp/`：C++ 原生代码
  - `native-lib.cpp`：JNI 实现和矩阵运算
  - `CMakeLists.txt`：构建配置
- `app/src/main/AndroidManifest.xml`：应用配置和权限声明

## 注意事项
- 这是 3DOF 姿态跟踪系统，**不具备位置跟踪能力**
- 旋转矢量传感器在大多数现代 Android 设备上都有良好支持
- 应用主要用于演示姿态跟踪效果，非生产级应用
- 磁力计可能受环境磁场干扰，影响航向精度

## 许可证

本项目采用 Apache License 2.0 许可证。详情请参见 [LICENSE](./LICENSE) 文件。

## 贡献者

[![Contributors](https://contrib.rocks/image?repo=Olsc/Android_3dof)](https://github.com/Olsc/Android_3dof/graphs/contributors)

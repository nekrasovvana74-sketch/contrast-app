# Contrast App

Android 图片亮度/对比度/饱和度调节器。

## 咋跑起来

1. 装 Android Studio（最新版）
2. File > Open，选这个文件夹
3. 等 Gradle sync 完（第一次会下依赖，慢）
4. 插手机或开模拟器，点绿色 Run 三角

## 功能

- 从相册选图（系统 Photo Picker，Android 13+ 原生，低版本自动回退）
- 三根滑杆实时调：亮度 / 对比度 / 饱和度
- 保存到相册（Pictures/ContrastApp/）

## 技术细节

- Kotlin + Jetpack Compose
- ColorMatrix 做色彩变换，GPU 层绘制，不碰 Bitmap 像素数组 [citation:3]
- 保存走 MediaStore，兼容 Android 10+ Scoped Storage [citation:7]

## 注意

这项目**没包含编译好的 APK**。你得自己用 Android Studio build 一下。

Min SDK 26（Android 8.0），Target SDK 34。

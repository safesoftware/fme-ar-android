![FME AR](https://is3-ssl.mzstatic.com/image/thumb/Purple118/v4/31/9c/c7/319cc748-5ac6-2d91-8b1a-afdc7e3e164e/AppIcon-1x_U007emarketing-0-0-GLES2_U002c0-512MB-sRGB-0-0-0-85-220-0-0-0-6.png/246x0w.jpg)

# FME AR

This repository contains the source code of the FME AR mobile app for iOS.

## Description
The FME AR writer introduced in FME 2018.0 can create 3D models in the custom FME AR format with a file extension .fmear. This app can open those .fmear files from cloud storages such as Google Drive, and display the models in augmented reality. Using FME Workbench, 3D models from many different formats can be converted to the FME AR format. Once the .fmear files are uploaded to a supported cloud storage, the app will list the files, and the user can select a file to view in augmented reality.

This app is built on the Android augmented reality framework called ARCore. It allows people to easily create unparalleled augmented reality experiences for Android devices. It has an accurate tracking of device movement. It can analyze the scene presented by the camera view and find horizontal and vertical planes in the real world. It can place and track virtual objects on the plane with a high degree of accuracy without additional calibration.

## Requirements
ARCore requires Android 7.0 or later and access to the Google Play Store. Here is a list of supported devices currently: https://developers.google.com/ar/discover/supported-devices
ARCore download: https://play.google.com/store/apps/details?id=com.google.ar.core&hl=en

## Licenses
* [FME AR](https://github.com/safesoftware/fme-ar-android/blob/master/LICENSE)
* [Gesture Detectors](https://github.com/Almeros/android-gesture-detectors)
* [Google ARCore Sample](https://developers.google.com/ar/develop/java/quickstart)
* [OBJ Loader](https://github.com/safesoftware/fme-ar-android/blob/master/FMEAR/app/src/main/java/de/LICENSE.txt)
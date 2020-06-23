package com.safe.fmear

// App
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

// AndroidX
import androidx.core.content.ContextCompat

// ARCore
import com.google.ar.core.Session
import com.google.ar.core.Config
import com.google.ar.core.ArCoreApk

class FMEARUtils {

    enum class RequestCode(val code: Int) {
        CAMERA(0x00000001),
        OPEN_DOCUMENT(0x00000002)
    }

    companion object {

        var arCoreInstallRequested: Boolean = false

        fun requestCameraPermission(activity: Activity) {
            ActivityCompat.requestPermissions(activity,
                arrayOf(Manifest.permission.CAMERA), RequestCode.CAMERA.code)
        }

        /** Check if the context has the camera permission. */
        fun hasCameraPermission(context: Context) : Boolean {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }

        fun createARSession(activity: Activity, lightEstimationMode: Config.LightEstimationMode) : Session? {
            var session: Session? = null

            if (hasCameraPermission(activity)) {
                when (ArCoreApk.getInstance().requestInstall(activity, !arCoreInstallRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        arCoreInstallRequested = true
                        return null
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> { /* do nothing */ }
                    else -> { /* do nothing */ }
                }

                session = Session(activity)
                var config = Config(session)
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE)
                config.setLightEstimationMode(lightEstimationMode)
                session.configure(config)
            } else {
                requestCameraPermission(activity)
            }

            return session
        }
    }
}
package com.safe.fmear

// App
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.ActivityCompat

// AndroidX
import androidx.core.content.ContextCompat

// ARCore
import com.google.ar.core.Session
import com.google.ar.core.Config
import com.google.ar.core.ArCoreApk

class FMEARUtils {

    enum class RequestCode(val code: Int) {
        CAMERA(0x00000001)
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

        fun getDisplayName(context: Context, uri: Uri) : String? {

            var displayName: String? = null

            // The query, because it only applies to a single document, returns only
            // one row. There's no need to filter, sort, or select fields,
            // because we want all fields for one document.
            val cursor: Cursor? = context.contentResolver.query(
                uri, null, null, null, null, null)

            cursor?.use {
                // moveToFirst() returns false if the cursor has 0 rows. Very handy for
                // "if there's anything to look at, look at it" conditionals.
                if (it.moveToFirst()) {

                    // Note it's called "Display Name". This is
                    // provider-specific, and might not necessarily be the file name.
                    displayName = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }

            return displayName
        }

    }
}
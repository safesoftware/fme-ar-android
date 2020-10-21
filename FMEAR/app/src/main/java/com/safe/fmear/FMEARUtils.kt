package com.safe.fmear

// App

// AndroidX

// ARCore
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

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

        fun extractData(context: Context, uri: Uri) {
            var zipInputStream: ZipInputStream = ZipInputStream(
                BufferedInputStream(context.contentResolver.openInputStream(uri))
            )

            zipInputStream.use { stream ->
                var entry: ZipEntry? = stream.nextEntry
                while (entry != null) {

                    var entryName = entry.name

                    // Skip the __MACOSX folders in the .fmear file in case the .fmear archive
                    // was created on macOS with the __MACOSX resource fork.
                    if (entry.name.startsWith("__MACOSX", true)) {
                        entry = stream.nextEntry
                        continue   // Skip anything from the __MACOSX directory
                    }

                    Log.i("extractData", "entry name = $entryName")

                    //
//
//                    val unzippedFile = File(destinationFolder, zipEntryName)
//
//
//                    if (!zipEntryName.startsWith("__MACOSX")) {
//                        if (zipEntry.isDirectory()) {
//                            // If the entry is a directory, we need to make sure all the parent
//                            // directories leading up to this directory exist before writing a
//                            // file in the directory
//                            unzippedFile.mkdirs()
//                        } else {
//                            // If the entry is a file, we need to make sure all the parent
//                            // directories leading up to this file exist before writing the
//                            // file to the path.
//                            val subfolder: File = unzippedFile.parentFile
//                            subfolder?.mkdirs()
//
//                            // Now we can unzip the file
//                            Log.i(
//                                "FME AR",
//                                "Unzipping '$unzippedFile' ..."
//                            )
//                            val fileOutputStream =
//                                FileOutputStream(unzippedFile)
//                            while (zipInputStream.read(buffer).also { numBytes = it } > 0) {
//                                fileOutputStream.write(buffer, 0, numBytes)
//                            }
//                            fileOutputStream.close()
//                        }
//                    }
//                    zipInputStream.closeEntry()
//
//
//
//
//                    if (entry.isDirectory) {
//                        val f = File(location, ze.name)
//                        if (!f.exists()) if (!f.isDirectory) f.mkdirs()
//                    } else {
//                        FileOutputStream(File(location, ze.name)).use { fout ->
//                            val buffer = ByteArray(8192)
//                            var len = zin.read(buffer)
//                            while (len != -1) {
//                                fout.write(buffer, 0, len)
//                                len = zin.read(buffer)
//                            }
//
//                            stream.closeEntry()
//                        }
//
//                    }

                    entry = stream.nextEntry
                }
            }
        }



//        fun extractData(uri: Uri) {
//            ZipFile(zipFileName).use { zip ->
//                zip.entries().asSequence().forEach { entry ->
//                    zip.getInputStream(entry).use { input ->
//                        File(entry.name).outputStream().use { output ->
//                            input.copyTo(output)
//                        }
//                    }
//                }
//            }
//        }
    }
}
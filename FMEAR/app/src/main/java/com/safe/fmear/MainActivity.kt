package com.safe.fmear

// OS
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log

// App
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts

// UI
import android.view.View
import com.google.android.material.snackbar.Snackbar

// ARCore
import com.google.ar.core.Session
import com.google.ar.core.Config
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipFile


class MainActivity : AppCompatActivity() {

    // Log
    private val TAG: String = MainActivity::class.simpleName!!

    // AR
    private var arSession: Session? = null

    // Contracts
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {

            var fmearFileStream: InputStream? = null
            val fileName = FMEARUtils.getDisplayName(this, uri)
            if (fileName != null && fileName.endsWith(".fmear")) {
                try {
                    fmearFileStream = contentResolver.openInputStream(uri)
                } catch (e: FileNotFoundException) {
                    fmearFileStream = null
                }
            }

            if (fmearFileStream != null) {
                Snackbar.make(
                    findViewById(R.id.ux_main_activity_coordinator_layout),
                    getString(R.string.opening_file, fileName),
                    Snackbar.LENGTH_INDEFINITE
                ).show()
            } else {
                Snackbar.make(
                    findViewById(R.id.ux_main_activity_coordinator_layout),
                    getString(R.string.unsupported_file, fileName),
                    Snackbar.LENGTH_INDEFINITE
                ).show()
            }

            // Get the zip file
            FMEARUtils.extractData(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()
    }

    override fun onResume() {
        super.onResume()

    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun setupUi() {

        setContentView(R.layout.ux_main_activity)

        val fabAdd: View = findViewById(R.id.fab_add)
        fabAdd.setOnClickListener {
            showFileBrowser()
        }
    }

    fun showFileBrowser() {
        openFile.launch(arrayOf("application/*"))
    }
}
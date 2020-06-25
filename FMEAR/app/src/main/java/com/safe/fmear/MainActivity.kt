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
import com.google.ar.sceneform.ux.ArFragment


class MainActivity : AppCompatActivity() {

    // Log
    private val TAG: String = MainActivity::class.simpleName!!

    // AR
    private var arSession: Session? = null
    private var arFragment: ArFragment? = null

    // Contracts
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val fileName = FMEARUtils.getDisplayName(this, uri)

            if (fileName != null && fileName.endsWith(".fmear")) {
                Snackbar.make(
                    findViewById(R.id.ux_main_activity_coordinator_layout),
                    getString(R.string.opening_file, fileName),
                    Snackbar.LENGTH_INDEFINITE
                ).show()
            } else {
                val invalidFileName = if (fileName != null) "'$fileName' " else ""
                Snackbar.make(
                    findViewById(R.id.ux_main_activity_coordinator_layout),
                    getString(R.string.unsupported_file, fileName),
                    Snackbar.LENGTH_INDEFINITE
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()
    }

    override fun onResume() {
        super.onResume()

        var arSceneView = arFragment?.arSceneView
        if (arSceneView == null) {
            return
        }

        if (arSceneView.session == null) {
            var session = FMEARUtils.createARSession(
                this,
                Config.LightEstimationMode.DISABLED
            )
            if (session == null) {
                return
            } else {
                arSceneView.setupSession(session)
            }
        }

        arSceneView.resume()
    }

    override fun onPause() {
        var arSceneView = arFragment?.arSceneView
        arSceneView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        var arSceneView = arFragment?.arSceneView
        arSceneView?.destroy()
        super.onDestroy()
    }

    fun setupUi() {

        setContentView(R.layout.ux_main_activity)

        arFragment = this.supportFragmentManager.findFragmentById(R.id.ux_ar_fragment) as ArFragment?

        val fabAdd: View = findViewById(R.id.fab_add)
        fabAdd.setOnClickListener {
            showFileBrowser()
        }
    }

    fun showFileBrowser() {
        openFile.launch(arrayOf("application/*"))
    }
}
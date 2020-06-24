package com.safe.fmear

// OS
import android.content.Intent
import android.os.Bundle

// App
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

// UI
import android.view.View
import com.google.android.material.snackbar.Snackbar

// ARCore
import com.google.ar.core.Session
import com.google.ar.core.Config
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.ux.ArFragment


class MainActivity : AppCompatActivity() {

    // AR
    private var arSession: Session? = null
    private var arFragment: ArFragment? = null

    // Contracts
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            this.arFragment?.view?.let {
                Snackbar.make(
                    it,
                    "Opening file '$uri'",
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
        fabAdd.setOnClickListener { view ->
            showFileBrowser(view)
        }
    }

    fun showFileBrowser(view: View) {
        openFile.launch(arrayOf("application/*"))
    }
}
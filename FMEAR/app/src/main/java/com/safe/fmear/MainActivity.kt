package com.safe.fmear

// OS
import android.os.Bundle

// App
import androidx.appcompat.app.AppCompatActivity

// ARCore
import com.google.ar.core.Session
import com.google.ar.core.Config
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.ux.ArFragment

class MainActivity : AppCompatActivity() {

    // AR
    private var arSession: Session? = null
    private var arFragment: ArFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.ux_main_activity)
        arFragment = this.supportFragmentManager.findFragmentById(R.id.ux_ar_fragment) as ArFragment?
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
}
package com.safe.fmear

// OS
import android.os.Bundle

// App
import androidx.appcompat.app.AppCompatActivity

// ARCore
import com.google.ar.core.Session
import com.google.ar.core.Config

class MainActivity : AppCompatActivity() {

    // AR
    private var arSession: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        var session = FMEARUtils.createARSession(this,
            Config.LightEstimationMode.DISABLED)
        if (session == null) {
            return
        }
    }
}
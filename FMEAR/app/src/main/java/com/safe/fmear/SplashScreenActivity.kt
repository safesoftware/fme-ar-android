package com.safe.fmear

// OS
import android.os.Bundle
import android.content.Intent

// AndroidX
import androidx.appcompat.app.AppCompatActivity

class SplashScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(Intent(this, MainActivity::class.java))

        // Finish the splash screen activity so that the main activity cannot go back to this
        // splash screen.
        finish();
    }
}
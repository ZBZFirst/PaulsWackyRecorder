package com.example.templei

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Legacy Screen 4 short-form route now redirects to the music controller host.
 */
class Screen4ShortFormActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, Screen4Activity::class.java))
        finish()
    }
}

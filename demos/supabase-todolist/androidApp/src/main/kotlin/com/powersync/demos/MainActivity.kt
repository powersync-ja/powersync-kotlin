package com.powersync.demos

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import com.powersync.DatabaseDriverFactory

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Needed to render system bar properly
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colors.background) {
                    MainView(DatabaseDriverFactory(this))
                }
            }
        }
    }
}

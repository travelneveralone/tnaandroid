package com.travelneveralone.tna

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider

/// FragmentActivity (still a ComponentActivity) because BiometricPrompt needs one.
class MainActivity : FragmentActivity() {
    private lateinit var model: AppModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is light-only (as on iOS): dark system icons on the cream background
        // even when the phone itself is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        model = ViewModelProvider(this, AppModel.factory(application))[AppModel::class.java]
        model.biometrics.attach(this)
        setContent {
            Surface(Modifier.fillMaxSize(), color = TnaColor.bg) {
                Root(model)
            }
        }
    }

    // Activity visibility drives the lock timer. The biometric prompt is a
    // fragment inside this activity, so it never counts as leaving the app.
    override fun onStart() {
        super.onStart()
        model.appLock.didBecomeActive()
    }

    override fun onStop() {
        super.onStop()
        model.appLock.didEnterBackground()
    }
}

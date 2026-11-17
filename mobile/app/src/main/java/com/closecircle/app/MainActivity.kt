package com.closecircle.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.closecircle.app.ui.AppRoot
import com.closecircle.app.ui.AppViewModel
import com.closecircle.app.ui.theme.CloseCircleTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CloseCircleTheme { AppRoot(vm) } }
    }
}

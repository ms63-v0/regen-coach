package com.team_o.regencoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.team_o.regencoach.ui.RegenCoachApp

class MainActivity : ComponentActivity() {

    private val vm: RegenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RegenCoachApp(vm) }
    }
}

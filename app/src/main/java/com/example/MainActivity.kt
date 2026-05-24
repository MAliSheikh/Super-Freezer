package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainFreezerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppFreezerViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val vm: AppFreezerViewModel = viewModel()
        MainFreezerScreen(viewModel = vm)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // Re-verify if user turned on standard or automated Accessibility Service
    try {
      val vm: AppFreezerViewModel = androidx.lifecycle.ViewModelProvider(this)[AppFreezerViewModel::class.java]
      vm.checkAccessibilityStatus()
    } catch (e: Exception) {
      // safe fallback
    }
  }
}

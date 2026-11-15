package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.MyApplicationTheme

import androidx.lifecycle.ViewModelProvider
import com.example.library.data.AppDatabase
import com.example.library.data.PhotoshootRepository
import com.example.ui.PhotoshootApp
import com.example.viewmodel.PhotoshootViewModel
import com.example.viewmodel.PhotoshootViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize Database & Repository
    val database = AppDatabase.getDatabase(this)
    val repository = PhotoshootRepository(
        context = this,
        photoDao = database.photoDao(),
        settingsDao = database.settingsDao(),
        registrationDao = database.registrationDao(),
        configDao = database.configDao(),
        projectDao = database.projectDao()
    )

    // Instantiate Photoshoot ViewModel
    val viewModel = ViewModelProvider(
        this,
        PhotoshootViewModelFactory(this, repository)
    )[PhotoshootViewModel::class.java]

    setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        PhotoshootApp(viewModel = viewModel)
      }
    }
  }
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.repository.AppDatabase
import com.example.data.repository.InventoryRepository
import com.example.ui.theme.MobileGalleryTheme
import com.example.ui.screens.MainAppScreen
import com.example.ui.viewmodel.StockViewModel
import com.example.ui.viewmodel.StockViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: StockViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = InventoryRepository(database)
        StockViewModelFactory(application, repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileGalleryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen(viewModel)
                }
            }
        }
    }
}

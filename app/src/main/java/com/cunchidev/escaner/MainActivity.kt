package com.cunchidev.escaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cunchidev.escaner.ui.DocumentScreen
import com.cunchidev.escaner.ui.EscanerTheme
import com.cunchidev.escaner.ui.LibraryScreen
import com.cunchidev.escaner.ui.UiPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable data object LibraryRoute
@Serializable data class DocumentRoute(val id: Long)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var uiPrefs: UiPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val config by uiPrefs.config.collectAsStateWithLifecycle()
            EscanerTheme(accent = config.accent) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = LibraryRoute) {
                    composable<LibraryRoute> {
                        LibraryScreen(onOpen = { id -> nav.navigate(DocumentRoute(id)) })
                    }
                    composable<DocumentRoute> {
                        DocumentScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

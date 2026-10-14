package com.dailynotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dailynotes.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: NotesViewModel = viewModel()
            val theme by vm.theme.collectAsStateWithLifecycle()
            val dark = when (theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            NotesTheme(dark) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(vm,
                            onOpen = { nav.navigate("editor/$it") },
                            onTrash = { nav.navigate("trash") },
                            onSettings = { nav.navigate("settings") })
                    }
                    composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        EditorScreen(vm, it.arguments!!.getLong("id")) { nav.popBackStack() }
                    }
                    composable("trash") { TrashScreen(vm) { nav.popBackStack() } }
                    composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
                }
            }
        }
    }
}

package io.kopitiam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.kopitiam.app.ui.screens.ComingSoonScreen
import io.kopitiam.app.ui.screens.CompressScreen
import io.kopitiam.app.ui.screens.ConvertScreen
import io.kopitiam.app.ui.screens.EditorScreen
import io.kopitiam.app.ui.screens.ExtractScreen
import io.kopitiam.app.ui.screens.HomeScreen
import io.kopitiam.app.ui.screens.MergeScreen
import io.kopitiam.app.ui.screens.OcrScreen
import io.kopitiam.app.ui.screens.RedactScreen
import io.kopitiam.app.ui.screens.ScanBatchScreen
import io.kopitiam.app.ui.screens.ScanDocumentScreen
import io.kopitiam.app.ui.screens.ScanFilterScreen
import io.kopitiam.app.ui.screens.ScanIdCardScreen
import io.kopitiam.app.ui.screens.SplitScreen
import io.kopitiam.app.ui.theme.KopitiamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // AndroidX core-splashscreen: brand-brown splash with the kopi mark,
        // handed off to the app UI once ready. Must run before super.onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Deep-link launch extra for deterministic verification screenshots:
        //   adb ... am start ... --es open <toolId>
        val openTool = intent?.getStringExtra("open")
        setContent {
            KopitiamTheme {
                KopitiamApp(startTool = openTool)
            }
        }
    }
}

@Composable
fun KopitiamApp(startTool: String? = null) {
    val nav = rememberNavController()
    val start = if (startTool != null && ToolRegistry.tool(startTool) != null) "tool/$startTool" else "home"

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        NavHost(navController = nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(onOpenTool = { id -> nav.navigate("tool/$id") })
            }
            composable("tool/{id}") { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }
                ToolRoute(id, back)
            }
        }
    }
}

@Composable
private fun ToolRoute(id: String, onBack: () -> Unit) {
    when (id) {
        "edit" -> EditorScreen(onBack)
        "merge" -> MergeScreen(onBack)
        "split" -> SplitScreen(onBack)
        "extract" -> ExtractScreen(onBack)
        "compress" -> CompressScreen(onBack)
        "redact" -> RedactScreen(onBack)
        "document" -> ScanDocumentScreen(onBack)
        "id-card" -> ScanIdCardScreen(onBack)
        "batch" -> ScanBatchScreen(onBack)
        "filters" -> ScanFilterScreen(onBack)
        "ocr" -> OcrScreen(onBack)
        "convert" -> ConvertScreen(onBack)
        else -> {
            val tool = ToolRegistry.tool(id)
            if (tool != null) ComingSoonScreen(tool, onBack) else onBack()
        }
    }
}

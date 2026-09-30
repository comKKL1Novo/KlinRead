package com.klin.read

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.klin.read.data.MusicState
import com.klin.read.data.MusicStore
import com.klin.read.data.ReaderPreferences
import com.klin.read.data.ReaderSettings
import com.klin.read.music.MusicPlayer
import com.klin.read.ui.about.AboutScreen
import com.klin.read.ui.design.DarkColors
import com.klin.read.ui.design.LightColors
import com.klin.read.ui.design.LocalColors
import com.klin.read.ui.music.MusicScreen
import com.klin.read.ui.music.MusicViewModel
import com.klin.read.ui.nav.BottomBar
import com.klin.read.ui.nav.HomeTab
import com.klin.read.ui.reader.ReaderScreen
import com.klin.read.ui.reader.ReaderViewModel
import com.klin.read.ui.settings.SettingsScreen
import com.klin.read.ui.settings.SettingsViewModel
import com.klin.read.ui.shelf.ShelfScreen
import com.klin.read.ui.shelf.ShelfViewModel
import com.klin.read.ui.splash.SplashScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        enableEdgeToEdge()
        setContent { ReaderApp() }
    }

    /**
     * Writes uncaught exceptions to the cache directory.
     *
     * Without it a failure on a screen the user cannot describe leaves nothing to
     * diagnose once the process is gone. The file is readable via
     * `adb shell run-as`.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                java.io.File(cacheDir, "last_crash.txt").writeText(
                    buildString {
                        appendLine("time: ${java.util.Date()}")
                        appendLine("thread: ${thread.name}")
                        appendLine(android.util.Log.getStackTraceString(throwable))
                    }
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val READER = "reader/{bookId}"
    fun reader(bookId: Long) = "reader/$bookId"
}

@Composable
private fun ReaderApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    var splashDone by remember { mutableStateOf(false) }

    val preferences = remember { ReaderPreferences(context) }
    val settings by preferences.settings.collectAsStateWithLifecycle(
        initialValue = ReaderSettings()
    )
    val colors = if (settings.darkGlass) DarkColors else LightColors

    // Brightness applied to this app's window.
    //
    // `screenBrightness` on the window attributes affects only this activity,
    // which is what "app brightness" should mean -- changing the device setting
    // needs a system permission and would follow the user out of the app.
    //
    // The live value is held here rather than read straight from the stored
    // setting, because that only changes once the debounced write completes. A
    // slider that dimmed the screen 120ms after the finger stopped did not look
    // connected to the drag at all.
    val liveBrightness = remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    val shownBrightness = liveBrightness.value ?: settings.brightness

    val activity = context as? android.app.Activity
    androidx.compose.runtime.LaunchedEffect(shownBrightness, activity) {
        activity?.window?.let { window ->
            val params = window.attributes
            params.screenBrightness = shownBrightness.coerceIn(0.05f, 1f)
            window.attributes = params
        }
    }

    // Drop the override once the stored value catches up, so changes made
    // elsewhere still take effect.
    androidx.compose.runtime.LaunchedEffect(settings.brightness, liveBrightness.value) {
        val held = liveBrightness.value
        if (held != null && kotlin.math.abs(held - settings.brightness) < 0.005f) {
            liveBrightness.value = null
        }
    }

    if (!splashDone) {
        SplashScreen(onFinished = { splashDone = true })
        return
    }

    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScaffold(
                        liveBrightness = liveBrightness.value,
                        onBrightnessPreview = { liveBrightness.value = it },
                        onOpenBook = { id -> navController.navigate(Routes.reader(id)) }
                    )
                }

                composable(
                    route = Routes.READER,
                    arguments = listOf(navArgument("bookId") { type = NavType.LongType })
                ) { entry ->
                    val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                    val vm: ReaderViewModel = viewModel()
                    LaunchedEffect(bookId) { vm.load(bookId) }
                    val musicScope = rememberCoroutineScope()

                    // Background music follows the reader: start on entry, pause on
                    // exit, and resume from the stored offset so leaving and
                    // re-entering a book continues the same track where it stopped.
                    val music = remember { MusicStore(context) }
                    val musicState by music.state.collectAsStateWithLifecycle(
                        initialValue = MusicState()
                    )
                    DisposableEffect(musicState.autoPlayInReader, musicState.currentUri) {
                        val uri = musicState.currentUri
                        if (musicState.autoPlayInReader && !uri.isNullOrBlank()) {
                            MusicPlayer.setVolume(musicState.volume)
                            MusicPlayer.setLoop(musicState.loop)
                            MusicPlayer.play(context, uri, musicState.positionMs)
                        }
                        onDispose {
                            if (musicState.autoPlayInReader) {
                                // Capture the offset before the fade, then persist
                                // once the audio has actually stopped, so the next
                                // visit resumes here rather than restarting.
                                val at = MusicPlayer.positionMs()
                                MusicPlayer.pause { musicScope.launch { music.setPosition(at) } }
                            }
                        }
                    }

                    ReaderScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/**
 * The four-tab shell.
 *
 * Tabs are plain state rather than nested routes: switching tabs should not push
 * onto the back stack, so Back from the shelf exits instead of cycling tabs.
 */
@Composable
private fun HomeScaffold(
    liveBrightness: Float?,
    onBrightnessPreview: (Float) -> Unit,
    onOpenBook: (Long) -> Unit
) {
    val c = LocalColors.current
    var tab by rememberSaveable { mutableStateOf(HomeTab.SHELF) }

    val shelfViewModel: ShelfViewModel = viewModel()
    val musicViewModel: MusicViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    Box(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab",
            modifier = Modifier.fillMaxSize()
        ) { current ->
            when (current) {
                HomeTab.SHELF -> ShelfScreen(
                    viewModel = shelfViewModel,
                    onOpenBook = onOpenBook
                )
                HomeTab.MUSIC -> MusicScreen(viewModel = musicViewModel)
                HomeTab.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel,
                    liveBrightness = liveBrightness,
                    onBrightnessPreview = onBrightnessPreview
                )
                HomeTab.ABOUT -> AboutScreen()
            }
        }

        BottomBar(
            selected = tab,
            onSelect = { tab = it },
            // The bar applies its own navigation-bar padding, so this is only a
            // small visual gap above the system bar.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )
    }
}

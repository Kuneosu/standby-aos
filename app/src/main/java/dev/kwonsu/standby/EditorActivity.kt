package dev.kwonsu.standby

import android.os.Bundle
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class EditorActivity : ComponentActivity() {
    private val editor: EditorViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            FoldTheme {
                val state by editor.state.collectAsStateWithLifecycle()
                SceneEditor(state, editor::change, editor::import, { editor.save() },
                    { editor.save { finish() } }, { editor.discard { finish() } }, ::finish, onPreset = editor::preset,
                    onSavePreset = { exit -> editor.save(updatePreset = true) { if (exit) finish() } },
                    onFullscreenChange = ::setFullscreenEditing)
            }
        }
    }
    override fun onStop() { editor.persistDraft(immediate = true); super.onStop() }

    private fun setFullscreenEditing(fullscreen: Boolean) {
        val orientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (requestedOrientation != orientation) requestedOrientation = orientation
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (fullscreen) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else show(WindowInsetsCompat.Type.systemBars())
        }
        if (fullscreen) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

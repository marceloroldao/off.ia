package ia.off

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.setContent

/**
 * Product launcher shell.
 *
 * The normal product surface hides diagnostic-only labelSmall telemetry.
 * Laboratory Mode restores those labels after app restart without changing the
 * underlying chat, Memoria.ia or llama.cpp behavior.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val laboratoryMode = AppSettingsStore(applicationContext).load().laboratoryMode
        setContent {
            OffiaTheme(laboratoryMode = laboratoryMode) {
                OffiaChatScreen()
            }
        }
    }
}

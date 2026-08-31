package ia.off

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.setContent

/**
 * Product launcher shell.
 *
 * MainActivity keeps the chat/orchestration implementation while this launcher
 * applies the canonical OFF.IA Material theme to the whole application.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OffiaTheme {
                OffiaChatScreen()
            }
        }
    }
}

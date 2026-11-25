package ir.blackrise.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VpnManager.init(this)
        setContent {
            BlackRiseTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun BlackRiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF09090B),
            surface = Color(0xFF141417),
            primary = Color(0xFFF97316),
            secondary = Color(0xFF27272A),
            onBackground = Color(0xFFFAFAFA),
            onSurface = Color(0xFFFAFAFA)
        ),
        content = content
    )
}

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import dev.mateuy.garbo.App
import io.github.vinceglb.filekit.FileKit

fun main() {
    FileKit.init(appId = "dev.mateuy.garbo")
    application {
        Window(
            title = "Garbo",
            state = rememberWindowState(width = 800.dp, height = 600.dp),
            onCloseRequest = ::exitApplication,
        ) {
            window.minimumSize = Dimension(350, 600)
            App()
        }
    }
}


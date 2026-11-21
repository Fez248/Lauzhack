package tea.ui.screens

import androidx.compose.runtime.Composable
import tea.ui.screens.gallery.GalleryScreen

/** Entry point shared by the desktop and web shells. Shows the design gallery until the real screens land. */
@Composable
fun App() {
    GalleryScreen()
}

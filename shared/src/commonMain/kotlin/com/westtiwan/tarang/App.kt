package com.westtiwan.tarang

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.LocalNav
import com.westtiwan.tarang.ui.LocalPlatformUi
import com.westtiwan.tarang.ui.Navigator
import com.westtiwan.tarang.ui.PlatformUi
import com.westtiwan.tarang.ui.Root
import com.westtiwan.tarang.ui.TarangTheme
import com.westtiwan.tarang.ui.isDarkTheme
import com.westtiwan.tarang.ui.platformDynamicScheme
import com.westtiwan.tarang.ui.rememberSeedColor

@Composable
fun App(app: AppContainer, navigator: Navigator = remember { Navigator() }, platformUi: PlatformUi = PlatformUi()) {
    val settings by app.settings.collectAsState()
    val player by app.player.state.collectAsState()
    CompositionLocalProvider(LocalApp provides app, LocalNav provides navigator, LocalPlatformUi provides platformUi) {
        val wallpaperAvailable = settings.dynamicColor && platformDynamicScheme(isDarkTheme(settings.theme)) != null
        val artEverywhere = settings.albumArtColors && (settings.albumArtEverywhere || !wallpaperAvailable)
        val seed = if (artEverywhere) rememberSeedColor(player.current?.coverArt) else null
        TarangTheme(settings.theme, settings.dynamicColor, settings.pureBlack, seed) {
            Root()
        }
    }
}

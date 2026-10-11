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

@Composable
fun App(app: AppContainer, navigator: Navigator = remember { Navigator() }, platformUi: PlatformUi = PlatformUi()) {
    val settings by app.settings.collectAsState()
    CompositionLocalProvider(LocalApp provides app, LocalNav provides navigator, LocalPlatformUi provides platformUi) {
        TarangTheme(settings.theme, settings.dynamicColor, settings.pureBlack) {
            Root()
        }
    }
}

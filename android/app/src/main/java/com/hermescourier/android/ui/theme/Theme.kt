
package com.hermescourier.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = HermesBlue,
    secondary = HermesSlate,
    background = HermesNavy,
    surface = HermesNavy,
)

private val LightColors = lightColorScheme(
    primary = HermesBlue,
    secondary = HermesSlate,
    background = HermesSurface,
    surface = HermesSurface,
)

@Composable
fun HermesCourierTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}

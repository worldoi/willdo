package com.antgskds.calendarassistant.ui.theme

import androidx.compose.runtime.Composable

@Composable
fun CalendarAssistantStyleTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    themeColorScheme: ThemeColorScheme,
    customThemeColorHex: String,
    content: @Composable () -> Unit
) {
    CalendarAssistantTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        themeColorScheme = themeColorScheme,
        customThemeColorHex = customThemeColorHex,
        content = content
    )
}

package com.tharunbirla.librecuts.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * 明暗模式（外观）设置的单一事实来源。
 *
 * 默认「跟随系统」；用户在设置页可选「浅色 / 深色」并持久化。
 * 主题在 [android.app.Application.onCreate] 中统一套用，
 * 因此无论从哪个入口进入（主界面、分享意图、崩溃页）都保持一致。
 */
object ThemeMode {

    const val PREFS_NAME = "librecuts_prefs"
    const val KEY = "theme_mode"

    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    fun current(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM
    }

    fun save(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode).apply()
    }

    /** 把持久化的模式套用到 AppCompatDelegate；模式未变化时不会触发重建。 */
    fun apply(context: Context) {
        val nightMode = when (current(context)) {
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
}

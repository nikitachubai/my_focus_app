package com.example.focustime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

private const val PREFS_NAME = "focus_prefs"
private const val KEY_END_TIME_MS = "end_time_ms"

class AppWatcherService : AccessibilityService() {

    private var lastLaunchMs: Long = 0L

    // Не трогаем важные системные пакеты — иначе будут “дергания”/зацикливание
    private val neverBlockPrefixes = listOf(
        "com.android.systemui",
        "com.google.android.permissioncontroller", // на некоторых девайсах префикс
        "com.android.permissioncontroller"
    )

    // ✅ Точное исключение
    private val neverBlockExact = setOf(
        "com.example.focustime",                  // наше приложение
        "com.google.android.apps.nexuslauncher",  // launcher
        "com.android.settings",                   // настройки
        "com.android.permissioncontroller",       // разрешения
        "com.google.android.permissioncontroller" // разрешения (вариант)
    )


    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("FOCUSTIME_WATCH", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        //if (pkg in neverBlockExact) {
         //   Log.d("FOCUSTIME", "Пропускаем свое приложение")
         //   return
        //        }


        if (event == null) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val pkg = event.packageName?.toString() ?: return
        Log.d("FOCUSTIME_WATCH", "Foreground package: $pkg")

        if (!isFocusActive()) return

        // 1) никогда не блокируем системные
        if (pkg in neverBlockExact) return
        if (neverBlockPrefixes.any { pkg.startsWith(it) }) return

        // 2) читаем whitelist из настроек пользователя
        val allowed = AllowedAppsStore.getAllowed(this)

        // 3) если приложение разрешено — пропускаем
        if (pkg in allowed) return

        // 4) анти-спам запуска BlockActivity
        val now = System.currentTimeMillis()
        if (now - lastLaunchMs < 1200) return
        lastLaunchMs = now

        Log.d("FOCUSTIME_WATCH", "BLOCKING: $pkg -> opening BlockActivity")

        startActivity(
            Intent(this, BlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    private fun isFocusActive(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endTime = prefs.getLong(KEY_END_TIME_MS, 0L)
        return endTime > System.currentTimeMillis()
    }

    override fun onInterrupt() {}
}


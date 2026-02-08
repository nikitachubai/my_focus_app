package com.example.focustime

import android.content.Context

object AllowedAppsStore {
    private const val PREFS = "allowed_apps_prefs"
    private const val KEY_ALLOWED = "allowed_pkgs"

    fun getAllowed(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet()
    }

    fun setAllowed(context: Context, pkgs: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_ALLOWED, pkgs).apply()
    }
}

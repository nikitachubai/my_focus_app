package com.example.focustime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Одна модель фокус-сессии
 */
data class FocusSession(
    val startMs: Long,
    val endMs: Long,
    val plannedEndMs: Long,
    val completed: Boolean
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * Основное хранилище статистики (SharedPreferences + JSON)
 */
object FocusStatsStore {
    private const val PREFS = "focus_stats"
    private const val KEY_SESSIONS_JSON = "sessions_json"
    private const val MAX_SESSIONS = 300 // чтобы prefs не раздувались

    fun addSession(context: Context, session: FocusSession) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_SESSIONS_JSON, "[]") ?: "[]")

        val obj = JSONObject().apply {
            put("startMs", session.startMs)
            put("endMs", session.endMs)
            put("plannedEndMs", session.plannedEndMs)
            put("completed", session.completed)
        }
        arr.put(obj)

        // обрезаем, если слишком много
        val trimmed = if (arr.length() > MAX_SESSIONS) {
            val cut = JSONArray()
            val startIndex = arr.length() - MAX_SESSIONS
            for (i in startIndex until arr.length()) cut.put(arr.getJSONObject(i))
            cut
        } else arr

        prefs.edit().putString(KEY_SESSIONS_JSON, trimmed.toString()).apply()
    }

    fun getSessions(context: Context): List<FocusSession> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_SESSIONS_JSON, "[]") ?: "[]")

        val list = ArrayList<FocusSession>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                FocusSession(
                    startMs = o.optLong("startMs", 0L),
                    endMs = o.optLong("endMs", 0L),
                    plannedEndMs = o.optLong("plannedEndMs", 0L),
                    completed = o.optBoolean("completed", false)
                )
            )
        }
        return list
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SESSIONS_JSON).apply()
    }

    fun getTotalFocusMs(context: Context): Long =
        getSessions(context).sumOf { it.durationMs }

    fun getCompletedCount(context: Context): Int =
        getSessions(context).count { it.completed }
}


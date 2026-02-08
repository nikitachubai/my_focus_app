package com.example.focustime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

private const val PREFS_NAME = "focus_prefs"
private const val KEY_END_TIME_MS = "end_time_ms"
private const val KEY_DURATION_MIN = "duration_min"
private const val KEY_START_TIME_MS = "start_time_ms"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FocusApp()
            }
        }
    }
}

@Composable
private fun FocusApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var endTimeMs by remember { mutableStateOf(prefs.getLong(KEY_END_TIME_MS, 0L)) }
    var durationMin by remember { mutableStateOf(prefs.getInt(KEY_DURATION_MIN, 60)) }
    var startTimeMs by remember { mutableStateOf(prefs.getLong(KEY_START_TIME_MS, 0L)) }

    fun saveEndTime(value: Long) {
        prefs.edit().putLong(KEY_END_TIME_MS, value).apply()
        endTimeMs = value
    }

    fun saveDurationMin(value: Int) {
        prefs.edit().putInt(KEY_DURATION_MIN, value).apply()
        durationMin = value
    }

    fun saveStartTime(value: Long) {
        prefs.edit().putLong(KEY_START_TIME_MS, value).apply()
        startTimeMs = value
    }

    fun finishSession(completed: Boolean) {
        val now = System.currentTimeMillis()
        val s = startTimeMs
        val planned = endTimeMs

        // если старт реально был — сохраняем
        if (s > 0L) {
            FocusStatsStore.addSession(
                context,
                FocusSession(
                    startMs = s,
                    endMs = now,
                    plannedEndMs = planned,
                    completed = completed
                )
            )
        }

        saveEndTime(0L)
        saveStartTime(0L)
    }

    // роутинг
    var screen by remember { mutableStateOf("main") } // main | apps | stats

    // тикер
    var remainingSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(endTimeMs) {
        while (true) {
            val now = System.currentTimeMillis()
            val diffMs = endTimeMs - now
            remainingSeconds = if (diffMs > 0) diffMs / 1000 else 0

            // таймер закончился
            if (endTimeMs != 0L && diffMs <= 0) {
                finishSession(completed = true)
            }

            delay(1000)
        }
    }

    val isActive = endTimeMs != 0L

    when (screen) {
        "apps" -> {
            AllowedAppsScreen(onBack = { screen = "main" })
            return
        }
        "stats" -> {
            StatsScreen(onBack = { screen = "main" })
            return
        }
    }

    if (!isActive) {
        ReadyScreen(
            durationMin = durationMin,
            onDurationChange = { saveDurationMin(it) },
            onStart = {
                val now = System.currentTimeMillis()
                val newEnd = now + durationMin * 60_000L
                saveStartTime(now)
                saveEndTime(newEnd)
            },
            onOpenAllowedApps = { screen = "apps" },
            onOpenStats = { screen = "stats" }
        )
    } else {
        ActiveFocusScreen(
            remainingSeconds = remainingSeconds,
            endTimeMs = endTimeMs,
            onStop = { finishSession(completed = false) },
            onOpenAllowedApps = { screen = "apps" },
            onOpenStats = { screen = "stats" }
        )
    }
}

@Composable
private fun ReadyScreen(
    durationMin: Int,
    onDurationChange: (Int) -> Unit,
    onStart: () -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenStats: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Ready", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenAllowedApps) { Text("Приложения") }
                OutlinedButton(onClick = onOpenStats) { Text("Статистика") }
            }

            Spacer(Modifier.height(24.dp))

            CircularMinutePicker(
                minutes = durationMin,
                onMinutesChange = onDurationChange,
                minMinutes = 1,
                maxMinutes = 90,
                sizeDp = 260.dp
            )

            Spacer(Modifier.height(20.dp))

            Button(onClick = onStart) { Text("Start") }
        }
    }
}

@Composable
private fun ActiveFocusScreen(
    remainingSeconds: Long,
    endTimeMs: Long,
    onStop: () -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenStats: () -> Unit
) {
    val mm = remainingSeconds / 60
    val ss = remainingSeconds % 60
    val timeLeftText = String.format("%02d:%02d", mm, ss)

    val endText = remember(endTimeMs) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        "до ${fmt.format(Date(endTimeMs))}"
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Focus active", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Text(endText, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(18.dp))
            Text(timeLeftText, style = MaterialTheme.typography.displayMedium)

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenAllowedApps) { Text("Приложения") }
                OutlinedButton(onClick = onOpenStats) { Text("Статистика") }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onStop) { Text("Stop") }
        }
    }
}

/**
 * Экран статистики (простая версия)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sessions = remember { FocusStatsStore.getSessions(context) }.reversed()

    val totalMs = remember(sessions) { sessions.sumOf { it.durationMs } }
    val completedCount = remember(sessions) { sessions.count { it.completed } }

    fun formatDuration(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0L)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}ч ${m}м" else "${m}м"
    }

    val dtFmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Сессий: ${sessions.size}", style = MaterialTheme.typography.titleMedium)
            Text("Завершено: $completedCount", style = MaterialTheme.typography.bodyMedium)
            Text("Фокус всего: ${formatDuration(totalMs)}", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))

            if (sessions.isEmpty()) {
                Text("Пока нет записей. Запусти фокус — и тут появится история.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sessions, key = { it.startMs }) { s ->
                        val start = dtFmt.format(Date(s.startMs))
                        val dur = formatDuration(s.durationMs)
                        val status = if (s.completed) "✅" else "⛔"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(status)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$start • $dur", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (s.completed) "завершено" else "остановлено",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

/**
 * Круглый выбор минут: 1..90
 */
@Composable
private fun CircularMinutePicker(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    minMinutes: Int,
    maxMinutes: Int,
    sizeDp: androidx.compose.ui.unit.Dp
) {
    val strokePx = with(LocalDensity.current) { 16.dp.toPx() }
    val handleRadiusPx = with(LocalDensity.current) { 10.dp.toPx() }

    val ringColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary

    fun minutesToAngleRad(m: Int): Float {
        val t = (m - minMinutes).toFloat() / (maxMinutes - minMinutes).toFloat()
        val angleDeg = -90f + t * 360f
        return Math.toRadians(angleDeg.toDouble()).toFloat()
    }

    fun angleRadToMinutes(angleRad: Float): Int {
        var deg = Math.toDegrees(angleRad.toDouble()).toFloat()
        deg = (deg + 360f + 90f) % 360f
        val t = deg / 360f
        val m = (minMinutes + t * (maxMinutes - minMinutes)).roundToInt()
        return m.coerceIn(minMinutes, maxMinutes)
    }

    val angleRad = remember(minutes) { minutesToAngleRad(minutes) }

    Box(
        modifier = Modifier
            .size(sizeDp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val v = offset - center
                        val a = atan2(v.y, v.x)
                        onMinutesChange(angleRadToMinutes(a))
                    },
                    onDrag = { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val v = change.position - center
                        val a = atan2(v.y, v.x)
                        onMinutesChange(angleRadToMinutes(a))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (min(size.width, size.height) / 2f) - strokePx

            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx)
            )

            val t = (minutes - minMinutes).toFloat() / (maxMinutes - minMinutes).toFloat()
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * t,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            val handleX = center.x + cos(angleRad) * radius
            val handleY = center.y + sin(angleRad) * radius
            drawCircle(
                color = progressColor,
                radius = handleRadiusPx,
                center = Offset(handleX, handleY)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$minutes", style = MaterialTheme.typography.displaySmall)
            Text(text = "минут", style = MaterialTheme.typography.bodyMedium)
            Text(text = "1–90", style = MaterialTheme.typography.bodySmall)
        }
    }
}

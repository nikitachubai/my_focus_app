package com.example.focustime

import android.content.Context
import android.content.pm.PackageManager
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
//import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*
import androidx.compose.material3.ExperimentalMaterial3Api
import android.content.Intent
import android.os.Build
//import androidx.compose.ui.platform.LocalContext



private const val PREFS_NAME = "focus_prefs"
private const val KEY_END_TIME_MS = "end_time_ms"
private const val KEY_DURATION_MIN = "duration_min"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FocusApp(this)
            }
        }
    }
}

@Composable
private fun FocusApp(context: Context) {
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var endTimeMs by remember { mutableStateOf(prefs.getLong(KEY_END_TIME_MS, 0L)) }
    var durationMin by remember { mutableStateOf(prefs.getInt(KEY_DURATION_MIN, 60)) }

    fun saveEndTime(value: Long) {
        prefs.edit().putLong(KEY_END_TIME_MS, value).apply()
        endTimeMs = value
    }

    fun saveDurationMin(value: Int) {
        prefs.edit().putInt(KEY_DURATION_MIN, value).apply()
        durationMin = value
    }

    // простой “роутер” экранов
    var screen by remember { mutableStateOf("main") } // "main" | "apps"

    // тикер времени
    var remainingSeconds by remember { mutableStateOf(0L) }


    LaunchedEffect(Unit) {
        // При запуске MainActivity закрываем все BlockActivity
        val intent = Intent(context, BlockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.stopService(Intent(context, BlockActivity::class.java))
    }

    LaunchedEffect(endTimeMs) {
        while (true) {
            val now = System.currentTimeMillis()
            val diffMs = endTimeMs - now
            remainingSeconds = if (diffMs > 0) diffMs / 1000 else 0

            // если таймер закончился — сбрасываем фокус
            if (endTimeMs != 0L && diffMs <= 0) {
                saveEndTime(0L)
            }

            delay(1000)
        }
    }

    val isActive = endTimeMs != 0L

    // если мы в настройке разрешённых приложений — показываем этот экран и выходим
    if (screen == "apps") {
        AllowedAppsScreen(onBack = { screen = "main" })
        return
    }

    if (!isActive) {
        ReadyScreen(
            durationMin = durationMin,
            onDurationChange = { saveDurationMin(it) },
            onStart = {
                val newEnd = System.currentTimeMillis() + durationMin * 60_000L
                saveEndTime(newEnd)
            },
            onOpenAllowedApps = { screen = "apps" }
        )
    } else {
        ActiveFocusScreen(
            remainingSeconds = remainingSeconds,
            endTimeMs = endTimeMs,
            onStop = { saveEndTime(0L) }
        )
    }
}

@Composable
private fun ReadyScreen(
    durationMin: Int,
    onDurationChange: (Int) -> Unit,
    onStart: () -> Unit,
    onOpenAllowedApps: () -> Unit
) {
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Ready", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { openAccessibilitySettings(context) }
            ) {
                Text("Включить Accessibility")
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { openUsageStatsSettings(context) }
            ) {
                Text("Включить Usage Access")
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

            Button(onClick = onStart) {
                Text("Start")
            }
        }
    }
}


@Composable
private fun ActiveFocusScreen(
    remainingSeconds: Long,
    endTimeMs: Long,
    onStop: () -> Unit
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

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onStop) { Text("Stop") }
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
        deg = (deg + 360f + 90f) % 360f // 0..360, 0 = верх
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

// ---------- Allowed apps UI + Store ----------

data class AppRow(
    val label: String,
    val pkg: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedAppsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    var query by remember { mutableStateOf("") }

    // список приложений (загружаем один раз)
    val apps = remember {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(
                launchIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launchIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        resolved
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                AppRow(
                    label = ri.loadLabel(pm)?.toString() ?: pkg,
                    pkg = pkg
                )
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    var allowed by remember { mutableStateOf(AllowedAppsStore.getAllowed(context).toMutableSet()) }

    // всегда разрешаем само приложение (чтобы не заблокировать себя)
    LaunchedEffect(Unit) {
        allowed.add("com.example.focustime")
        AllowedAppsStore.setAllowed(context, allowed)
    }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                    it.pkg.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Разрешённые приложения") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск") }
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Во время фокуса всё, кроме отмеченных, будет блокироваться.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.pkg }) { app ->
                    val checked = allowed.contains(app.pkg)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newAllowed = allowed.toMutableSet()
                                if (checked) newAllowed.remove(app.pkg) else newAllowed.add(app.pkg)
                                allowed = newAllowed
                                AllowedAppsStore.setAllowed(context, allowed)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                val newAllowed = allowed.toMutableSet()
                                if (isChecked) newAllowed.add(app.pkg) else newAllowed.remove(app.pkg)
                                allowed = newAllowed
                                AllowedAppsStore.setAllowed(context, allowed)
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }

                    Divider()
                }
            }
        }
    }
}

/**
 * Хранилище разрешённых пакетов (whitelist)
 * SharedPreferences StringSet
 */
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

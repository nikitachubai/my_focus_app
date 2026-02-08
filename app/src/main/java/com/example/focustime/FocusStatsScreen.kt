package com.example.focustime

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusStatsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // грузим сессии (и обновляем при заходе на экран)
    var sessions by remember { mutableStateOf(emptyList<FocusSession>()) }

    LaunchedEffect(Unit) {
        sessions = FocusStatsStore.getSessions(context) // <-- подстрой под своё API
            .sortedByDescending { it.startMs }
    }

    val totalCount = sessions.size
    val completedCount = sessions.count { it.completed }
    val totalMs = sessions.sumOf { safeDurationMs(it) }
    val totalMinutes = (totalMs / 60_000L).toInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    TextButton(onClick = {
                        // если у тебя есть clear()
                        // FocusStatsStore.clear(context)
                        // sessions = emptyList()
                    }) { Text("Очистить") }
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

            // SUMMARY
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Всего сессий: $totalCount", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Завершено: $completedCount")
                    Text("Всего фокуса: $totalMinutes мин")
                }
            }

            Spacer(Modifier.height(12.dp))

            // LIST
            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Пока нет сессий")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions, key = { it.startMs }) { s ->
                        SessionRow(s)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: FocusSession) {
    val fmtDate = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val startText = fmtDate.format(Date(s.startMs))
    val durMs = safeDurationMs(s)
    val durMin = (durMs / 60_000L).toInt()

    val status = if (s.completed) "✅ завершено" else "⛔ остановлено"
    val endText = remember(s.endMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.endMs))
    }

    Card {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    startText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("$durMin мин", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text("$status • до $endText", style = MaterialTheme.typography.bodyMedium)

            // если есть plannedEndMs — покажем “план”
            val planned = s.plannedEndMs
            if (planned != null && planned > 0L) {
                val plannedText = remember(planned) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(planned))
                }
                Spacer(Modifier.height(4.dp))
                Text("План: до $plannedText", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun safeDurationMs(s: FocusSession): Long {
    val end = max(s.endMs, s.startMs)
    return max(0L, end - s.startMs)
}



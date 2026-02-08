package com.example.focustime

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
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
import java.util.Locale

data class AppRow(val label: String, val pkg: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    var query by remember { mutableStateOf("") }

    val apps = remember {
        val installed = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(
                    (PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(
                PackageManager.GET_META_DATA or
                        PackageManager.MATCH_UNINSTALLED_PACKAGES or
                        PackageManager.MATCH_DISABLED_COMPONENTS
            )
        }

        installed
            .asSequence()
            // оставляем только запускаемые (есть launcher intent)
            .filter { ai -> pm.getLaunchIntentForPackage(ai.packageName) != null }
            // иногда полезно скрыть себя из списка (по желанию)
            // .filter { ai -> ai.packageName != context.packageName }
            .map { ai ->
                val pkg = ai.packageName
                val label = pm.getApplicationLabel(ai)?.toString() ?: pkg
                AppRow(label = label, pkg = pkg)
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    var allowed by remember { mutableStateOf(AllowedAppsStore.getAllowed(context).toMutableSet()) }

    LaunchedEffect(Unit) {
        allowed.add(context.packageName) // всегда разрешаем себя
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск") }
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


@file:OptIn(ExperimentalMaterial3Api::class)

package com.dailynotes.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailynotes.app.NotesViewModel
import com.dailynotes.app.R
import com.dailynotes.app.data.Note
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun fmt(epoch: Long, pattern: String): String =
    LocalDate.ofEpochDay(epoch).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

private val transparentField @Composable get() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
)

// ───────────────────────── Home ─────────────────────────

@Composable
fun HomeScreen(vm: NotesViewModel, onOpen: (Long) -> Unit, onTrash: () -> Unit, onSettings: () -> Unit) {
    val day by vm.day.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val marked by vm.daysWithNotes.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val movedMsg = stringResource(R.string.moved_to_trash)
    val undoLabel = stringResource(R.string.undo)
    val today = LocalDate.now().toEpochDay()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = { vm.query.value = it }, singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            colors = transparentField, modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            if (day == today) stringResource(R.string.today) + " · " + fmt(day, "d MMM")
                            else fmt(day, "EEEE d MMM yyyy"),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; vm.query.value = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    } else {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, null) }
                        IconButton(onClick = { picker = true }) {
                            Icon(Icons.Default.CalendarMonth, stringResource(R.string.pick_date))
                        }
                        IconButton(onClick = onTrash) { Icon(Icons.Default.Delete, stringResource(R.string.trash)) }
                        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(0L) }) { Icon(Icons.Default.Add, null) }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (!searching) DayStrip(day, marked) { vm.day.value = it }
            val list = if (searching) results else notes
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (searching) R.string.no_results else R.string.no_notes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { n ->
                        val state = rememberSwipeToDismissBoxState(confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.Settled) false else {
                                vm.trashNote(n.id)
                                scope.launch {
                                    if (snack.showSnackbar(movedMsg, undoLabel, duration = SnackbarDuration.Short)
                                        == SnackbarResult.ActionPerformed) vm.restore(n.id)
                                }
                                true
                            }
                        })
                        SwipeToDismissBox(state, backgroundContent = {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) { Icon(Icons.Default.Delete, null) }
                        }) { NoteCard(n, showDate = searching) { onOpen(n.id) } }
                    }
                }
            }
        }
    }

    if (picker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { vm.day.value = it / 86_400_000L }
                    picker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(st) }
    }
}

@Composable
private fun DayStrip(selected: Long, marked: Set<Long>, onSelect: (Long) -> Unit) {
    val today = LocalDate.now().toEpochDay()
    val first = today - 60
    val days = remember { (first..today + 14).toList() }
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        val idx = (selected - first).toInt()
        if (idx in days.indices) listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { d ->
            val sel = d == selected
            Surface(
                onClick = { onSelect(d) }, shape = RoundedCornerShape(14.dp),
                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(54.dp)
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt(d, "EEE"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(fmt(d, "d"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier.size(5.dp).background(
                            if (d in marked) LocalContentColor.current else Color.Transparent, CircleShape
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCard(n: Note, showDate: Boolean, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.title.ifBlank { n.content.lineSequence().firstOrNull().orEmpty() },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (n.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
            }
            if (n.title.isNotBlank() && n.content.isNotBlank()) {
                Text(
                    n.content, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (n.category.isNotBlank()) {
                    Text(
                        n.category, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Text(
                    if (showDate) fmt(n.dayEpoch, "d MMM yyyy") else
                        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(n.updatedAt),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ───────────────────────── Editor ─────────────────────────

@Composable
fun EditorScreen(vm: NotesViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(false) }
    var noteId by rememberSaveable { mutableLongStateOf(id) }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var dayEpoch by rememberSaveable { mutableLongStateOf(vm.day.value) }
    var created by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var lastSaved by rememberSaveable { mutableStateOf("") }

    fun key() = listOf(title, content, category, pinned).joinToString("\u0000")

    LaunchedEffect(Unit) {
        if (!loaded) {
            if (id != 0L) vm.get(id)?.let {
                title = it.title; content = it.content; category = it.category
                pinned = it.pinned; dayEpoch = it.dayEpoch; created = it.createdAt
            }
            lastSaved = key()
            loaded = true
        }
    }

    suspend fun save() {
        val k = key()
        if (!loaded || k == lastSaved) return
        if (title.isBlank() && content.isBlank() && noteId == 0L) return
        val res = vm.upsert(
            Note(id = noteId, title = title.trim(), content = content, category = category.trim(),
                dayEpoch = dayEpoch, pinned = pinned, createdAt = created)
        )
        if (noteId == 0L) noteId = res
        lastSaved = k
    }

    LaunchedEffect(title, content, category, pinned, loaded) {
        if (loaded) { delay(600); save() }
    }

    val goBack: () -> Unit = { scope.launch { save(); onBack() } }
    BackHandler(onBack = goBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fmt(dayEpoch, "EEE d MMM yyyy"), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(
                            Icons.Default.PushPin, stringResource(R.string.pin),
                            tint = if (pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, listOf(title, content).filter { it.isNotBlank() }.joinToString("\n\n"))
                        ctx.startActivity(Intent.createChooser(send, null))
                    }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
                    IconButton(onClick = {
                        if (noteId != 0L) vm.trashNote(noteId)
                        onBack()
                    }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    content += (if (content.isEmpty() || content.endsWith("\n")) "" else "\n") + "☐ "
                }) { Icon(Icons.Default.CheckBox, stringResource(R.string.checklist)) }
                Spacer(Modifier.weight(1f))
                val words = content.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                Text(
                    stringResource(R.string.stats, words, content.length),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                colors = transparentField, modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = category, onValueChange = { category = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.category)) },
                colors = transparentField, modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = content, onValueChange = { content = it },
                placeholder = { Text(stringResource(R.string.content_hint)) },
                colors = transparentField, modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

// ───────────────────────── Trash ─────────────────────────

@Composable
fun TrashScreen(vm: NotesViewModel, onBack: () -> Unit) {
    val trash by vm.trash.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.trash)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                if (trash.isNotEmpty()) IconButton(onClick = { vm.emptyTrash() }) {
                    Icon(Icons.Default.DeleteSweep, stringResource(R.string.empty_trash))
                }
            }
        )
    }) { pad ->
        if (trash.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trash, key = { it.id }) { n ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    n.title.ifBlank { n.content }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(fmt(n.dayEpoch, "d MMM yyyy"), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline)
                            }
                            IconButton(onClick = { vm.restore(n.id) }) { Icon(Icons.Default.Restore, stringResource(R.string.restore)) }
                            IconButton(onClick = { vm.purge(n.id) }) { Icon(Icons.Default.DeleteForever, stringResource(R.string.delete_forever)) }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Settings ─────────────────────────

@Composable
fun SettingsScreen(vm: NotesViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val theme by vm.theme.collectAsStateWithLifecycle()
    val remOn by vm.reminderOn.collectAsStateWithLifecycle()
    val hour by vm.hour.collectAsStateWithLifecycle()
    val minute by vm.minute.collectAsStateWithLifecycle()
    val okBackup = stringResource(R.string.backup_done)
    val failed = stringResource(R.string.failed)
    val importedFmt = stringResource(R.string.restore_done, 0)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { scope.launch { snack.showSnackbar(if (vm.export(it)) okBackup else failed) } }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                val n = vm.import(it)
                snack.showSnackbar(if (n >= 0) importedFmt.replace("0", n.toString()) else failed)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark).forEachIndexed { i, r ->
                        FilterChip(selected = theme == i, onClick = { vm.setTheme(i) }, label = { Text(stringResource(r)) })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminder), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = remOn, onCheckedChange = {
                        if (it && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        vm.setReminder(it)
                    })
                }
                if (remOn) {
                    val label = "%02d:%02d".format(hour, minute)
                    OutlinedButton(onClick = {
                        TimePickerDialog(ctx, { _, h, m -> vm.setReminder(true, h, m) }, hour, minute, true).show()
                    }) { Text(stringResource(R.string.reminder_time, label)) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleSmall)
                Button(onClick = { exporter.launch("daily-notes-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_notes))
                }
                OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_notes))
                }
            }
        }
    }
}

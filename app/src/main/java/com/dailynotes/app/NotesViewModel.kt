package com.dailynotes.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailynotes.app.data.AppDatabase
import com.dailynotes.app.data.Note
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(private val app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private fun <T> Flow<T>.keep(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val day = MutableStateFlow(LocalDate.now().toEpochDay())
    val query = MutableStateFlow("")

    val notes = day.flatMapLatest { dao.byDay(it) }.keep(emptyList())
    val results = query.flatMapLatest {
        if (it.isBlank()) flowOf(emptyList()) else dao.search(it.trim())
    }.keep(emptyList())
    val trash = dao.trash().keep(emptyList())
    val daysWithNotes = dao.days().map { it.toSet() }.keep(emptySet())

    val theme = MutableStateFlow(prefs.getInt("theme", 0))
    val reminderOn = MutableStateFlow(prefs.getBoolean("rem", false))
    val hour = MutableStateFlow(prefs.getInt("hour", 21))
    val minute = MutableStateFlow(prefs.getInt("minute", 0))

    fun setTheme(v: Int) { theme.value = v; prefs.edit().putInt("theme", v).apply() }

    fun setReminder(on: Boolean, h: Int = hour.value, m: Int = minute.value) {
        reminderOn.value = on; hour.value = h; minute.value = m
        prefs.edit().putBoolean("rem", on).putInt("hour", h).putInt("minute", m).apply()
        if (on) Reminder.schedule(app, h, m) else Reminder.cancel(app)
    }

    suspend fun get(id: Long) = dao.get(id)
    suspend fun upsert(n: Note) = dao.upsert(n)
    fun trashNote(id: Long) = viewModelScope.launch { dao.setDeleted(id, true, System.currentTimeMillis()) }
    fun restore(id: Long) = viewModelScope.launch { dao.setDeleted(id, false, System.currentTimeMillis()) }
    fun purge(id: Long) = viewModelScope.launch { dao.purge(id) }
    fun emptyTrash() = viewModelScope.launch { dao.emptyTrash() }

    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            dao.all().forEach {
                arr.put(JSONObject()
                    .put("title", it.title).put("content", it.content).put("category", it.category)
                    .put("day", it.dayEpoch).put("pinned", it.pinned).put("deleted", it.deleted)
                    .put("created", it.createdAt).put("updated", it.updatedAt))
            }
            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(arr.toString(2).toByteArray()) }
            true
        }.getOrDefault(false)
    }

    /** Returns number of imported notes, or -1 on failure. Skips notes that already exist. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            val text = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val arr = JSONArray(text)
            val existing = dao.all().map { it.createdAt to it.content }.toSet()
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                Note(
                    title = it.optString("title"), content = it.optString("content"),
                    category = it.optString("category"), dayEpoch = it.getLong("day"),
                    pinned = it.optBoolean("pinned"), deleted = it.optBoolean("deleted"),
                    createdAt = it.optLong("created", System.currentTimeMillis()),
                    updatedAt = it.optLong("updated", System.currentTimeMillis())
                )
            }.filter { (it.createdAt to it.content) !in existing }
            dao.insertAll(list)
            list.size
        }.getOrDefault(-1)
    }
}

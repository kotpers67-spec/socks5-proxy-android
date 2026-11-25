package com.socks5proxy.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Кольцевой буфер диагностических событий приложения (в памяти, без записи секретов на диск).
 */
object AppLogBuffer {
    private const val MAX_ENTRIES = 120
    private val lock = Any()
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
    val logsFlow: StateFlow<List<String>> = _logsFlow.asStateFlow()

    fun log(tag: String, message: String) {
        val timestamp = synchronized(lock) { timeFormat.format(Date()) }
        val line = "[$timestamp] [$tag] $message"
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(line)
            _logsFlow.value = entries.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            _logsFlow.value = emptyList()
        }
    }
}

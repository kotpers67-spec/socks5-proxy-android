package com.socks5proxy.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Монитор состояния экрана устройства на базе BroadcastReceiver и PowerManager.
 * Позволяет фоновым сервисам адаптировать частоту поллинга сетевых метрик:
 * при выключенном дисплее циклы опроса засыпают, исключая пробуждения CPU и экономя батарею.
 */
class ScreenStateMonitor(private val context: Context) {

    private val isRegistered = AtomicBoolean(false)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val initialScreenState: Boolean
        get() = powerManager?.isInteractive ?: true

    private val _isScreenOn = MutableStateFlow(initialScreenState)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Экран включен (ACTION_SCREEN_ON)")
                    _isScreenOn.value = true
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Экран выключен (ACTION_SCREEN_OFF)")
                    _isScreenOn.value = false
                }
            }
        }
    }

    init {
        register()
    }

    private fun register() {
        if (isRegistered.compareAndSet(false, true)) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.registerReceiver(
                        context,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    context.registerReceiver(receiver, filter)
                }
                _isScreenOn.value = initialScreenState
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка регистрации ScreenStateMonitor: ${e.message}")
            }
        }
    }

    /**
     * Адаптивная задержка для циклов опроса статистики:
     * - При включенном экране: задержка activeIntervalMs (например, 1500 мс).
     * - При выключенном экране: засыпает на idleIntervalMs (например, 15000-30000 мс)
     *   или до момента включения экрана (через StateFlow.first { it }), мгновенно возобновляя опрос.
     * Возвращает true, если экран активен, и false, если экран оставался выключенным.
     */
    suspend fun awaitScreenOnOrInterval(
        activeIntervalMs: Long = 1500L,
        idleIntervalMs: Long = 15000L
    ): Boolean {
        if (_isScreenOn.value) {
            delay(activeIntervalMs)
            return _isScreenOn.value
        }

        // Экран выключен: ждем включения экрана или истечения idle-интервала
        val screenTurnedOn = withTimeoutOrNull(idleIntervalMs) {
            _isScreenOn.first { it }
        }
        return screenTurnedOn == true
    }

    /**
     * Освобождает системные ресурсы и снимает регистрацию BroadcastReceiver.
     */
    fun destroy() {
        if (isRegistered.compareAndSet(true, false)) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка дерегистрации ScreenStateMonitor: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ScreenStateMonitor"
    }
}

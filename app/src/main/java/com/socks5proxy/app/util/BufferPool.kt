package com.socks5proxy.app.util

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Потокобезопасный пул фиксированных 32 KB байтовых буферов для сетевого ввода-вывода (I/O).
 * Устраняет непрерывные аллокации памяти в цикле TCP-релея и минимизирует паузы сборщика мусора (GC).
 */
object BufferPool {
    const val BUFFER_SIZE = 32 * 1024 // 32 KB = 32768 байт
    const val MAX_POOL_SIZE = 64     // Максимум 64 буфера (2 МБ в памяти)

    private val pool = ConcurrentLinkedQueue<ByteArray>()
    private val currentCount = AtomicInteger(0)

    /**
     * Текущее количество свободных буферов в пуле.
     */
    val availableCount: Int
        get() = currentCount.get()

    /**
     * Извлекает существующий буфер из пула или создает новый ByteArray(BUFFER_SIZE),
     * если пул пуст.
     */
    fun acquire(): ByteArray {
        val buffer = pool.poll()
        if (buffer != null) {
            currentCount.decrementAndGet()
            return buffer
        }
        return ByteArray(BUFFER_SIZE)
    }

    /**
     * Возвращает буфер обратно в пул.
     * Проверяет точное совпадение размера BUFFER_SIZE и лимит пула MAX_POOL_SIZE.
     * Неподходящие или избыточные буферы отбрасываются для сборщика мусора.
     */
    fun release(buffer: ByteArray) {
        if (buffer.size != BUFFER_SIZE) {
            return
        }

        while (true) {
            val count = currentCount.get()
            if (count >= MAX_POOL_SIZE) {
                return
            }
            if (currentCount.compareAndSet(count, count + 1)) {
                pool.offer(buffer)
                return
            }
        }
    }

    /**
     * Очищает пул буферов.
     */
    fun clear() {
        pool.clear()
        currentCount.set(0)
    }

    /**
     * Выполняет операцию над заимствованным буфером с гарантированным возвратом в пул в блоке finally.
     */
    inline fun <T> useBuffer(block: (ByteArray) -> T): T {
        val buffer = acquire()
        return try {
            block(buffer)
        } finally {
            release(buffer)
        }
    }
}

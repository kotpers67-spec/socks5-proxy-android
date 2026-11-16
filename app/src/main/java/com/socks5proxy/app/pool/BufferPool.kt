package com.socks5proxy.app.pool

import java.util.concurrent.ConcurrentLinkedQueue

object BufferPool {
    private const val BUFFER_SIZE = 32768
    private const val MAX_POOL_SIZE = 64

    private val pool = ConcurrentLinkedQueue<ByteArray>()

    fun acquire(): ByteArray {
        return pool.poll() ?: ByteArray(BUFFER_SIZE)
    }

    fun release(buffer: ByteArray) {
        if (buffer.size == BUFFER_SIZE && pool.size < MAX_POOL_SIZE) {
            pool.offer(buffer)
        }
    }
}

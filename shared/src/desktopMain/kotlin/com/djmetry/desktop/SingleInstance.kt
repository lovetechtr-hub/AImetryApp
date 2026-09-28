package com.djmetry.desktop

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import kotlin.concurrent.thread

/**
 * Одна копия приложения. На Windows и Linux ОС открывает `djmetry://oauth?code=…`,
 * запуская НОВЫЙ процесс с URL в аргументах. Второй экземпляр пересылает URL первому
 * через локальный сокет (только 127.0.0.1) и завершается.
 */
class SingleInstance(private val dir: File) {
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    /**
     * true — мы первый экземпляр, [onMessage] будет получать пересланные ссылки.
     * false — приложение уже запущено, [forward] отправлен ему.
     */
    fun acquireOrForward(forward: String?, onMessage: (String) -> Unit): Boolean {
        dir.mkdirs()
        val channel = RandomAccessFile(File(dir, "instance.lock"), "rw").channel
        // В другом процессе tryLock вернёт null, в этом же — бросит OverlappingFileLockException
        val acquired = try { channel.tryLock() } catch (e: java.nio.channels.OverlappingFileLockException) { null }
        if (acquired == null) {
            channel.close()
            forward?.let(::send)
            return false
        }
        lock = acquired
        val socket = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        server = socket
        File(dir, "instance.port").writeText(socket.localPort.toString())
        thread(isDaemon = true, name = "djmetry-single-instance") {
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { client ->
                        client.getInputStream().bufferedReader().readLine()?.takeIf { it.isNotBlank() }?.let(onMessage)
                    }
                }
            }
        }
        return true
    }

    private fun send(message: String) {
        val port = runCatching { File(dir, "instance.port").readText().trim().toInt() }.getOrNull() ?: return
        runCatching {
            Socket(InetAddress.getLoopbackAddress(), port).use { it.getOutputStream().write((message + "\n").toByteArray()) }
        }
    }

    fun release() {
        runCatching { server?.close() }
        runCatching { lock?.release(); lock?.channel()?.close() }
    }
}

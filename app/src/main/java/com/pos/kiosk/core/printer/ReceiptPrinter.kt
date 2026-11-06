package com.pos.kiosk.core.printer

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class PrinterTarget(val host: String, val port: Int)

/** A printer reached over the network. The device's own printer is [com.pos.kiosk.core.printer.builtin.BuiltInPrinter]. */
interface ReceiptPrinter {
    suspend fun print(target: PrinterTarget, image: MonochromeImage, copies: Int = 1): AppResult<Unit>
}

/**
 * Raw ESC/POS over TCP (port 9100), the protocol LAN thermal printers speak.
 * Jobs are serialized: most LAN printers accept a single connection, so a second concurrent socket
 * (receipt + admin test page, or a double "print again") would fail or interleave.
 */
@Singleton
class EscPosLanPrinter @Inject constructor() : ReceiptPrinter {

    private val printerLock = Mutex()

    override suspend fun print(target: PrinterTarget, image: MonochromeImage, copies: Int): AppResult<Unit> {
        val payload = withContext(Dispatchers.Default) { EscPosRaster.encode(image) }
        return printerLock.withLock {
            // Deliberately NOT cancellable once the socket work starts: a customer tapping "done" (which
            // clears the success screen's scope) must not cut a receipt off halfway. The connect/write
            // timeouts bound how long this can block.
            withContext(Dispatchers.IO) {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS)
                        socket.soTimeout = WRITE_TIMEOUT_MS
                        socket.getOutputStream().apply {
                            repeat(copies.coerceIn(1, 3)) { write(payload) }
                            flush()
                        }
                    }
                    AppResult.Success(Unit)
                } catch (e: IOException) {
                    AppResult.Failure(AppError.PrinterUnavailable)
                }
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val WRITE_TIMEOUT_MS = 10_000
    }
}

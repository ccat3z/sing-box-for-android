package io.nekohasekai.sfa.bg

import android.util.Log
import io.nekohasekai.libbox.LogEntry
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Resident mirror of sing-box logs into logcat (tag "sing-box").
 *
 * sing-box logs normally only live in the in-memory buffers held by UI
 * [CommandClient.Handler]s (e.g. [io.nekohasekai.sfa.compose.screen.log.LogViewModel]),
 * which subscribe to the libbox log stream only while the Logs tab is open and
 * the app is in the foreground. That makes the logs unreachable from
 * `adb logcat` for headless/emulator diagnostics.
 *
 * This handler is a long-lived [CommandClient.ConnectionType.Log] subscriber
 * owned by [BoxService] — it stays connected for the whole lifetime of the
 * service, so logs reach logcat in real time regardless of UI state. It only
 * mirrors to logcat; it does not retain logs in memory. The libbox server
 * supports multiple concurrent log subscribers, so this coexists with the
 * UI log client.
 */
class LogcatLogMirror : CommandClient.Handler {
    @OptIn(DelicateCoroutinesApi::class)
    private val commandClient =
        CommandClient(GlobalScope, CommandClient.ConnectionType.Log, this)

    private var stopped = false

    fun connect() {
        stopped = false
        commandClient.connect()
    }

    fun disconnect() {
        stopped = true
        commandClient.disconnect()
    }

    override fun onConnectionError(kind: CommandClient.ConnectionErrorKind, message: String) {
        // The wrapper CommandClient never retries on its own. For this
        // resident subscriber a transient failure (e.g. connecting while the
        // core instance is still coming up) must not silence the mirror for
        // the rest of the service lifetime — reconnect after a short delay.
        if (stopped) return
        Log.w(TAG, "log stream error ($kind): $message; retrying in ${RETRY_DELAY_MS}ms")
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            delay(RETRY_DELAY_MS)
            if (!stopped) {
                commandClient.connect()
            }
        }
    }

    override fun appendLogs(message: List<LogEntry>) {
        for (entry in message) {
            Log.println(toAndroidPriority(entry.level), TAG, entry.message)
        }
    }

    companion object {
        private const val TAG = "sing-box"
        private const val RETRY_DELAY_MS = 3_000L
    }
}

// Map libbox log.Level (Panic=0 ... Trace=6) to android.util.Log priorities.
private fun toAndroidPriority(level: Int): Int = when (level) {
    0, 1 -> Log.ERROR // Panic, Fatal
    2 -> Log.ERROR // Error
    3 -> Log.WARN // Warning
    4 -> Log.INFO // Info
    else -> Log.DEBUG // Debug, Trace, Default
}

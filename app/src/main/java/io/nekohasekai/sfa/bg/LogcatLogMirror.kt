package io.nekohasekai.sfa.bg

import android.util.Log
import io.nekohasekai.libbox.LogEntry
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

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

    fun connect() {
        commandClient.connect()
    }

    fun disconnect() {
        commandClient.disconnect()
    }

    override fun appendLogs(message: List<LogEntry>) {
        for (entry in message) {
            Log.println(toAndroidPriority(entry.level), TAG, entry.message)
        }
    }

    companion object {
        private const val TAG = "sing-box"
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

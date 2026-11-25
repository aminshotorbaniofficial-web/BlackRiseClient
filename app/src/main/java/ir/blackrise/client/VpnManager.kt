package ir.blackrise.client

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** Thin wrapper around the official WireGuard GoBackend. */
object VpnManager {
    private var backend: GoBackend? = null

    private val tunnel = object : Tunnel {
        override fun getName(): String = "blackrise"
        override fun onStateChange(newState: Tunnel.State) {}
    }

    fun init(context: Context) {
        if (backend == null) backend = GoBackend(context.applicationContext)
    }

    private fun parse(text: String): Config =
        Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    /** Returns null if valid, otherwise an error message. */
    fun validate(text: String): String? = try {
        parse(text); null
    } catch (e: Exception) {
        e.message ?: e.toString()
    }

    fun isUp(): Boolean = runCatching {
        backend?.getState(tunnel) == Tunnel.State.UP
    }.getOrDefault(false)

    suspend fun connect(configText: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = parse(configText)
            backend!!.setState(tunnel, Tunnel.State.UP, cfg)
            Unit
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            backend!!.setState(tunnel, Tunnel.State.DOWN, null)
            Unit
        }
    }

    /** Pair(rxBytes, txBytes) */
    suspend fun stats(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        runCatching {
            val s = backend!!.getStatistics(tunnel)
            Pair(s.totalRx(), s.totalTx())
        }.getOrNull()
    }
}

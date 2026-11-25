package ir.blackrise.client

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Process
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Picks the right core (sing-box or WireGuard) for a profile and drives it. */
object VpnController {
    private var baseRx = 0L
    private var baseTx = 0L

    private fun isSingBox(text: String) = SingBoxConfig.isLink(text)

    fun protocolName(text: String): String = SingBoxConfig.protocolOf(text)

    fun defaultName(text: String): String =
        (if (isSingBox(text)) SingBoxConfig.nameOf(text) else null) ?: protocolName(text)

    /** null = valid. */
    fun validate(text: String): String? =
        if (isSingBox(text)) {
            runCatching { SingBoxConfig.fromLink(text) }.exceptionOrNull()?.let { it.message ?: it.toString() }
        } else {
            VpnManager.validate(text)
        }

    fun isUp(): Boolean =
        VpnManager.isUp() || SingBoxVpnService.state.value == CoreState.CONNECTED

    suspend fun connect(ctx: Context, text: String): Result<Unit> =
        if (isSingBox(text)) connectSingBox(ctx, text) else VpnManager.connect(text)

    suspend fun disconnect(ctx: Context) {
        if (SingBoxVpnService.state.value != CoreState.IDLE) {
            ctx.startService(
                Intent(ctx, SingBoxVpnService::class.java).setAction(SingBoxVpnService.ACTION_STOP)
            )
            withTimeoutOrNull(8_000) {
                SingBoxVpnService.state.first { it == CoreState.IDLE }
            }
        }
        if (VpnManager.isUp()) VpnManager.disconnect()
    }

    suspend fun stats(): Pair<Long, Long>? =
        if (SingBoxVpnService.state.value == CoreState.CONNECTED) {
            withContext(Dispatchers.IO) {
                val uid = Process.myUid()
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx < 0 || tx < 0) null
                else Pair((rx - baseRx).coerceAtLeast(0), (tx - baseTx).coerceAtLeast(0))
            }
        } else {
            VpnManager.stats()
        }

    private suspend fun connectSingBox(ctx: Context, text: String): Result<Unit> =
        withContext(Dispatchers.Main) {
            runCatching {
                val cfg = SingBoxConfig.fromLink(text)
                val uid = Process.myUid()
                baseRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
                baseTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)

                SingBoxVpnService.lastError = null
                SingBoxVpnService.state.value = CoreState.CONNECTING
                val intent = Intent(ctx, SingBoxVpnService::class.java)
                    .setAction(SingBoxVpnService.ACTION_START)
                    .putExtra(SingBoxVpnService.EXTRA_CONFIG, cfg)
                ContextCompat.startForegroundService(ctx, intent)

                val result = withTimeoutOrNull(30_000) {
                    SingBoxVpnService.state.first { it != CoreState.CONNECTING }
                }
                if (result != CoreState.CONNECTED) {
                    ctx.startService(
                        Intent(ctx, SingBoxVpnService::class.java)
                            .setAction(SingBoxVpnService.ACTION_STOP)
                    )
                    throw Exception(SingBoxVpnService.lastError ?: "connection timed out")
                }
            }
        }
}

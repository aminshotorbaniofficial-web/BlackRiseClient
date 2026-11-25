package ir.blackrise.client

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.NetworkInterface as JNetworkInterface
import io.nekohasekai.libbox.NetworkInterface as LbInterface

enum class CoreState { IDLE, CONNECTING, CONNECTED }

/**
 * VpnService hosting the sing-box core (libbox, pinned to sing-box v1.8.13).
 * Handles VLESS (+Reality) and Hysteria2 via a TUN inbound.
 */
class SingBoxVpnService : VpnService(), PlatformInterface {

    companion object {
        const val ACTION_START = "ir.blackrise.client.START"
        const val ACTION_STOP = "ir.blackrise.client.STOP"
        const val EXTRA_CONFIG = "config"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIF_ID = 1
        private const val TAG = "BlackRiseCore"

        val state = MutableStateFlow(CoreState.IDLE)

        @Volatile
        var lastError: String? = null

        @Volatile
        private var setupDone = false
    }

    private var box: BoxService? = null
    private var monitorCallback: ConnectivityManager.NetworkCallback? = null

    // ---------------------------------------------------------------- lifecycle

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Thread { shutdown() }.start()
            return START_NOT_STICKY
        }
        val cfg = intent?.getStringExtra(EXTRA_CONFIG)
        if (cfg == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (box != null) return START_NOT_STICKY

        startForegroundCompat()
        state.value = CoreState.CONNECTING
        lastError = null
        val service = this@SingBoxVpnService
        Thread {
            try {
                ensureSetup()
                val b = Libbox.newService(cfg, service)
                b.start()
                box = b
                state.value = CoreState.CONNECTED
            } catch (e: Throwable) {
                Log.e(TAG, "start failed", e)
                lastError = e.message ?: e.toString()
                shutdown()
            }
        }.start()
        return START_NOT_STICKY
    }

    @Synchronized
    private fun shutdown() {
        runCatching { box?.close() }
        box = null
        stopMonitor()
        state.value = CoreState.IDLE
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onRevoke() {
        Thread { shutdown() }.start()
    }

    override fun onDestroy() {
        runCatching { box?.close() }
        box = null
        stopMonitor()
        state.value = CoreState.IDLE
        super.onDestroy()
    }

    private fun ensureSetup() {
        if (setupDone) return
        val base = filesDir.absolutePath
        val work = (getExternalFilesDir(null) ?: filesDir).absolutePath
        val temp = cacheDir.absolutePath
        val m = Libbox::class.java.methods.first { it.name == "setup" }
        val p = m.parameterTypes
        val uid = android.os.Process.myUid().toLong()
        val args: Array<Any> = when {
            p.size == 4 && p[2] == String::class.java -> arrayOf(base, work, temp, false)
            p.size == 4 -> arrayOf(base, temp, uid, uid)
            p.size == 2 -> arrayOf(base, temp)
            else -> throw IllegalStateException("Unsupported Libbox.setup signature: ${p.joinToString()}")
        }
        m.invoke(null, *args)
        setupDone = true
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlackRise")
            .setContentText("VPN active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ------------------------------------------------------- PlatformInterface

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!protect(fd)) throw Exception("protect($fd) failed")
    }

    override fun openTun(options: TunOptions): Int {
        if (VpnService.prepare(this) != null) throw Exception("android: missing VPN permission")

        val b = Builder().setSession("BlackRise").setMtu(options.getMTU())

        val v4 = options.getInet4Address()
        while (v4.hasNext()) {
            val a = v4.next()
            b.addAddress(a.address(), a.prefix())
        }
        val v6 = options.getInet6Address()
        while (v6.hasNext()) {
            val a = v6.next()
            b.addAddress(a.address(), a.prefix())
        }

        if (options.getAutoRoute()) {
            b.addDnsServer(options.getDNSServerAddress())

            val r4 = options.getInet4RouteAddress()
            if (r4.hasNext()) {
                while (r4.hasNext()) {
                    val a = r4.next()
                    b.addRoute(a.address(), a.prefix())
                }
            } else if (options.getInet4Address().hasNext()) {
                b.addRoute("0.0.0.0", 0)
            }

            val r6 = options.getInet6RouteAddress()
            if (r6.hasNext()) {
                while (r6.hasNext()) {
                    val a = r6.next()
                    b.addRoute(a.address(), a.prefix())
                }
            } else if (options.getInet6Address().hasNext()) {
                b.addRoute("::", 0)
            }

            val inc = options.getIncludePackage()
            while (inc.hasNext()) b.addAllowedApplication(inc.next())
            val exc = options.getExcludePackage()
            while (exc.hasNext()) b.addDisallowedApplication(exc.next())
        }

        val pfd = b.establish()
            ?: throw Exception("android: VPN not prepared or has been revoked")
        return pfd.detachFd()
    }

    override fun writeLog(message: String) {
        Log.i(TAG, message)
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): Int = throw Exception("per-app routing rules are not supported")

    override fun packageNameByUid(uid: Int): String =
        packageManager.getPackagesForUid(uid)?.firstOrNull()
            ?: throw Exception("package not found for uid $uid")

    override fun uidByPackageName(packageName: String): Int = try {
        packageManager.getApplicationInfo(packageName, 0).uid
    } catch (e: Exception) {
        throw Exception("package not found: $packageName")
    }

    override fun usePlatformDefaultInterfaceMonitor(): Boolean = true

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                notifyDefault(cm, network, listener)
            }

            override fun onLinkPropertiesChanged(network: Network, lp: android.net.LinkProperties) {
                notifyDefault(cm, network, listener)
            }

            override fun onLost(network: Network) {
                listener.updateDefaultInterface("", -1)
            }
        }
        monitorCallback = cb

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        if (Build.VERSION.SDK_INT >= 31) {
            cm.registerBestMatchingNetworkCallback(request, cb, Handler(Looper.getMainLooper()))
        } else {
            cm.requestNetwork(request, cb)
        }
        cm.activeNetwork?.let { notifyDefault(cm, it, listener) }
    }

    private fun notifyDefault(cm: ConnectivityManager, network: Network, l: InterfaceUpdateListener) {
        val name = cm.getLinkProperties(network)?.interfaceName ?: return
        val index = runCatching { JNetworkInterface.getByName(name).index }.getOrNull() ?: return
        l.updateDefaultInterface(name, index)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        stopMonitor()
    }

    private fun stopMonitor() {
        val cb = monitorCallback ?: return
        monitorCallback = null
        runCatching {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(cb)
        }
    }

    override fun usePlatformInterfaceGetter(): Boolean = true

    override fun getInterfaces(): NetworkInterfaceIterator {
        val result = ArrayList<LbInterface>()
        val en = JNetworkInterface.getNetworkInterfaces()
        while (en != null && en.hasMoreElements()) {
            val ni = en.nextElement()
            val item = LbInterface()
            item.setIndex(ni.index)
            item.setMTU(ni.mtu)
            item.setName(ni.name)
            item.setAddresses(
                StrIter(ni.interfaceAddresses.map {
                    (it.address.hostAddress ?: "").substringBefore('%') + "/" + it.networkPrefixLength
                })
            )
            result.add(item)
        }
        return IfIter(result)
    }

    override fun underNetworkExtension(): Boolean = false

    override fun readWIFIState(): WIFIState? = null

    override fun clearDNSCache() {}

    // ----------------------------------------------------------------- helpers

    private class StrIter(private val items: List<String>) : StringIterator {
        private var i = 0
        override fun hasNext(): Boolean = i < items.size
        override fun next(): String = items[i++]
    }

    private class IfIter(private val items: List<LbInterface>) : NetworkInterfaceIterator {
        private var i = 0
        override fun hasNext(): Boolean = i < items.size
        override fun next(): LbInterface = items[i++]
    }
}

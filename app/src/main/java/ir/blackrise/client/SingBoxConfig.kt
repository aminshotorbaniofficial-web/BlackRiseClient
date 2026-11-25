package ir.blackrise.client

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Converts vless:// and hy2:// share links into a full sing-box (v1.8) TUN config. */
object SingBoxConfig {

    fun isLink(text: String): Boolean {
        val s = text.trim().lowercase()
        return s.startsWith("vless://") || s.startsWith("hy2://") ||
            s.startsWith("hysteria2://") || s.startsWith("{")
    }

    fun protocolOf(text: String): String {
        val s = text.trim().lowercase()
        return when {
            s.startsWith("vless://") -> if ("security=reality" in s) "VLESS + Reality" else "VLESS"
            s.startsWith("hy2://") || s.startsWith("hysteria2://") -> "Hysteria2"
            s.startsWith("{") -> "sing-box"
            else -> "WireGuard"
        }
    }

    fun nameOf(text: String): String? = runCatching {
        val s = text.trim().lines().first { it.isNotBlank() }.trim()
        if (s.startsWith("{")) null else Uri.parse(s).fragment?.takeIf { it.isNotBlank() }
            ?: Uri.parse(s).host
    }.getOrNull()

    /** Throws IllegalArgumentException with a readable message on bad input. */
    fun fromLink(raw: String): String {
        val link = raw.trim().lines().firstOrNull { it.isNotBlank() }?.trim()
            ?: throw IllegalArgumentException("empty")
        val lower = link.lowercase()
        val outbound = when {
            lower.startsWith("vless://") -> vless(Uri.parse(link))
            lower.startsWith("hy2://") || lower.startsWith("hysteria2://") -> hy2(Uri.parse(link))
            link.startsWith("{") -> JSONObject(link).put("tag", "proxy")
            else -> throw IllegalArgumentException("unsupported link")
        }
        return wrap(outbound)
    }

    private fun hostOf(u: Uri): String =
        (u.host ?: throw IllegalArgumentException("missing host")).trim('[', ']')

    private fun vless(u: Uri): JSONObject {
        fun q(k: String): String? = u.getQueryParameter(k)?.takeIf { it.isNotBlank() }

        val uuid = u.userInfo?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("missing uuid")
        val o = JSONObject()
            .put("type", "vless")
            .put("tag", "proxy")
            .put("server", hostOf(u))
            .put("server_port", if (u.port > 0) u.port else 443)
            .put("uuid", uuid)
            .put("packet_encoding", "xudp")
        q("flow")?.let { o.put("flow", it) }

        val security = q("security") ?: "none"
        if (security == "tls" || security == "reality") {
            val tls = JSONObject().put("enabled", true)
            (q("sni") ?: q("host"))?.let { tls.put("server_name", it) }
            if (q("allowInsecure") == "1" || q("insecure") == "1") tls.put("insecure", true)
            q("alpn")?.let { tls.put("alpn", JSONArray(it.split(","))) }
            val fp = q("fp") ?: if (security == "reality") "chrome" else null
            fp?.let { tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", it)) }
            if (security == "reality") {
                val pbk = q("pbk") ?: throw IllegalArgumentException("missing Reality public key (pbk)")
                tls.put(
                    "reality",
                    JSONObject().put("enabled", true).put("public_key", pbk).put("short_id", q("sid") ?: "")
                )
            }
            o.put("tls", tls)
        }

        when (val type = q("type") ?: "tcp") {
            "tcp" -> {}
            "ws" -> {
                val t = JSONObject().put("type", "ws").put("path", q("path") ?: "/")
                q("host")?.let { t.put("headers", JSONObject().put("Host", it)) }
                o.put("transport", t)
            }
            "grpc" -> o.put(
                "transport",
                JSONObject().put("type", "grpc").put("service_name", q("serviceName") ?: "")
            )
            "httpupgrade" -> {
                val t = JSONObject().put("type", "httpupgrade").put("path", q("path") ?: "/")
                q("host")?.let { t.put("host", it) }
                o.put("transport", t)
            }
            else -> throw IllegalArgumentException("unsupported transport: $type")
        }
        return o
    }

    private fun hy2(u: Uri): JSONObject {
        fun q(k: String): String? = u.getQueryParameter(k)?.takeIf { it.isNotBlank() }

        val password = u.userInfo?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("missing password")
        val o = JSONObject()
            .put("type", "hysteria2")
            .put("tag", "proxy")
            .put("server", hostOf(u))
            .put("server_port", if (u.port > 0) u.port else 443)
            .put("password", password)
        if (q("obfs") == "salamander") {
            o.put(
                "obfs",
                JSONObject().put("type", "salamander").put("password", q("obfs-password") ?: "")
            )
        }
        val tls = JSONObject().put("enabled", true).put("alpn", JSONArray(listOf("h3")))
        q("sni")?.let { tls.put("server_name", it) }
        if (q("insecure") == "1") tls.put("insecure", true)
        o.put("tls", tls)
        return o
    }

    private fun wrap(outbound: JSONObject): String {
        val cfg = JSONObject(
            """
            {
              "log": { "level": "warn", "timestamp": true },
              "dns": {
                "servers": [
                  { "tag": "remote", "address": "https://1.1.1.1/dns-query", "detour": "proxy" },
                  { "tag": "local", "address": "local" }
                ],
                "rules": [ { "outbound": "any", "server": "local" } ]
              },
              "inbounds": [
                {
                  "type": "tun",
                  "tag": "tun-in",
                  "inet4_address": "172.19.0.1/30",
                  "mtu": 9000,
                  "auto_route": true,
                  "strict_route": true,
                  "stack": "mixed",
                  "sniff": true
                }
              ],
              "outbounds": [
                { "type": "direct", "tag": "direct" },
                { "type": "block", "tag": "block" },
                { "type": "dns", "tag": "dns-out" }
              ],
              "route": {
                "auto_detect_interface": true,
                "final": "proxy",
                "rules": [
                  { "protocol": "dns", "outbound": "dns-out" },
                  { "ip_is_private": true, "outbound": "direct" }
                ]
              }
            }
            """.trimIndent()
        )
        cfg.getJSONArray("outbounds").put(0, outbound)
        // put(0, ...) replaces index 0; re-add direct after it
        cfg.getJSONArray("outbounds").put(JSONObject().put("type", "direct").put("tag", "direct"))
        return cfg.toString()
    }
}

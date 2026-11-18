package com.socks5proxy.app.data

import android.util.Base64
import libXray.LibXray
import com.socks5proxy.app.model.VpnServerProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object VpnSubscriptionImporter {
    private const val MAX_SUBSCRIPTION_BYTES = 12 * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private val supportedPrefixes = listOf(
        "vless://", "vmess://", "trojan://", "ss://", "socks://", "socks5://", "hysteria2://", "hy2://"
    )

    fun import(input: String): List<VpnServerProfile> {
        val source = input.trim()
        require(source.isNotEmpty()) { "Вставьте VPN-ссылку или ссылку на подписку" }

        val rawText = if (source.startsWith("https://", true)) {
            downloadSubscription(source)
        } else if (source.startsWith("http://", true)) {
            throw IllegalArgumentException("Ссылка должна использовать HTTPS")
        } else {
            source
        }

        val decodedText = invokeTextMethod("decodeSubscriptionText", JSONObject().put("text", rawText))
        val content = unwrapBase64IfNeeded(decodedText)
        val lines = content.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val legacyVmess = lines.filter { it.startsWith("vmess://", true) }
            .mapNotNull { parseLegacyVmess(it, source) }
        val remaining = lines.filterNot { it.startsWith("vmess://", true) }
        val parsed = if (remaining.isEmpty()) {
            emptyList()
        } else {
            parseShareLinks(remaining.joinToString("\n"), source)
        }

        val merged = (legacyVmess + parsed).distinctBy { it.id }
        if (merged.isEmpty()) {
            throw IllegalArgumentException("В ссылке не найдено поддерживаемых VPN-серверов")
        }
        return merged
    }

    private fun downloadSubscription(link: String): String {
        var currentUrl = URL(link)
        repeat(MAX_REDIRECTS + 1) { attempt ->
            if (currentUrl.protocol != "https") {
                throw IllegalArgumentException("Перенаправление подписки должно оставаться на HTTPS")
            }
            val connection = currentUrl.openConnection() as? HttpURLConnection
                ?: throw IllegalArgumentException("Не удалось открыть ссылку подписки")
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "text/plain, application/json, */*")
                connection.setRequestProperty("User-Agent", "Socks5Proxy/1.4 Android")
                val status = connection.responseCode
                if (status in 300..399) {
                    if (attempt == MAX_REDIRECTS) throw IllegalArgumentException("Слишком много перенаправлений подписки")
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalArgumentException("Ссылка подписки вернула пустое перенаправление")
                    currentUrl = URL(currentUrl, location)
                } else {
                    if (status !in 200..299) throw IllegalArgumentException("Ссылка подписки вернула HTTP $status")
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { stream ->
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_SUBSCRIPTION_BYTES) {
                                throw IllegalArgumentException("Подписка превышает лимит 12 МБ")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                    return output.toString(StandardCharsets.UTF_8.name()).trim()
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalArgumentException("Не удалось загрузить подписку")
    }

    private fun unwrapBase64IfNeeded(text: String): String {
        val trimmed = text.trim()
        if (looksLikeShareText(trimmed) || trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed

        val compact = trimmed.filterNot(Char::isWhitespace)
        if (compact.length < 16 || compact.any { !(it.isLetterOrDigit() || it in "+/=_-") }) return trimmed
        val decoded = runCatching {
            Base64.decode(compact, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP)
                .toString(StandardCharsets.UTF_8)
                .trim()
        }.getOrNull() ?: return trimmed

        return if (looksLikeShareText(decoded) || decoded.startsWith("{") || decoded.startsWith("[")) decoded else trimmed
    }

    private fun looksLikeShareText(text: String): Boolean =
        supportedPrefixes.any { text.contains(it, ignoreCase = true) }

    private fun parseShareLinks(text: String, source: String): List<VpnServerProfile> {
        val response = invoke("convertShareLinksToXrayJson", JSONObject().put("text", text))
        if (!response.optBoolean("success")) {
            if (text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
                throw IllegalArgumentException("Формат VPN-конфигурации не поддержан")
            }
            val message = response.optString("error").takeIf(String::isNotBlank)
            throw IllegalArgumentException(message ?: "Не удалось разобрать VPN-ссылку")
        }

        val outbounds = response.optJSONObject("data")?.optJSONArray("outbounds") ?: JSONArray()
        return (0 until outbounds.length()).mapNotNull { index ->
            val outbound = outbounds.optJSONObject(index) ?: return@mapNotNull null
            profileFromOutbound(outbound, source)
        }
    }

    private fun parseLegacyVmess(line: String, source: String): VpnServerProfile? = runCatching {
        val encoded = line.substringAfter("vmess://", "").substringBefore('#').trim()
        val json = Base64.decode(encoded, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP)
            .toString(StandardCharsets.UTF_8)
        val legacy = JSONObject(json)
        val host = legacy.optString("add").trim()
        val port = legacy.optString("port").toIntOrNull() ?: legacy.optInt("port")
        val userId = legacy.optString("id").trim()
        if (host.isBlank() || port !in 1..65535 || userId.isBlank()) return null

        val name = legacy.optString("ps").ifBlank { host }
        val network = legacy.optString("net", "tcp").ifBlank { "tcp" }
        val tlsMode = legacy.optString("tls").lowercase()
        val outbound = JSONObject()
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("users", JSONArray().put(
                        JSONObject()
                            .put("id", userId)
                            .put("alterId", legacy.optInt("aid", 0))
                            .put("security", legacy.optString("scy").ifBlank { "auto" })
                    ))
            )))

        val stream = JSONObject().put("network", network)
        if (tlsMode.isNotBlank() && tlsMode != "none") {
            val tls = JSONObject()
                .put("serverName", legacy.optString("sni").ifBlank { legacy.optString("host").ifBlank { host } })
            legacy.optString("fp").takeIf(String::isNotBlank)?.let { tls.put("fingerprint", it) }
            legacy.optString("alpn").takeIf(String::isNotBlank)?.let { value ->
                tls.put("alpn", JSONArray(value.split(',').map(String::trim).filter(String::isNotBlank)))
            }
            stream.put("security", "tls").put("tlsSettings", tls)
        } else {
            stream.put("security", "none")
        }

        val path = legacy.optString("path").ifBlank { "/" }
        val transportHost = legacy.optString("host")
        when (network.lowercase()) {
            "ws" -> {
                val headers = JSONObject()
                if (transportHost.isNotBlank()) headers.put("Host", transportHost)
                stream.put("wsSettings", JSONObject().put("path", path).put("headers", headers))
            }
            "grpc" -> stream.put("grpcSettings", JSONObject().put("serviceName", path.trim('/')))
            "h2", "http" -> {
                val hosts = JSONArray()
                if (transportHost.isNotBlank()) hosts.put(transportHost)
                stream.put("httpSettings", JSONObject().put("path", path).put("host", hosts))
            }
            "tcp" -> {
                if (legacy.optString("type") == "http") {
                    val request = JSONObject()
                    if (transportHost.isNotBlank()) request.put("headers", JSONObject().put("Host", JSONArray().put(transportHost)))
                    stream.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http").put("request", request)))
                }
            }
            "kcp" -> {
                stream.put("kcpSettings", JSONObject().put("header", JSONObject().put("type", legacy.optString("type", "none"))))
            }
            else -> return null
        }
        outbound.put("streamSettings", stream)

        val raw = outbound.toString()
        VpnServerProfile(
            id = stableId(source, raw),
            name = name,
            protocol = "vmess",
            host = host,
            port = port,
            outboundJson = raw,
            sourceUrl = source.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }.orEmpty()
        )
    }.getOrNull()

    private fun profileFromOutbound(outbound: JSONObject, source: String): VpnServerProfile? {
        val protocol = outbound.optString("protocol").ifBlank { return null }
        if (protocol in setOf("freedom", "blackhole", "dns", "loopback")) return null
        val settings = outbound.optJSONObject("settings") ?: JSONObject()
        val endpoint = settings.optJSONArray("vnext")?.optJSONObject(0)
            ?: settings.optJSONArray("servers")?.optJSONObject(0)
            ?: settings.optJSONObject("server")
            ?: return null
        val host = endpoint.optString("address").ifBlank { endpoint.optString("host") }
        val port = endpoint.optInt("port")
        if (host.isBlank() || port !in 1..65535) return null

        val name = outbound.optString("tag").ifBlank { "$protocol · $host" }
        val raw = JSONObject(outbound.toString()).put("tag", "vpn-node").toString()
        return VpnServerProfile(
            id = stableId(source, raw),
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            outboundJson = raw,
            sourceUrl = source.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }.orEmpty()
        )
    }

    private fun invokeTextMethod(method: String, payload: JSONObject): String {
        val response = invoke(method, payload)
        if (!response.optBoolean("success")) {
            throw IllegalArgumentException(response.optString("error").ifBlank { "Не удалось обработать ссылку" })
        }
        return response.optJSONObject("data")?.optString("text") ?: ""
    }

    private fun invoke(method: String, payload: JSONObject): JSONObject {
        val request = JSONObject()
            .put("apiVersion", 3)
            .put("method", method)
            .put("payload", payload)
        return try {
            JSONObject(LibXray.invoke(request.toString()))
        } catch (_: UnsatisfiedLinkError) {
            throw IllegalStateException("Не удалось загрузить VPN-ядро Xray")
        } catch (_: Exception) {
            throw IllegalArgumentException("Не удалось разобрать VPN-ссылку")
        }
    }

    private fun stableId(source: String, outbound: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((source + "\n" + outbound).toByteArray(StandardCharsets.UTF_8))
        return UUID.nameUUIDFromBytes(bytes).toString()
    }
}

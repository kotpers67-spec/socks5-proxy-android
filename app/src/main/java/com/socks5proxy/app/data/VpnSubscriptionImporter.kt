package com.socks5proxy.app.data

import android.util.Base64
import com.socks5proxy.app.model.VpnServerProfile
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
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

        val content = unwrapBase64IfNeeded(rawText).trim()
        val parsedProfiles = mutableListOf<VpnServerProfile>()

        if (content.startsWith("[") || content.startsWith("{")) {
            val jsonProfiles = parseJsonContent(content, source)
            parsedProfiles.addAll(jsonProfiles)
        }

        if (parsedProfiles.isEmpty()) {
            val lines = content.lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()

            val unparsedLines = mutableListOf<String>()

            for (line in lines) {
                val profile = parseSingleLine(line, source)
                if (profile != null) {
                    parsedProfiles.add(profile)
                } else if (looksLikeShareText(line)) {
                    unparsedLines.add(line)
                }
            }

            if (unparsedLines.isNotEmpty()) {
                val fallbackProfiles = runCatching {
                    parseShareLinksWithLibXray(unparsedLines.joinToString("\n"), source)
                }.getOrDefault(emptyList())
                parsedProfiles.addAll(fallbackProfiles)
            }

            if (parsedProfiles.isEmpty()) {
                val fallbackProfiles = runCatching {
                    parseShareLinksWithLibXray(content, source)
                }.getOrDefault(emptyList())
                parsedProfiles.addAll(fallbackProfiles)
            }
        }

        val merged = parsedProfiles.distinctBy { it.id }
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
                connection.setRequestProperty("User-Agent", "v2rayNG/1.8.12 (Linux; Android 14)")
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
        if (looksLikeShareText(trimmed) || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }

        val decodedNatively = tryDecodeBase64(trimmed)
        if (decodedNatively != null && (looksLikeShareText(decodedNatively) || decodedNatively.startsWith("{") || decodedNatively.startsWith("["))) {
            return decodedNatively
        }

        val decodedViaLibXray = runCatching {
            invokeTextMethod("decodeSubscriptionText", JSONObject().put("text", trimmed))
        }.getOrNull()?.trim()

        if (!decodedViaLibXray.isNullOrBlank() && (looksLikeShareText(decodedViaLibXray) || decodedViaLibXray.startsWith("{") || decodedViaLibXray.startsWith("["))) {
            return decodedViaLibXray
        }

        return trimmed
    }

    private fun tryDecodeBase64(input: String): String? {
        val clean = input.filterNot(Char::isWhitespace)
        if (clean.length < 8) return null

        val candidates = listOf(
            clean,
            clean.replace('-', '+').replace('_', '/')
        ).distinct()

        val flags = listOf(
            Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP,
            Base64.DEFAULT,
            Base64.URL_SAFE,
            Base64.NO_WRAP
        )

        for (cand in candidates) {
            val padded = when (cand.length % 4) {
                2 -> "$cand=="
                3 -> "$cand="
                else -> cand
            }
            for (flag in flags) {
                val decoded = runCatching {
                    Base64.decode(padded, flag).toString(StandardCharsets.UTF_8).trim()
                }.getOrNull()
                if (!decoded.isNullOrBlank() && (looksLikeShareText(decoded) || decoded.startsWith("{") || decoded.startsWith("["))) {
                    return decoded
                }
            }
        }
        return null
    }

    private fun looksLikeShareText(text: String): Boolean =
        supportedPrefixes.any { text.contains(it, ignoreCase = true) }

    private fun parseSingleLine(line: String, source: String): VpnServerProfile? {
        val trimmed = line.trim()
        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("vless://") -> parseVless(trimmed, source)
            lower.startsWith("vmess://") -> parseLegacyVmess(trimmed, source)
            lower.startsWith("trojan://") -> parseTrojan(trimmed, source)
            lower.startsWith("ss://") -> parseShadowsocks(trimmed, source)
            lower.startsWith("socks://") || lower.startsWith("socks5://") -> parseSocks(trimmed, source)
            else -> null
        }
    }

    private fun parseJsonContent(content: String, source: String): List<VpnServerProfile> = runCatching {
        val trimmed = content.trim()
        val list = mutableListOf<VpnServerProfile>()
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val profile = parseConfigObject(item, source)
                if (profile != null) list.add(profile)
            }
        } else if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            val profile = parseConfigObject(obj, source)
            if (profile != null) list.add(profile)
        }
        list
    }.getOrDefault(emptyList())

    private fun parseConfigObject(config: JSONObject, source: String): VpnServerProfile? {
        val remarks = config.optString("remarks").ifBlank { config.optString("tag") }
        val outbounds = config.optJSONArray("outbounds")
        if (outbounds != null) {
            var selectedOutbound: JSONObject? = null
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(i) ?: continue
                val proto = outbound.optString("protocol").lowercase()
                if (proto in setOf("vless", "vmess", "trojan", "shadowsocks", "socks")) {
                    if (outbound.optString("tag") == "proxy" || selectedOutbound == null) {
                        selectedOutbound = outbound
                        if (outbound.optString("tag") == "proxy") break
                    }
                }
            }
            if (selectedOutbound != null) {
                val profile = profileFromOutbound(selectedOutbound, source)
                if (profile != null) {
                    return if (remarks.isNotBlank()) profile.copy(name = remarks) else profile
                }
            }
        }
        return profileFromOutbound(config, source)
    }

    private fun decodeName(name: String): String {
        if (name.isBlank()) return ""
        return runCatching {
            URLDecoder.decode(name, StandardCharsets.UTF_8.name())
        }.getOrDefault(name).trim()
    }

    private fun parseHostPort(hostPortStr: String, defaultPort: Int): Pair<String, Int> {
        val raw = hostPortStr.trim()
        return if (raw.startsWith("[")) {
            val host = raw.substringBeforeLast("]:").removePrefix("[").removeSuffix("]")
            val port = raw.substringAfterLast("]:", "").toIntOrNull() ?: defaultPort
            host to port
        } else if (raw.contains(':')) {
            val host = raw.substringBeforeLast(':')
            val port = raw.substringAfterLast(':').toIntOrNull() ?: defaultPort
            host to port
        } else {
            raw to defaultPort
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()
        return queryString.split('&')
            .mapNotNull { param ->
                val parts = param.split('=', limit = 2)
                if (parts.isEmpty()) null
                else {
                    val key = parts[0].trim().lowercase()
                    val value = if (parts.size > 1) {
                        runCatching {
                            URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                        }.getOrDefault(parts[1])
                    } else ""
                    key to value
                }
            }.toMap()
    }

    private fun parseVless(line: String, source: String): VpnServerProfile? = runCatching {
        val name = decodeName(line.substringAfter('#', ""))
        val main = line.removePrefix("vless://").removePrefix("VLESS://").substringBefore('#').trim()
        val beforeQuery = main.substringBefore('?')
        val queryString = if (main.contains('?')) main.substringAfter('?') else ""
        val params = parseQueryParams(queryString)

        val userId = beforeQuery.substringBeforeLast('@').trim()
        val hostPortStr = beforeQuery.substringAfterLast('@').trim()
        val (host, port) = parseHostPort(hostPortStr, 443)
        if (userId.isBlank() || host.isBlank() || port !in 1..65535) return null

        val encryption = params["encryption"]?.ifBlank { "none" } ?: "none"
        val flow = params["flow"].orEmpty()
        val security = params["security"]?.lowercase() ?: "none"
        val network = (params["type"] ?: params["net"] ?: "tcp").lowercase()
        val sni = params["sni"] ?: params["host"].orEmpty()
        val alpn = params["alpn"].orEmpty()
        val fp = params["fp"] ?: "chrome"
        val pbk = params["pbk"].orEmpty()
        val sid = params["sid"].orEmpty()
        val spx = params["spx"].orEmpty()
        val path = params["path"] ?: "/"
        val headerType = params["headertype"] ?: "none"
        val hostHeader = params["host"].orEmpty()
        val serviceName = params["servicename"] ?: params["path"].orEmpty()
        val mode = params["mode"] ?: "gun"

        val userObj = JSONObject()
            .put("id", userId)
            .put("encryption", encryption)
        if (flow.isNotBlank()) {
            userObj.put("flow", flow)
        }

        val outbound = JSONObject()
            .put("tag", "vpn-node")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("users", JSONArray().put(userObj))
            )))

        val stream = JSONObject().put("network", network)
        when (security) {
            "reality" -> {
                val reality = JSONObject()
                    .put("show", false)
                    .put("fingerprint", fp.ifBlank { "chrome" })
                    .put("serverName", sni.ifBlank { host })
                    .put("publicKey", pbk)
                    .put("shortId", sid)
                    .put("spiderX", spx)
                stream.put("security", "reality").put("realitySettings", reality)
            }
            "tls" -> {
                val tls = JSONObject()
                    .put("serverName", sni.ifBlank { host })
                    .put("fingerprint", fp.ifBlank { "chrome" })
                if (alpn.isNotBlank()) {
                    tls.put("alpn", JSONArray(alpn.split(',').map(String::trim).filter(String::isNotEmpty)))
                }
                stream.put("security", "tls").put("tlsSettings", tls)
            }
            else -> {
                stream.put("security", "none")
            }
        }

        when (network) {
            "ws" -> {
                val ws = JSONObject().put("path", path.ifBlank { "/" })
                val hdrHost = hostHeader.ifBlank { sni }
                if (hdrHost.isNotBlank()) {
                    ws.put("headers", JSONObject().put("Host", hdrHost))
                }
                stream.put("wsSettings", ws)
            }
            "grpc" -> {
                val grpc = JSONObject()
                    .put("serviceName", serviceName.trim('/'))
                    .put("multiMode", mode.equals("multi", ignoreCase = true))
                stream.put("grpcSettings", grpc)
            }
            "h2", "http" -> {
                val http = JSONObject().put("path", path.ifBlank { "/" })
                val hdrHost = hostHeader.ifBlank { sni }
                if (hdrHost.isNotBlank()) {
                    http.put("host", JSONArray().put(hdrHost))
                }
                stream.put("httpSettings", http)
            }
            "tcp" -> {
                if (headerType.equals("http", ignoreCase = true)) {
                    val req = JSONObject()
                    val hdrHost = hostHeader.ifBlank { sni }
                    if (hdrHost.isNotBlank()) {
                        req.put("headers", JSONObject().put("Host", JSONArray().put(hdrHost)))
                    }
                    stream.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http").put("request", req)))
                }
            }
            "kcp" -> {
                stream.put("kcpSettings", JSONObject().put("header", JSONObject().put("type", headerType.ifBlank { "none" })))
            }
        }
        outbound.put("streamSettings", stream)

        val raw = outbound.toString()
        VpnServerProfile(
            id = stableId(source, raw),
            name = name.ifBlank { "vless · $host" },
            protocol = "vless",
            host = host,
            port = port,
            outboundJson = raw,
            sourceUrl = cleanSourceUrl(source)
        )
    }.getOrNull()

    private fun parseTrojan(line: String, source: String): VpnServerProfile? = runCatching {
        val name = decodeName(line.substringAfter('#', ""))
        val main = line.removePrefix("trojan://").removePrefix("TROJAN://").substringBefore('#').trim()
        val beforeQuery = main.substringBefore('?')
        val queryString = if (main.contains('?')) main.substringAfter('?') else ""
        val params = parseQueryParams(queryString)

        val password = beforeQuery.substringBeforeLast('@').trim()
        val hostPortStr = beforeQuery.substringAfterLast('@').trim()
        val (host, port) = parseHostPort(hostPortStr, 443)
        if (password.isBlank() || host.isBlank() || port !in 1..65535) return null

        val security = params["security"]?.lowercase() ?: "tls"
        val network = (params["type"] ?: params["net"] ?: "tcp").lowercase()
        val sni = params["sni"] ?: params["host"].orEmpty()
        val alpn = params["alpn"].orEmpty()
        val fp = params["fp"] ?: "chrome"
        val pbk = params["pbk"].orEmpty()
        val sid = params["sid"].orEmpty()
        val spx = params["spx"].orEmpty()
        val path = params["path"] ?: "/"
        val hostHeader = params["host"].orEmpty()
        val serviceName = params["servicename"] ?: params["path"].orEmpty()

        val outbound = JSONObject()
            .put("tag", "vpn-node")
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("password", password)
            )))

        val stream = JSONObject().put("network", network)
        when (security) {
            "reality" -> {
                val reality = JSONObject()
                    .put("show", false)
                    .put("fingerprint", fp.ifBlank { "chrome" })
                    .put("serverName", sni.ifBlank { host })
                    .put("publicKey", pbk)
                    .put("shortId", sid)
                    .put("spiderX", spx)
                stream.put("security", "reality").put("realitySettings", reality)
            }
            "none" -> {
                stream.put("security", "none")
            }
            else -> {
                val tls = JSONObject()
                    .put("serverName", sni.ifBlank { host })
                    .put("fingerprint", fp.ifBlank { "chrome" })
                if (alpn.isNotBlank()) {
                    tls.put("alpn", JSONArray(alpn.split(',').map(String::trim).filter(String::isNotEmpty)))
                }
                stream.put("security", "tls").put("tlsSettings", tls)
            }
        }

        when (network) {
            "ws" -> {
                val ws = JSONObject().put("path", path.ifBlank { "/" })
                val hdrHost = hostHeader.ifBlank { sni }
                if (hdrHost.isNotBlank()) {
                    ws.put("headers", JSONObject().put("Host", hdrHost))
                }
                stream.put("wsSettings", ws)
            }
            "grpc" -> {
                stream.put("grpcSettings", JSONObject().put("serviceName", serviceName.trim('/')))
            }
            "h2", "http" -> {
                val http = JSONObject().put("path", path.ifBlank { "/" })
                val hdrHost = hostHeader.ifBlank { sni }
                if (hdrHost.isNotBlank()) {
                    http.put("host", JSONArray().put(hdrHost))
                }
                stream.put("httpSettings", http)
            }
        }
        outbound.put("streamSettings", stream)

        val raw = outbound.toString()
        VpnServerProfile(
            id = stableId(source, raw),
            name = name.ifBlank { "trojan · $host" },
            protocol = "trojan",
            host = host,
            port = port,
            outboundJson = raw,
            sourceUrl = cleanSourceUrl(source)
        )
    }.getOrNull()

    private fun parseShadowsocks(line: String, source: String): VpnServerProfile? = runCatching {
        val name = decodeName(line.substringAfter('#', ""))
        val main = line.removePrefix("ss://").removePrefix("SS://").substringBefore('#').trim()
        val withoutQuery = main.substringBefore('?')

        var method = ""
        var password = ""
        var host = ""
        var port = 0

        if (withoutQuery.contains('@')) {
            val userPart = withoutQuery.substringBeforeLast('@')
            val hostPortPart = withoutQuery.substringAfterLast('@')
            val decodedUser = runCatching {
                Base64.decode(userPart, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP).toString(StandardCharsets.UTF_8)
            }.getOrNull() ?: userPart
            if (decodedUser.contains(':')) {
                method = decodedUser.substringBefore(':')
                password = decodedUser.substringAfter(':')
            }
            val (h, p) = parseHostPort(hostPortPart, 8388)
            host = h
            port = p
        } else {
            val decoded = runCatching {
                Base64.decode(withoutQuery, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP).toString(StandardCharsets.UTF_8)
            }.getOrNull() ?: withoutQuery
            if (decoded.contains('@')) {
                val userPart = decoded.substringBeforeLast('@')
                val hostPortPart = decoded.substringAfterLast('@')
                if (userPart.contains(':')) {
                    method = userPart.substringBefore(':')
                    password = userPart.substringAfter(':')
                }
                val (h, p) = parseHostPort(hostPortPart, 8388)
                host = h
                port = p
            }
        }

        if (host.isBlank() || port !in 1..65535 || method.isBlank()) return null

        val outbound = JSONObject()
            .put("tag", "vpn-node")
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("method", method)
                    .put("password", password)
                    .put("uot", true)
            )))

        val raw = outbound.toString()
        VpnServerProfile(
            id = stableId(source, raw),
            name = name.ifBlank { "ss · $host" },
            protocol = "shadowsocks",
            host = host,
            port = port,
            outboundJson = raw,
            sourceUrl = cleanSourceUrl(source)
        )
    }.getOrNull()

    private fun parseSocks(line: String, source: String): VpnServerProfile? = runCatching {
        val name = decodeName(line.substringAfter('#', ""))
        val afterScheme = line.substringAfter("://").substringBefore('#').trim()
        val withoutQuery = afterScheme.substringBefore('?')

        var username = ""
        var password = ""
        var host = ""
        var port = 1080

        if (withoutQuery.contains('@')) {
            val userPart = withoutQuery.substringBeforeLast('@')
            val hostPortPart = withoutQuery.substringAfterLast('@')
            val decodedUser = runCatching {
                Base64.decode(userPart, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP).toString(StandardCharsets.UTF_8)
            }.getOrNull() ?: userPart
            if (decodedUser.contains(':')) {
                username = decodedUser.substringBefore(':')
                password = decodedUser.substringAfter(':')
            } else {
                username = decodedUser
            }
            val (h, p) = parseHostPort(hostPortPart, 1080)
            host = h
            port = p
        } else {
            val (h, p) = parseHostPort(withoutQuery, 1080)
            host = h
            port = p
        }

        if (host.isBlank() || port !in 1..65535) return null

        val serverObj = JSONObject()
            .put("address", host)
            .put("port", port)
        if (username.isNotBlank()) {
            serverObj.put("users", JSONArray().put(
                JSONObject()
                    .put("user", username)
                    .put("pass", password)
            ))
        }

        val outbound = JSONObject()
            .put("tag", "vpn-node")
            .put("protocol", "socks")
            .put("settings", JSONObject().put("servers", JSONArray().put(serverObj)))

        val raw = outbound.toString()
        VpnServerProfile(
            id = stableId(source, raw),
            name = name.ifBlank { "socks · $host" },
            protocol = "socks",
            host = host,
            port = port,
            outboundJson = raw,
            sourceUrl = cleanSourceUrl(source)
        )
    }.getOrNull()

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
            .put("tag", "vpn-node")
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
            sourceUrl = cleanSourceUrl(source)
        )
    }.getOrNull()

    private fun cleanSourceUrl(source: String): String {
        return if (source.startsWith("http://", true) || source.startsWith("https://", true)) {
            source
        } else {
            ""
        }
    }

    private fun parseShareLinksWithLibXray(text: String, source: String): List<VpnServerProfile> {
        val base64Payload = Base64.encodeToString(text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val response = invoke("convertShareLinksToXrayJson", JSONObject().put("text", base64Payload))
        if (!response.optBoolean("success")) {
            return emptyList()
        }
        val outbounds = response.optJSONObject("data")?.optJSONArray("outbounds") ?: JSONArray()
        return (0 until outbounds.length()).mapNotNull { index ->
            val outbound = outbounds.optJSONObject(index) ?: return@mapNotNull null
            profileFromOutbound(outbound, source)
        }
    }

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
            sourceUrl = cleanSourceUrl(source)
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

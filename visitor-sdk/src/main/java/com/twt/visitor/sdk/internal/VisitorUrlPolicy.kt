package com.twt.visitor.sdk.internal

import com.twt.visitor.sdk.VisitorConfig
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.io.ByteArrayOutputStream

/** Validates visitor page URLs, encodes parameters and performs same-origin checks. */
internal object VisitorUrlPolicy {
    /** Validates an already-built HTTPS URL without adding or overriding query parameters. */
    fun validateUrl(rawUrl: String): String {
        val uri = parseHttpsUri(rawUrl)
        uri.rawQuery?.let { parseQuery(it, linkedMapOf()) }
        return uri.toString()
    }

    fun buildUrl(config: VisitorConfig): String {
        val uri = parseHttpsUri(config.url)
        val merged = linkedMapOf<String, String>()
        uri.rawQuery?.let { parseQuery(it, merged) }
        config.query.forEach { (key, value) ->
            require(key.isNotBlank()) { "Visitor query key is invalid" }
            merged[key] = value
        }
        if (config.isApp) merged["is_app"] = "1"
        merged["lang"] = config.language.queryValue
        merged["theme"] = config.theme.queryValue
        config.directChatId?.let { id ->
            require(id.isNotBlank()) { "Visitor direct chat id is invalid" }
            merged.remove("chat_id")
            merged["direct"] = "1"
            merged["chatid"] = id
        }
        val query = merged.entries.joinToString("&") { (key, value) ->
            "${encodeQueryComponent(key)}=${encodeQueryComponent(value)}"
        }.ifEmpty { null }

        return buildString {
            append(uri.scheme)
            append("://")
            append(uri.rawAuthority)
            append(uri.rawPath.ifEmpty { "/" })
            query?.let {
                append('?')
                append(it)
            }
            uri.rawFragment?.let {
                append('#')
                append(it)
            }
        }
    }

    fun isSameOrigin(baseUrl: String, candidateUrl: String): Boolean {
        return runCatching {
            val base = parseHttpsUri(baseUrl)
            val candidate = parseHttpsUri(candidateUrl)
            base.scheme.equals(candidate.scheme, ignoreCase = true) &&
                base.host.equals(candidate.host, ignoreCase = true) &&
                effectivePort(base) == effectivePort(candidate)
        }.getOrDefault(false)
    }

    private fun parseHttpsUri(value: String): URI {
        val uri = try {
            URI(value)
        } catch (error: Exception) {
            throw IllegalArgumentException("Visitor URL is invalid", error)
        }

        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Visitor URL must use HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "Visitor URL must include a host" }
        require(uri.rawUserInfo == null) { "Visitor URL must not include user information" }
        require(uri.port == -1 || uri.port in 1..65535) { "Visitor URL port is invalid" }
        return uri
    }

    private fun parseQuery(raw: String, output: MutableMap<String, String>) {
        if (raw.isEmpty()) return
        raw.split('&').forEach { part ->
            val separator = part.indexOf('=')
            val rawKey = if (separator < 0) part else part.substring(0, separator)
            val rawValue = if (separator < 0) "" else part.substring(separator + 1)
            val key = percentDecode(rawKey)
            require(key.isNotBlank()) { "Visitor query key is invalid" }
            output[key] = percentDecode(rawValue)
        }
    }

    private fun percentDecode(value: String): String {
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                require(index + 2 < value.length) { "Visitor query encoding is invalid" }
                val high = value[index + 1].digitToIntOrNull(16)
                val low = value[index + 2].digitToIntOrNull(16)
                require(high != null && low != null) { "Visitor query encoding is invalid" }
                bytes.write((high shl 4) or low)
                index += 3
            } else {
                val codePoint = value.codePointAt(index)
                bytes.write(String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8))
                index += Character.charCount(codePoint)
            }
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString()
        } catch (error: Exception) {
            throw IllegalArgumentException("Visitor query encoding is invalid", error)
        }
    }

    private fun effectivePort(uri: URI): Int = if (uri.port == -1) 443 else uri.port

    private fun encodeQueryComponent(value: String): String {
        return buildString {
            value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
                val code = byte.toInt() and 0xff
                if (
                    code in 'a'.code..'z'.code ||
                    code in 'A'.code..'Z'.code ||
                    code in '0'.code..'9'.code ||
                    code == '-'.code ||
                    code == '.'.code ||
                    code == '_'.code ||
                    code == '~'.code
                ) {
                    append(code.toChar())
                } else {
                    append('%')
                    append(HEX[code ushr 4])
                    append(HEX[code and 0x0f])
                }
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}

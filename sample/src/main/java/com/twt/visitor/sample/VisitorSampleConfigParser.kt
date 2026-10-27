package com.twt.visitor.sample

import com.twt.visitor.sdk.VisitorConfig
import com.twt.visitor.sdk.VisitorLanguage
import com.twt.visitor.sdk.VisitorTheme
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Values entered in the sample's custom visitor configuration form. */
data class VisitorSampleFormValues(
    val url: String,
    val query: String = "",
    val language: VisitorLanguage = VisitorLanguage.ZH_CN,
    val theme: VisitorTheme = VisitorTheme.SYSTEM,
    val directChatId: String = "",
)

/**
 * Converts editable sample values into the SDK's typed configuration.
 *
 * The sample deliberately parses the free-form query before starting the SDK so
 * malformed URLs or percent escapes can be shown in the form instead of
 * crashing the host activity. The SDK remains responsible for the final URL
 * merge and encoding.
 */
object VisitorSampleConfigParser {
    fun parse(values: VisitorSampleFormValues): VisitorConfig {
        val url = values.url.trim()
        require(url.isNotEmpty()) { "Enter a visitor HTTPS URL" }

        val uri = parseHttpsUri(url)
        // Validate query already present in the URL as well. It is intentionally
        // not copied into VisitorConfig.query; the SDK merges it in place.
        uri.rawQuery?.let { parseQuery(it, "URL query parameters") }

        val query = parseQuery(values.query.trim(), "custom query parameters")
        val directChatId = values.directChatId.trim().ifEmpty { null }

        return VisitorConfig(
            url = url,
            query = query,
            language = values.language,
            theme = values.theme,
            directChatId = directChatId,
        )
    }

    private fun parseHttpsUri(rawUrl: String): URI {
        val uri = try {
            URI(rawUrl)
        } catch (error: Exception) {
            throw IllegalArgumentException("Visitor URL format is invalid", error)
        }

        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Visitor URL must use HTTPS"
        }
        require(!uri.host.isNullOrBlank()) {
            "Visitor URL must include a host"
        }
        require(uri.rawUserInfo == null) {
            "Visitor URL must not include a username or password"
        }
        require(uri.port == -1 || uri.port in 1..65535) {
            "Visitor URL port is invalid"
        }
        return uri
    }

    private fun parseQuery(raw: String, fieldName: String): LinkedHashMap<String, String> {
        val output = linkedMapOf<String, String>()
        if (raw.isEmpty()) return output

        var start = 0
        while (true) {
            val ampersand = raw.indexOf('&', start)
            val end = if (ampersand >= 0) ampersand else raw.length
            val segment = raw.substring(start, end)
            require(segment.isNotEmpty()) { "$fieldName must not contain an empty parameter" }

            val separator = segment.indexOf('=')
            val rawKey = if (separator < 0) segment else segment.substring(0, separator)
            val rawValue = if (separator < 0) "" else segment.substring(separator + 1)
            val key = percentDecode(rawKey, fieldName)
            require(key.trim().isNotEmpty()) { "$fieldName must not contain an empty key" }
            output[key] = percentDecode(rawValue, fieldName)

            if (ampersand < 0) break
            start = ampersand + 1
        }
        return output
    }

    private fun percentDecode(value: String, fieldName: String): String {
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                require(index + 2 < value.length) { "$fieldName contains invalid percent encoding" }
                val high = value[index + 1].digitToIntOrNull(16)
                val low = value[index + 2].digitToIntOrNull(16)
                require(high != null && low != null) { "$fieldName contains invalid percent encoding" }
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
            throw IllegalArgumentException("$fieldName contains invalid UTF-8 encoding", error)
        }
    }
}

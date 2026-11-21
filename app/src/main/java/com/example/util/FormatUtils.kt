package com.example.util

import android.net.Uri
import android.webkit.MimeTypeMap
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "نامشخص"
        if (bytes == 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB/s", mb)
            else -> String.format(Locale.US, "%.1f KB/s", kb)
        }
    }

    fun formatSpeedLimit(kbps: Int): String {
        if (kbps <= 0) return "نامحدود"
        return if (kbps >= 1024) {
            String.format(Locale.US, "%.1f MB/s", kbps / 1024.0)
        } else {
            "$kbps KB/s"
        }
    }

    fun formatEta(seconds: Long): String {
        if (seconds < 0L) return "--:--"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatPercent(fraction: Float): String {
        val pct = (fraction.coerceIn(0f, 1f) * 100f).roundToInt()
        return "$pct%"
    }

    fun formatTimeHm(hour: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "—"
        val sdf = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US)
        return sdf.format(Date(millis))
    }

    fun isValidHttpUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US)
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    fun extractHost(url: String): String {
        return runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
    }

    fun extractFileNameFromUrl(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null
    ): String {
        // 1. Try Content-Disposition filename* or filename
        if (!contentDisposition.isNullOrBlank()) {
            val utf8Match = Regex("filename\\*\\s*=\\s*UTF-8''([^;\\r\\n]+)", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
            if (utf8Match != null) {
                val decoded = runCatching {
                    URLDecoder.decode(utf8Match.groupValues[1].trim().trim('"'), "UTF-8")
                }.getOrNull()
                if (!decoded.isNullOrBlank()) return sanitizeFileName(decoded)
            }
            val stdMatch = Regex("filename\\s*=\\s*\"?([^\";\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
            if (stdMatch != null) {
                val raw = stdMatch.groupValues[1].trim()
                if (raw.isNotBlank()) return sanitizeFileName(raw)
            }
        }

        // 2. Extract from URL path
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val lastSegment = uri?.lastPathSegment?.substringBefore('?')?.substringBefore('#').orEmpty()
        var candidate = runCatching { URLDecoder.decode(lastSegment, "UTF-8") }.getOrDefault(lastSegment)
        candidate = sanitizeFileName(candidate)

        if (candidate.isBlank() || candidate == "/") {
            candidate = "download_${System.currentTimeMillis() % 100000}"
        }

        // 3. Append extension from MIME type if missing
        if (!candidate.contains('.') && !mimeType.isNullOrBlank()) {
            val cleanMime = mimeType.substringBefore(';').trim().lowercase(Locale.US)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(cleanMime)
            if (!ext.isNullOrBlank()) {
                candidate = "$candidate.$ext"
            }
        }

        return candidate
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.')
        return cleaned.take(160).ifBlank { "file_${System.currentTimeMillis() % 100000}" }
    }

    fun getFileExtension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0 || dot == fileName.length - 1) return ""
        return fileName.substring(dot + 1).lowercase(Locale.US)
    }

    fun guessMimeType(fileName: String): String {
        val ext = getFileExtension(fileName)
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}

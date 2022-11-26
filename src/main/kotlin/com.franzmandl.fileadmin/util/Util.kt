package com.franzmandl.fileadmin.util

import org.apache.tika.Tika
import org.springframework.http.MediaType
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*


object Util {
    private val random = SecureRandom()
    private val tika = Tika()
    val yyyy_MM_dd_HH_mm_ss_SSS_format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun encodePath(path: String?): String {
        return URLEncoder.encode(path, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    }

    fun createSecureRandomString(bytesCount: Int): String {
        val bytes = ByteArray(bytesCount)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun getMimeType(path: Path, onFailure: String): String {
        return try {
            val contentType = Files.probeContentType(path)
            contentType ?: tika.detect(path)
        } catch (e: IOException) {
            onFailure
        }
    }

    fun getContentType(path: Path): String {
        var contentType = getMimeType(path, MediaType.TEXT_PLAIN_VALUE)
        if (contentType.startsWith("text/")) {
            contentType += "; charset=utf-8"
        }
        return contentType
    }

    fun createPeriod(years: Int, months: Int, days: Int, weeks: Int): Period {
        return Period.of(years, months, days + Math.multiplyExact(weeks, 7))
    }

    fun convertToZonedDateTime(milliseconds: Long): ZonedDateTime {
        return Instant.ofEpochMilli(milliseconds).atZone(ZoneId.systemDefault())
    }

    fun convertToLocalDate(milliseconds: Long): LocalDate {
        return convertToZonedDateTime(milliseconds).toLocalDate()
    }

    fun convertToLocalDateTime(milliseconds: Long): LocalDateTime {
        return convertToZonedDateTime(milliseconds).toLocalDateTime()
    }

    fun convertToLocalDate(date: Date): LocalDate {
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
}

package com.franzmandl.fileadmin.common

import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.HasChildren
import com.franzmandl.fileadmin.vfs.Inode1
import org.apache.tika.Tika
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.FileTime
import java.security.SecureRandom
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.isDirectory

object CommonUtil {
    val contentTypeTextPlainUtf8 = appendCharset(MediaType.TEXT_PLAIN_VALUE)

    /** Inclusive dot. */
    const val maxFileEndingLength = 5
    private val random = SecureRandom()
    private val tika = Tika()
    private const val wildcard = "*"
    private val wildcardGlob = createGlobHelper(wildcard)
    val yyyy_MM_dd_HH_mm_ss_SSS_format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun appendCharset(mimeType: String): String =
        if (mimeType.startsWith("text/")) "$mimeType;charset=utf-8" else mimeType

    fun setContentTypeAsString(responseHeaders: HttpHeaders, contentType: String) {
        // Sets the same as responseHeaders.contentType
        responseHeaders["Content-Type"] = contentType
    }

    fun createContentTypePlainUtf8HttpHeaders(): HttpHeaders {
        val httpHeaders = HttpHeaders()
        setContentTypeAsString(httpHeaders, contentTypeTextPlainUtf8)
        return httpHeaders
    }

    private fun createGlobHelper(pattern: String): PathMatcher =
        FileSystems.getDefault().getPathMatcher("glob:$pattern")

    fun createGlob(pattern: String): PathMatcher =
        if (pattern == wildcard) wildcardGlob else createGlobHelper(pattern)

    fun createSecureRandomString(bytesCount: Int): String {
        val bytes = ByteArray(bytesCount)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun evaluateGlobs(globs: List<PathMatcher>, localPath: Path): Boolean {
        var result = false
        for (glob in globs) {
            result = result || glob === wildcardGlob || glob.matches(localPath)
        }
        return result
    }

    const val mimeTypeDirectory = "inode/directory"

    fun getMimeType(localPath: Path): String =
        mimeTypeDirectory.takeIf { localPath.isDirectory() } ?: Files.probeContentType(localPath) ?: tika.detect(localPath)

    fun createPeriod(years: Int, months: Int, days: Int, weeks: Int): Period =
        Period.of(years, months, days + Math.multiplyExact(weeks, 7))

    fun convertToZonedDateTime(milliseconds: Long): ZonedDateTime =
        Instant.ofEpochMilli(milliseconds).atZone(ZoneId.systemDefault())

    fun convertToLocalDate(milliseconds: Long): LocalDate =
        LocalDate.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneId.systemDefault())

    fun convertToLocalDateTime(milliseconds: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneId.systemDefault())

    fun convertToLocalDate(date: Date): LocalDate =
        LocalDate.ofInstant(date.toInstant(), ZoneId.systemDefault())

    fun convertToLocalDate(fileTime: FileTime): LocalDate =
        LocalDate.ofInstant(fileTime.toInstant(), ZoneId.systemDefault())

    private fun parseInt(string: String): Int? = try {
        string.toInt()
    } catch (e: NumberFormatException) {
        null
    }

    fun parseDate(yearString: String, monthString: String?, dayString: String?): LocalDate? {
        val year = parseInt(yearString) ?: return null
        val month = if (monthString == null) 1 else (parseInt(monthString) ?: return null).coerceAtLeast(1).coerceAtMost(12)
        val day = if (dayString == null) 1 else (parseInt(dayString) ?: return null).coerceAtLeast(1).coerceAtMost(31)
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
        }
    }

    val dateRegex = Regex("""^([0-9]{4})(?:-([0-9]{2})(?:-([0-9]{2}))?)?(?:$|[A-Z+. ])""")

    fun parseDatePairs(string: String): Pair<String, Pair<String, String?>?>? {
        val groups = dateRegex.find(string)?.groups ?: return null
        val year = groups[1]?.value ?: return null
        val month = groups[2]?.value
        val day = groups[3]?.value
        return year to (if (month != null) month to day else null)
    }

    fun parseDate(string: String): LocalDate? {
        val groups = dateRegex.find(string)?.groups ?: return null
        return parseDate(groups[1]?.value ?: return null, groups[2]?.value, groups[3]?.value)
    }

    fun createJsonHttpHeaders(): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

    inline fun <reified T> createJsonResponseEntity(value: T): ResponseEntity<String> =
        ResponseEntity(JsonFormat.encodeToString(value), createJsonHttpHeaders(), HttpStatus.OK)

    fun <T> popOrNull(list: LinkedList<T>): T? =
        if (list.isEmpty()) null else list.pop()

    fun takeStringIf(condition: Boolean, string: String): String =
        if (condition) string else ""

    fun <E> getReversedSequenceOf(list: List<E>): Sequence<E> =
        sequence {
            for (index in list.lastIndex downTo 0) {
                yield(list[index])
            }
        }

    fun getSequenceOfChildren(ctx: RequestCtx, container: HasChildren): Sequence<Inode1<*>> =
        sequence {
            for (child in container.children) {
                yield(ctx.getInode(child))
            }
        }

    fun <T> getSequenceOfParts(parts: List<T>, firstIsEmpty: Boolean): Sequence<List<T>> =
        sequence {
            val builder = ArrayList<T>(parts.size)
            if (firstIsEmpty) {
                yield(listOf())
            }
            for (part in parts) {
                builder += part
                yield(builder.toList())
            }
        }

    fun <T> appendNullable(a: Set<T>, b: Iterable<T>?): Set<T> =
        if (b != null) a + b else a

    fun <T> appendNullable(a: Sequence<T>, b: Sequence<T>?): Sequence<T> =
        if (b != null) a + b else a

    fun <T> prependNullable(a: Sequence<T>?, b: Sequence<T>): Sequence<T> =
        if (a != null) a + b else b

    private fun getSafeIndex(index: Int, size: Int): Int? =
        when (index) {
            in -size..-1 -> index.mod(size)
            in 0..<size -> index
            else -> null
        }

    fun <T> getSafe(list: List<T>, index: Int): T? =
        getSafeIndex(index, list.size)?.let { list[it] }

    fun replaceChecked(regex: Regex, input: CharSequence, replacement: String): String? =
        regex.replaceFirst(input, replacement).takeIf { it !== input }

    fun <T> concatenate(vararg lists: List<T>): MutableList<T> {
        val size = lists.fold(0) { accumulator, list -> accumulator + list.size }
        val result = ArrayList<T>(size)
        for (list in lists) {
            result += list
        }
        return result
    }

    fun noop(@Suppress("UNUSED_PARAMETER") vararg ignore: Any) = Unit

    fun stringToUri(string: String): String =
        string.split("/").joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()).replace("+", "%20") }
}
package com.franzmandl.fileadmin.common

import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.InodeWithoutConfig
import com.franzmandl.fileadmin.vfs.PathFinder
import org.apache.tika.Tika
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.FileTime
import java.security.SecureRandom
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

object CommonUtil {
    val contentTypeTextPlainUtf8 = appendCharset(MediaType.TEXT_PLAIN_VALUE)
    private val random = SecureRandom()
    private val tika = Tika()
    private const val wildcard = "*"
    private val wildcardGlob = createGlob(wildcard)
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

    fun createGlob(pattern: String): PathMatcher =
        if(pattern == wildcard) wildcardGlob else FileSystems.getDefault().getPathMatcher("glob:$pattern")

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

    fun getMimeType(localPath: Path): String =
        Files.probeContentType(localPath) ?: tika.detect(localPath)

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

    private fun parseDate(yearString: String, monthString: String, dayString: String): LocalDate? {
        val year = parseInt(yearString) ?: return null
        val month = (parseInt(monthString) ?: return null).coerceAtLeast(1).coerceAtMost(12)
        val day = (parseInt(dayString) ?: return null).coerceAtLeast(1).coerceAtMost(31)
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
        }
    }

    private val dateRegex = Regex("^([0-9]{4})(?:-([0-9]{2})(?:-([0-9]{2}))?)?(?:$|[^0-9])")

    fun parseDate(string: String): LocalDate? {
        val groups = dateRegex.find(string)?.groups ?: return null
        return parseDate(groups[1]?.value ?: return null, groups[2]?.value ?: "0", groups[3]?.value ?: "0")
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

    fun getSequenceOfChildren(ctx: RequestCtx, directory: InodeWithoutConfig): Sequence<Inode> =
        sequence {
            for (child in directory.children) {
                yield(ctx.getInode(child))
            }
        }

    fun getSequenceOfDescendants(ctx: PathFinder.Ctx, directory: InodeWithoutConfig, minDepth: Int, maxDepth: Int, pruneNames: Set<String>, onError: (String) -> Unit): Sequence<Inode> =
        sequence {
            for (child in directory.children) {
                if (maxDepth >= 0 && child.name !in pruneNames) {
                    val inode = ctx.createPathFinder(child).find()
                    if (minDepth <= 0) {
                        yield(inode)
                    }
                    try {
                        if (inode.contentOperation.canDirectoryGet) {
                            yieldAll(getSequenceOfDescendants(ctx, inode, minDepth - 1, maxDepth - 1, pruneNames, onError))
                        }
                    } catch (e: HttpException) {
                        onError(e.message)
                    }
                }
            }
        }

    fun <T> getPartsList(parts: List<T>, firstIsEmpty: Boolean): List<List<T>> {
        val builder = LinkedList<T>()
        val result = LinkedList<List<T>>()
        if (firstIsEmpty) {
            result.add(listOf())
        }
        for (part in parts) {
            builder.add(part)
            result.add(builder.toList())
        }
        return result
    }

    fun noop(@Suppress("UNUSED_PARAMETER") vararg any: Any) {}

    fun <T>nextOrNull(iterator: Iterator<T>): T? =
        if (iterator.hasNext()) iterator.next() else null
}
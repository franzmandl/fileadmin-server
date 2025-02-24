package com.franzmandl.fileadmin.dto

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.dto.config.Mapper
import com.franzmandl.fileadmin.filter.FilterCtx
import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.filter.SystemTags
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.NativeFileSystem
import com.franzmandl.fileadmin.vfs.RootFileSystem
import com.franzmandl.fileadmin.vfs.SafePath
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable

@Serializable
@Component
final class ApplicationCtx(
    @Autowired val bookmarks: BookmarksProperties,
    @Autowired val config: ConfigProperties,
    @Autowired val filter: FilterProperties,
    @Autowired val jail: JailProperties,
    @Autowired val security: SecurityProperties,
    @Autowired val share: ShareProperties,
    @Autowired val system: SystemProperties,
    @Autowired val task: TaskProperties,
    @Autowired val web: WebProperties,
) {
    @Serializable
    @Component
    final class BookmarksProperties(
        @Value("\${application.bookmarks.path}") private val pathString: String,
    ) {
        @Transient
        val path: Path? = createCheckedPathIfNotEmpty(pathString)
    }

    @Serializable
    @Component
    final class ConfigProperties(
        @Value("\${application.config.name-cursor-position.default:}") val nameCursorPosition: Int?,
        @Value("\${application.config.new-inode-template.is-file.default}") private val newInodeTemplateIsFile: Boolean,
        @Value("\${application.config.new-inode-template.name.default}") private val newInodeTemplateName: String,
        @Value("\${application.config.file-name}") val fileName: String,
        @Value("\${application.config.max-import-level}") val maxImportLevel: Int,
    ) {
        val newInodeTemplate = NewInode(newInodeTemplateIsFile, newInodeTemplateName)
    }

    @Serializable
    @Component
    final class FilterProperties(
        @Value("\${application.filter.tag.inode-tag.suggest-minimum-length.default}") val inodeTagOperandSuggestMinimumLength: Int,
        @Value("\${application.filter.tag.input-tag.name.prefix.default}") val inputTagPrefix: String,
        @Value("\${application.filter.tag.input-tag.name.suffix.default}") val inputTagSuffix: String,
        @Value("\${application.filter.tag.operand.suggest-minimum-length.default}") val operandSuggestMinimumLength: Int,
        @Value("\${application.filter.tag.suggest-minimum-length.default}") private val suggestMinimumLength: Int,
        @Autowired private val originalSystemTags: SystemTagsProperties,
    ) {
        val systemTags = SystemTags.map(originalSystemTags) { originalName ->
            val name = FilterFileSystem.trimPrefix(originalName, ErrorHandler.error)
            if (!FilterFileSystem.isValidName(name)) {
                throw HttpException.badRequest("""Illegal tag name: "$name" matches anti pattern.""")
            }
            name
        }

        fun coerceSuggestMinimumLength(name: String, suggestMinimumLength: Int?): Int =
            name.length.coerceAtMost(suggestMinimumLength ?: this.suggestMinimumLength)

        @Serializable
        @Component
        final class SystemTagsProperties(
            @Value("\${application.filter.tag.system.directory.default}") override val directory: String,
            @Value("\${application.filter.tag.system.empty-content.default}") override val emptyContent: String,
            @Value("\${application.filter.tag.system.empty-name.default}") override val emptyName: String,
            @Value("\${application.filter.tag.system.empty-parent-path.default}") override val emptyParentPath: String,
            @Value("\${application.filter.tag.system.file.default}") override val file: String,
            @Value("\${application.filter.tag.system.input.default}") override val input: String,
            @Value("\${application.filter.tag.system.lost-and-found.default}") override val lostAndFound: String,
            @Value("\${application.filter.tag.system.prune.default}") override val prune: String,
            @Value("\${application.filter.tag.system.task.default}") override val task: String,
            @Value("\${application.filter.tag.system.task-done.default}") override val taskDone: String,
            @Value("\${application.filter.tag.system.unknown.default}") override val unknown: String,
        ) : SystemTags<String>
    }

    object Header {
        // I recommend using small letters.
        const val lastModifiedMilliseconds = "x-last-modified-milliseconds"
    }

    @Serializable
    @Component
    final class JailProperties(
        @Value("\${application.jail.path}") private val pathString: String,
    ) {
        @Transient
        val fileSystem: RootFileSystem = NativeFileSystem(createCheckedPath(pathString))
    }

    object RequestMappingPaths {
        const val authenticated = "/service"
        const val bookmarks = "/bookmarks"
        const val bookmarksPrivate = "$bookmarks/private"
        const val error = "/error"
        const val local = "/local"
        const val login = "/login"
        const val readyz = "/readyz"
        const val web = "/web"

        object Authenticated {
            const val command = "$authenticated/command"
            const val directory = "$authenticated/directory"
            const val file = "$authenticated/file"
            const val fileConvertImageToImage = "$file/convert/image/to/image"
            const val fileConvertPdfToImage = "$file/convert/pdf/to/image"
            const val fileStream = "$file/stream"
            const val inode = "$authenticated/inode"
            const val logout = "$authenticated/logout"
            const val scanItems = "$authenticated/scanItems"
            const val suggestion = "$authenticated/suggestion"
        }

        object Local {
            const val ping = "$local/ping"
            const val task = "$local/task"
        }
    }

    @Serializable
    @Component
    final class SecurityProperties(
        @Value("\${application.security.allowed-origins}") private val allowedOriginsString: String,
        @Value("\${application.security.cors-enabled}") val corsEnabled: Boolean,
        @Value("\${application.security.key}") val key: String,
        @Value("\${application.security.max-login-attempts}") val maxLoginAttempts: Int,
        @Value("\${application.security.password-hash}") val passwordHash: String,
        @Value("\${application.security.token-cookie-name}") val tokenCookieName: String,
        @Value("\${application.security.token-max-age-seconds}") val tokenMaxAgeSeconds: Int,
        @Value("\${server.servlet.session.cookie.secure}") val useOnlySecureCookies: Boolean,
        @Value("\${application.security.usernames}") private val usernamesString: String,
    ) {
        val allowedOrigins = allowedOriginsString.split(",").toTypedArray()
        val usernames = usernamesString.split(",").toTypedArray()
    }

    @Serializable
    @Component
    final class ShareProperties(
        @Value("\${application.share.binary.path}") val binaryPath: String,
    )

    @Serializable
    @Component
    final class SystemProperties(
        @Value("\${application.system.max-iteration-count}") val maxIterationCount: Int,
        @Value("\${application.system.directory-name}") val directoryName: String,
    )

    @Serializable
    @Component
    final class TaskProperties(
        @Value("\${application.task.binaries.path}") private val binariesPathString: String,
        @Value("\${application.jail.path}") private val jailPathString: String,
        @Value("\${application.task.mail.path}") private val mailPathString: String,
        @Value("\${application.task.done-status-name}") val doneStatusName: String,
    ) {
        @Transient
        val binariesFileSystem: RootFileSystem? = createCheckedPathIfNotEmpty(binariesPathString)?.let { NativeFileSystem(it) }

        @Transient
        val mailPath: SafePath? = createCheckedPathIfNotEmpty(mailPathString)?.let {
            if (!mailPathString.startsWith(jailPathString)) {
                throw HttpException.badRequest("task.mail.path must be inside jail.")
            }
            SafePath(mailPathString.substring(jailPathString.length, mailPathString.length))
        }
    }

    @Serializable
    @Component
    final class WebProperties(
        @Value("\${application.web.path}") private val pathString: String,
    ) {
        @Transient
        val path: Path? = createCheckedPathIfNotEmpty(pathString)
    }

    fun createRequestCtx(now: LocalDate?): RequestCtx =
        RequestCtx(this, now ?: LocalDate.now())

    @Transient
    private val nextLogId = AtomicLong(System.currentTimeMillis())

    fun incrementLogId(): String =
        nextLogId.getAndIncrement().toString()

    @Transient
    private val filterFileSystemCache = ConcurrentHashMap<SafePath, FilterFileSystem>()

    @Transient
    private val filterFileSystemNotCacheable = ConcurrentHashMap.newKeySet<SafePath>()

    @Scheduled(cron = "\${application.cache.clear.cron-expression}")
    fun clearCache() {
        filterFileSystemCache.clear()
        filterFileSystemNotCacheable.clear()
    }

    fun cacheFilterFileSystem(
        key: SafePath,
        lastModified: FileTime?,
        factory: (Boolean) -> FilterCtx,
    ): FilterFileSystem {
        if (lastModified == null) {
            filterFileSystemCache.remove(key)
            return FilterFileSystem(null, factory(filterFileSystemNotCacheable.add(key)))
        }
        val system = filterFileSystemCache[key]
        filterFileSystemNotCacheable.remove(key)
        return when {
            system == null -> FilterFileSystem(lastModified, factory(true)).also { filterFileSystemCache[key] = it }
            system.time != lastModified -> system.setLocked(lastModified, factory(true))
            else -> system
        }
    }

    @Transient
    val mapper = Mapper(config.fileName)

    companion object {
        private fun createCheckedPathIfNotEmpty(string: String): Path? =
            if (string.isNotEmpty()) createCheckedPath(string) else null

        private fun createCheckedPath(string: String): Path {
            val path = Path.of(string)
            if (!path.isDirectory()) {
                throw HttpException.badRequest("""Path "$path": Not a directory.""")
            }
            if (!path.isReadable()) {
                throw HttpException.badRequest("""Path "$path": Insufficient permission.""")
            }
            return path
        }
    }
}
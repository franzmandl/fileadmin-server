package com.franzmandl.fileadmin.model

import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.filter.FilterCtx
import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable

@Serializable
@Component
class ApplicationCtx(
    @Value("\${application.config.file-name}") val configFileName: String,
    @Value("\${application.config.max-import-level}") val configMaxImportLevel: Int,
    @Value("\${application.max-iteration-count}") val maxIterationCount: Int,
    @Value("\${application.system.directory-name}") val systemDirectoryName: String,
    @Autowired val binaries: Binaries,
    @Autowired val default: Default,
    @Autowired val paths: Paths,
    @Autowired val security: Security,
) {
    @Serializable
    @Component
    class Default(
        @Value("\${application.default.name-cursor-position:}") final val nameCursorPosition: Int?,
        @Value("\${application.default.new-inode-template.is-file}") private val newInodeTemplateIsFile: Boolean,
        @Value("\${application.default.new-inode-template.name}") private val newInodeTemplateName: String,
        @Value("\${application.default.tag.directory}") final val tagDirectory: String,
        @Value("\${application.default.tag.file}") final val tagFile: String,
        @Value("\${application.default.tag.input}") final val tagInput: String,
        @Value("\${application.default.tag.lost-and-found}") final val tagLostAndFound: String,
        @Value("\${application.default.tag.unknown}") final val tagUnknown: String,
    ) {
        final val newInodeTemplate = NewInode(newInodeTemplateIsFile, newInodeTemplateName)

        init {
            FilterFileSystem.validateName(tagDirectory)
            FilterFileSystem.validateName(tagFile)
            FilterFileSystem.validateName(tagInput)
            FilterFileSystem.validateName(tagLostAndFound)
            FilterFileSystem.validateName(tagUnknown)
        }
    }

    @Serializable
    @Component
    class Paths(
        @Value("\${application.paths.bookmarks}") private val bookmarksString: String,
        @Value("\${application.paths.jail}") private val jailString: String,
        @Value("\${application.paths.task-bin}") private val taskBinariesString: String,
        @Value("\${application.paths.task-mail}") private val taskMailString: String,
        @Value("\${application.paths.web}") private val webString: String,
    ) {
        @Transient
        final val bookmarks: Path? = if (bookmarksString.isNotEmpty()) Path.of(bookmarksString).also {
            if (!it.isDirectory()) {
                throw HttpException.badRequest("Not a directory.")
            }
            if (!it.isReadable()) {
                throw HttpException.badRequest("Insufficient permission.")
            }
        } else null

        @Transient
        final val jail: RootFileSystem = NativeFileSystem(Path.of(jailString).also {
            if (!it.isDirectory()) {
                throw HttpException.badRequest("Not a directory.")
            }
            if (!it.isReadable()) {
                throw HttpException.badRequest("Insufficient permission.")
            }
        })

        @Transient
        final val taskMail: SafePath? = if (taskMailString.isNotEmpty()) {
            val localPath = Path.of(taskMailString)
            if (!localPath.isDirectory()) {
                throw HttpException.badRequest("Not a directory.")
            }
            if (!localPath.isReadable()) {
                throw HttpException.badRequest("Insufficient permission.")
            }
            if (!taskMailString.startsWith(jailString)) {
                throw HttpException.badRequest("TaskMail must be inside jail.")
            }
            SafePath(taskMailString.substring(jailString.length, taskMailString.length))
        } else null

        @Transient
        final val taskBinaries: RootFileSystem? = if (taskBinariesString.isNotEmpty()) NativeFileSystem(Path.of(taskBinariesString).also {
            if (!it.isDirectory()) {
                throw HttpException.badRequest("Not a directory.")
            }
            if (!it.isReadable()) {
                throw HttpException.badRequest("Insufficient permission.")
            }
        }) else null

        @Transient
        final val web: Path? = if (webString.isNotEmpty()) Path.of(webString).also {
            if (!it.isDirectory()) {
                throw HttpException.badRequest("Not a directory.")
            }
            if (!it.isReadable()) {
                throw HttpException.badRequest("Insufficient permission.")
            }
        } else null
    }

    object Header {
        // I recommend using small letters.
        const val lastModified = "x-last-modified"
    }

    object RequestMappingPaths {
        const val authenticated = "/service"
        const val bookmarks = "/bookmarks"
        const val bookmarksPrivate = "$bookmarks/private"
        const val error = "/error"
        const val local = "/local"
        const val login = "/login"
        const val web = "/web"

        object Authenticated {
            const val command = "$authenticated/command"
            const val directory = "$authenticated/directory"
            const val file = "$authenticated/file"
            const val fileStream = "$file/stream"
            const val inode = "$authenticated/inode"
            const val logout = "$authenticated/logout"
            const val suggestion = "$authenticated/suggestion"
            const val thumbnail = "$authenticated/thumbnail"
        }

        object Local {
            const val task = "$local/task"
        }
    }

    @Serializable
    @Component
    class Binaries(
        @Value("\${application.binaries.share}") val share: String,
    )

    @Serializable
    @Component
    class Security(
        @Value("\${application.security.allowed-origins}") private val allowedOriginsString: String,
        @Value("\${application.security.enable-cors}") val enableCors: Boolean,
        @Value("\${application.security.key}") val key: String,
        @Value("\${application.security.password-hash}") val passwordHash: String,
        @Value("\${application.security.token-cookie-name}") val tokenCookieName: String,
        @Value("\${application.security.token-max-age-seconds}") val tokenMaxAgeSeconds: Int,
        @Value("\${server.servlet.session.cookie.secure}") val useOnlySecureCookies: Boolean,
        @Value("\${application.security.usernames}") private val usernamesString: String,
    ) {
        final val allowedOrigins = allowedOriginsString.split(",").toTypedArray()
        final val usernames = usernamesString.split(",").toTypedArray()
    }

    fun createRequestCtx(now: LocalDate = LocalDate.now()): RequestCtx =
        RequestCtx(this, now)

    @Transient
    private var nextLogId = System.currentTimeMillis()

    fun incrementLogId(): String =
        nextLogId++.toString()

    @Transient
    private val filterFileSystemCache = mutableMapOf<SafePath, FilterFileSystem>()

    @Scheduled(cron = "0 0 5 * * *")
    fun doDaily() {
        filterFileSystemCache.clear()
    }

    fun cacheFilterFileSystem(
        key: SafePath,
        lastModified: FileTime?,
        factory: () -> FilterCtx,
    ): FilterFileSystem {
        if (lastModified == null) {
            filterFileSystemCache.remove(key)
            return FilterFileSystem(null, factory())
        }
        val system = filterFileSystemCache[key]
        return when {
            system == null -> FilterFileSystem(lastModified, factory()).also { filterFileSystemCache[key] = it }
            system.time != lastModified -> system.also {
                it.time = lastModified
                it.ctx = factory()
            }

            else -> system
        }
    }
}
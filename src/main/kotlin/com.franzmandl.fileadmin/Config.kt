package com.franzmandl.fileadmin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File

@Component
class Config(
    @Value("\${application.hostname}") val hostname: String,
    @Value("\${application.settings-file-name}") val settingsFileName: String,
    @Autowired val binaries: Binaries,
    @Autowired val files: Files,
    @Autowired val paths: Paths,
    @Autowired val security: Security,
) {
    @Component
    class Paths(
        @Value("\${application.paths.bookmarks}") val bookmarks: String,
        @Value("\${application.paths.web}") val web: String,
        @Value("\${application.paths.jail}") val jail: String,
        @Value("\${application.paths.ticket_bins}") val ticketBinaries: String,
    )

    @Component
    class Files(@Autowired paths: Paths) {
        final val jail = File(paths.jail)
        final val ticketBinaries = File(paths.ticketBinaries)
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
        const val login = "/login"
        const val web = "/web"

        object Authenticated {
            const val add = "$authenticated/add"
            const val directory = "$authenticated/directory"
            const val file = "$authenticated/file"
            const val inode = "$authenticated/inode"
            const val logout = "$authenticated/logout"
            const val move = "$authenticated/move"
            const val remove = "$authenticated/remove"
            const val share = "$authenticated/share"
            const val thumbnail = "$authenticated/thumbnail"
        }
    }

    @Component
    class Binaries(
        @Value("\${application.binaries.share}") val share: String,
    )

    @Component
    class Security(
        @Value("\${application.security.enable-cors}") val enableCors: Boolean,
        @Value("\${application.security.allowed-origins}") allowedOriginsString: String,
        @Value("\${application.security.password-hash}") val passwordHash: String,
        @Value("\${application.security.token-cookie-name}") val tokenCookieName: String,
        @Value("\${application.security.token-max-age-seconds}") val tokenMaxAgeSeconds: Int,
        @Value("\${server.servlet.session.cookie.secure}") val useOnlySecureCookies: Boolean,
        @Value("\${application.security.usernames}") usernamesString: String,
    ) {
        final val allowedOrigins = allowedOriginsString.split(",").toTypedArray()
        final val usernames = usernamesString.split(",").toTypedArray()
    }
}

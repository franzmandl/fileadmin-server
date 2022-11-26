package com.franzmandl.fileadmin.model

import com.franzmandl.fileadmin.Config
import com.franzmandl.fileadmin.ticket.TicketObjects
import com.franzmandl.fileadmin.ticket.TicketUtil
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

@Serializable
data class Directory(
    val inode: Inode,
    val inodes: List<Inode>,
    val settings: Settings,
) {
    companion object {
        fun create(config: Config, files: List<File>, directory: File, now: LocalDate): Directory {
            // WARNING: directory might not exist because it can be a wildcard * directory.
            val ticketObjectsCache = mutableMapOf<String, TicketObjects?>()
            fun createTicketObjects(file: File) = ticketObjectsCache.computeIfAbsent(
                file.parent
            ) { TicketUtil.createTicketObjects(config, file.parentFile, now) }
            return Directory(
                Inode.create(config, directory, createTicketObjects(directory)),
                files.map { file ->
                    Inode.create(
                        config,
                        file,
                        createTicketObjects(file)
                    )
                },
                Settings.create(config, directory)
            )
        }
    }
}
package com.franzmandl.fileadmin.model

import com.franzmandl.fileadmin.Config
import com.franzmandl.fileadmin.ticket.TicketException
import com.franzmandl.fileadmin.ticket.TicketObjects
import com.franzmandl.fileadmin.util.Util
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import kotlin.io.path.notExists

@Serializable
data class Inode(
    val basename: String,
    val friendlyName: String?,
    /** Does not start with a slash! */
    val dirname: String,
    /** Required to resolve relative paths in markdown files. Does not start with a slash! */
    val realDirname: String,
    val path: String,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val lastModified: Long,
    val canRead: Boolean,
    val canWrite: Boolean,
    val size: Long,
    val mimeType: String,
    val target: String?,
    val ticket: Ticket?,
    val error: String?,
    val isRoot: Boolean,
    val isVirtual: Boolean,
) {
    companion object {
        fun create(
            config: Config,
            file: File,
            ticketObjects: TicketObjects?,
        ): Inode {
            val pathParts = file.path.split("/")
            var error: String? = null
            var friendlyName: String? = null
            var ticket: Ticket? = null
            if (ticketObjects != null) {
                try {
                    ticket = Ticket.create(file, pathParts, ticketObjects)
                    friendlyName = if (ticket.usesExpression) "${ticket.date}${ticket.fileEnding}" else null
                } catch (e: TicketException) {
                    error = e.message
                }
            }
            val nioPath = file.toPath()
            val parentDir = nioPath.parent.toString()
            val isRoot = nioPath.toString() == config.paths.jail
            val isVirtual = nioPath.notExists()
            val dirname =
                if (isRoot) "" else if (parentDir.length == config.paths.jail.length) "" else parentDir.substring(config.paths.jail.length + 1)
            return Inode(
                if (isRoot) "" else file.name,
                if (isRoot) null else friendlyName,
                dirname,
                if (isRoot || isVirtual) dirname else config.files.jail.toPath().toRealPath()
                    .relativize(nioPath.toRealPath().parent).toString(),
                if (isRoot) "/" else file.path.substring(config.paths.jail.length),
                file.isDirectory,
                file.isFile,
                file.lastModified(),
                file.canRead(),
                file.canRead() && file.canWrite(),
                if (file.isDirectory) {
                    val list = file.list()
                    list?.size?.toLong() ?: 0
                } else {
                    file.length()
                },
                if (file.isFile) Util.getMimeType(nioPath, "") else "",
                if (isRoot) null else if (Files.isSymbolicLink(nioPath)) Files.readSymbolicLink(nioPath)
                    .toString() else null,
                ticket,
                error,
                isRoot,
                isVirtual
            )
        }
    }
}
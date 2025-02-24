package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.OptionalValue
import com.franzmandl.fileadmin.common.ProcessResult
import com.franzmandl.fileadmin.resource.RequestCtx
import org.springframework.core.io.UrlResource
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.*
import java.nio.file.attribute.FileTime
import kotlin.io.path.*

class NativeInode(
    private val localPath: Path,
    override val parent: OptionalValue<Inode0>,
    override val path: SafePath,
) : Inode0 {
    private val operation = Operation(this)
    private val permission = Permission(this)
    val publicLocalPath = localPath

    override val bytes: ByteArray
        get() = try {
            localPath.readBytes()
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().getFile(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().getFile(path).build()
        }

    val canRead: Boolean get() = localPath.isReadable()
    val canWrite: Boolean get() = localPath.isWritable()

    override val children: Set<SafePath>
        get() = try {
            localPath.useDirectoryEntries { sequence -> sequence.map { path.resolve(it.name) }.toSet() }
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().children(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().children(path).build()
        } catch (_: NotDirectoryException) {
            throw HttpException.notSupported().children(path).build()
        }

    val childrenCount: Int?
        get() = try {
            localPath.useDirectoryEntries { sequence -> sequence.count() }
        } catch (_: FileSystemException) {
            null
        }

    override val contentOperation: ContentInode.Operation = operation
    override val contentPermission: ContentInode.Operation = permission

    override fun copy(ctx: RequestCtx, target: Inode1<*>) {
        when (target.inode0) {
            is NativeInode -> {
                if (localPath == target.inode0.localPath) {
                    throw HttpException.sameTarget().copy(path).to(target.inode0.path).build()
                }
                try {
                    localPath.copyTo(target.inode0.localPath)
                } catch (e: AccessDeniedException) {
                    throw (if (e.file == localPath.toString()) HttpException.notAllowed() else HttpException.notAllowedDestination())
                        .copy(path).to(target.inode0.path).build()
                } catch (_: FileAlreadyExistsException) {
                    throw HttpException.alreadyExists().copy(path).to(target.inode0.path).build()
                } catch (_: FileSystemException) {
                    throw HttpException.notSupportedParent().copy(path).to(target.inode0.path).build()
                } catch (e: NoSuchFileException) {
                    throw (if (e.file == localPath.toString()) HttpException.notExists() else HttpException.notExistsParent())
                        .copy(path).to(target.inode0.path).build()
                }
            }

            else -> throw HttpException.notSupported().copy(path).to(target.inode0.path).build()
        }
    }

    override fun create(ctx: RequestCtx, isFile: Boolean) {
        try {
            if (isFile) {
                localPath.createFile()
            } else {
                localPath.createDirectory()
            }
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().create(path).build()
        } catch (_: FileAlreadyExistsException) {
            throw HttpException.alreadyExists().create(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExistsParent().create(path).build()
        }
    }

    override fun delete(ctx: RequestCtx) {
        try {
            localPath.deleteExisting()
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().delete(path).build()
        } catch (_: DirectoryNotEmptyException) {
            throw HttpException.directoryNotEmpty().delete(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().delete(path).build()
        }
    }

    val exists: Boolean get() = localPath.exists(LinkOption.NOFOLLOW_LINKS) // Makes broken symlink to exist.

    override val inputStream: InputStream
        get() = try {
            localPath.inputStream()
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().getFile(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().getFile(path).build()
        }

    override val isDirectory: Boolean get() = localPath.isDirectory()
    override val isFile: Boolean get() = localPath.isRegularFile()
    val isLink: Boolean get() = localPath.isSymbolicLink()
    override val isVirtual: Boolean = false

    override val lastModified: FileTime?
        get() =
            try {
                localPath.getLastModifiedTime()
            } catch (_: AccessDeniedException) {
                throw HttpException.notAllowed().lastModified(path).build()
            } catch (_: FileSystemException) {  // Thrown on broken symbolic link.
                null
            } catch (_: NoSuchFileException) {
                throw HttpException.notExists().lastModified(path).build()
            }

    override val lines: List<String>
        get() = try {
            localPath.readLines()
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().getFile(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().getFile(path).build()
        }

    val linkTarget: UnsafePath get() = UnsafePath(localPath.readSymbolicLink())

    override val mimeType: String by lazy {
        try {
            CommonUtil.getMimeType(localPath)
        } catch (_: IOException) {
            MediaType.TEXT_PLAIN_VALUE
        }
    }

    override fun move(ctx: RequestCtx, target: Inode1<*>) {
        when (target.inode0) {
            is NativeInode -> {
                if (localPath == target.inode0.localPath) {
                    throw HttpException.sameTarget().move(path).to(target.inode0.path).build()
                }
                try {
                    localPath.moveTo(target.inode0.localPath)
                } catch (e: AccessDeniedException) {
                    throw (if (e.file == localPath.toString()) HttpException.notAllowed() else HttpException.notAllowedDestination())
                        .move(path).to(target.inode0.path).build()
                } catch (_: FileAlreadyExistsException) {
                    throw HttpException.alreadyExists().move(path).to(target.inode0.path).build()
                } catch (_: FileSystemException) {
                    throw HttpException.notSupportedParent().move(path).to(target.inode0.path).build()
                } catch (e: NoSuchFileException) {
                    throw (if (e.file == localPath.toString()) HttpException.notExists() else HttpException.notExistsParent())
                        .move(path).to(target.inode0.path).build()
                }
            }

            else -> throw HttpException.notSupported().move(path).to(target.inode0.path).build()
        }
    }

    override val outputStream: OutputStream
        get() = try {
            localPath.outputStream(options = writeOpenOptions)
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().setFile(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().setFile(path).build()
        }

    override val sizeOfDirectory: Int
        get() = try {
            localPath.useDirectoryEntries { sequence -> sequence.count() }
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().sizeOfDirectory(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().sizeOfDirectory(path).build()
        } catch (_: NotDirectoryException) {
            throw HttpException.notSupported().sizeOfDirectory(path).build()
        }

    override val sizeOfFile: Long
        get() = try {
            localPath.fileSize()
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().sizeOfFile(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().sizeOfFile(path).build()
        }

    override fun stream(requestHeaders: HttpHeaders, responseHeaders: HttpHeaders): ResponseEntity<ResourceRegion> =
        try {
            responseHeaders["Accept-Ranges"] = "bytes"
            // See https://melgenek.github.io/spring-video-service
            // See https://github.com/melgenek/spring-video-service
            val resource = UrlResource(localPath.toUri())
            val contentLength = resource.contentLength()
            val range = requestHeaders.range.firstOrNull()
            if (range != null) {
                val position = range.getRangeStart(contentLength)
                val end = range.getRangeEnd(contentLength)
                val count = (end - position + 1).coerceAtMost(resourceRegionMaxRangeCount)
                ResponseEntity(ResourceRegion(resource, position, count), responseHeaders, HttpStatus.PARTIAL_CONTENT)
            } else if (contentLength <= resourceRegionMaxRangeCount) {
                // HttpStatus.OK is necessary to download files via Brave mobile browser.
                ResponseEntity(ResourceRegion(resource, 0, contentLength), responseHeaders, HttpStatus.OK)
            } else {
                ResponseEntity(ResourceRegion(resource, 0, resourceRegionMaxRangeCount), responseHeaders, HttpStatus.PARTIAL_CONTENT)
            }
        } catch (e: FileNotFoundException) {
            // This exception is not thrown and caught here if only the file is not allowed to be read.
            // In this case the exception is thrown later by spring when it tries to read the file leading to an internal server error.
            throw (if (e.message?.endsWith(" (Permission denied)") == true) HttpException.notAllowed() else HttpException.notExists())
                .stream(path).build()
        }

    override fun setText(value: String) {
        try {
            localPath.writeText(value, options = writeOpenOptions)
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().setFile(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().setFile(path).build()
        }
    }

    override fun share(ctx: RequestCtx, days: Int): String {
        if (!isFile) throw HttpException.notSupported().share(path).build()
        if (!canRead) throw HttpException.notAllowed().share(path).build()
        val processResult =
            ProcessResult.create(
                ProcessBuilder(
                    ctx.application.share.binaryPath,
                    "--days",
                    days.toString(),
                    "--format",
                    "json",
                    localPath.toString()
                ).start()
            )
                ?: throw HttpException.processTimeout().share(path).build()
        if (processResult.exitValue != 0 || processResult.stderr.isNotEmpty()) {
            throw HttpException.processError(processResult.toString()).share(path).build()
        }
        return processResult.stdout
    }

    override val text: String
        get() = try {
            localPath.readText()
        } catch (_: AccessDeniedException) {
            throw HttpException.notAllowed().getFile(path).build()
        } catch (_: NoSuchFileException) {
            throw HttpException.notExists().getFile(path).build()
        }

    override fun toString(): String = localPath.toString()

    override val treeOperation: TreeInode.Operation = operation
    override val treePermission: TreeInode.Operation = permission

    companion object {
        private const val resourceRegionMaxRangeCount = 10L * 1024L * 1024L

        /**
         * By default, StandardOpenOption.CREATE is also used see kotlin/io/path/PathReadWrite.kt Path.outputStream
         */
        private val writeOpenOptions = arrayOf(StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)

        private class Operation(
            private val inode: NativeInode,
        ) : ContentInode.Operation, TreeInode.Operation {
            override val canDirectoryAdd: Boolean get() = inode.isDirectory  // Implies exists.
            override val canDirectoryGet: Boolean get() = inode.isDirectory  // Implies exists.
            override val canFileGet: Boolean get() = inode.isFile  // Implies exists.
            override val canFileSet: Boolean get() = inode.isFile  // Implies exists.
            override val canFileStream: Boolean get() = inode.isFile  // Implies exists.
            override val canInodeCopy: Boolean get() = inode.exists
            override val canInodeDelete: Boolean get() = inode.exists
            override val canInodeMove: Boolean get() = inode.exists
            override val canInodeRename: Boolean get() = inode.exists
            override val canInodeShare: Boolean get() = inode.isFile  // Implies exists.
            override val canInodeToDirectory: Boolean get() = inode.isFile  // Implies exists.
            override val canInodeToFile: Boolean get() = inode.isDirectory && inode.childrenCount.let { it != null && it <= 1 }  // Implies exists.
        }

        private class Permission(
            private val inode: NativeInode,
        ) : ContentInode.Operation, TreeInode.Operation {
            override val canDirectoryAdd: Boolean get() = inode.operation.canDirectoryAdd && inode.canWrite
            override val canDirectoryGet: Boolean get() = inode.operation.canDirectoryGet && inode.canRead
            override val canFileGet: Boolean get() = inode.operation.canFileGet && inode.canRead
            override val canFileSet: Boolean get() = inode.operation.canFileSet && inode.canWrite
            override val canFileStream: Boolean get() = inode.operation.canFileStream && inode.canRead
            override val canInodeCopy: Boolean get() = inode.operation.canInodeCopy && inode.canRead
            override val canInodeDelete: Boolean get() = inode.operation.canInodeDelete && parentCanWrite
            override val canInodeMove: Boolean get() = inode.operation.canInodeMove && parentCanWrite
            override val canInodeRename: Boolean get() = inode.operation.canInodeRename && parentCanWrite
            override val canInodeShare: Boolean get() = inode.operation.canInodeShare && inode.canRead
            override val canInodeToDirectory: Boolean get() = inode.operation.canInodeToDirectory && parentCanWrite
            override val canInodeToFile: Boolean get() = inode.operation.canInodeToFile && parentCanWrite
            private val parentCanWrite: Boolean get() = inode.parent.nullableValue.let { it is NativeInode && it.canWrite }
        }
    }
}
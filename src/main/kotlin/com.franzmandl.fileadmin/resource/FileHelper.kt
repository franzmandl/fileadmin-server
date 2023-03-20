package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.model.*
import com.franzmandl.fileadmin.task.TaskCtx
import com.franzmandl.fileadmin.task.TaskException
import com.franzmandl.fileadmin.vfs.*
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

object FileHelper {
    fun applyAdd(ctx: RequestCtx, command: Add): InodeModel {
        val path = command.path.resolve(PathUtil.validateName(command.newInode.name))
        val inode = ctx.getInode(path)
        inode.create(ctx, command.newInode.isFile)
        inode.config.filter?.ctx?.scanItems(ctx, false, CommonUtil::noop)
        return getInodeModel(ctx, inode)
    }

    fun applyDelete(ctx: RequestCtx, command: Delete) {
        val inode = ctx.getInode(command.path)
        inode.delete(ctx)
        inode.config.filter?.ctx?.scanItems(ctx, false, CommonUtil::noop)
    }

    fun applyMove(ctx: RequestCtx, command: Move): InodeModel {
        val oldInode = ctx.getInode(command.path)
        val newInode = ctx.getInode(command.newPath)
        oldInode.move(ctx, newInode)
        oldInode.config.filter?.ctx?.scanItems(ctx, false, CommonUtil::noop)
        newInode.config.filter?.ctx?.scanItems(ctx, false, CommonUtil::noop)
        return getInodeModel(ctx, newInode)
    }

    fun applyRename(ctx: RequestCtx, command: Rename): InodeModel {
        val oldInode = ctx.getInode(command.path)
        val newInode = ctx.getInode(command.path.resolveSibling(PathUtil.validateName(command.newName)))
        oldInode.move(ctx, newInode)
        newInode.config.filter?.ctx?.scanItems(ctx, false, CommonUtil::noop)
        return getInodeModel(ctx, newInode)
    }

    fun applyShare(ctx: RequestCtx, command: Share): String {
        val inode = ctx.getInode(command.path)
        return inode.share(ctx, command.days)
    }

    fun applyToDirectory(ctx: RequestCtx, command: ToDirectory): InodeModel {
        val oldFile = ctx.getInode(command.path)
        val ending = command.path.name.lastIndexOf('.').let { index ->
            if (index == -1 || index < command.path.name.length - 4) "" else command.path.name.substring(index, command.path.name.length)
        }
        val newDirectory = if (ending.isEmpty()) {
            oldFile
        } else {
            ctx.getInode(command.path.resolveSibling(command.path.name.substring(0, command.path.name.length - ending.length)))
        }
        if (oldFile.sizeFile > 0) {
            val readmeFile = ctx.getInode(newDirectory.path.resolve("readme$ending"))
            val temporaryFile = ctx.getInode(oldFile.path.resolveSibling(oldFile.path.name + ".tmp"))
            oldFile.move(ctx, temporaryFile)
            newDirectory.create(ctx, false)
            temporaryFile.move(ctx, readmeFile)
        } else {
            oldFile.delete(ctx)
            newDirectory.create(ctx, false)
        }
        return getInodeModel(ctx, newDirectory)
    }

    fun applyToFile(ctx: RequestCtx, command: ToFile): InodeModel {
        val oldDirectory = ctx.getInode(command.path)
        val iterator = oldDirectory.children.iterator()
        if (!iterator.hasNext()) {
            oldDirectory.delete(ctx)
            val newFile = ctx.getInode(oldDirectory.path.resolveSibling(oldDirectory.path.name + ".txt")).apply { create(ctx, true) }
            return getInodeModel(ctx, newFile)
        }
        val oldFilePath = iterator.next()
        if (iterator.hasNext()) {
            throw HttpException.badRequest("Directory '${command.path}' has more than one child.")
        }
        val newFile = oldFilePath.name.lastIndexOf('.').let { index ->
            if (index == -1 || index < oldFilePath.name.length - 4) {
                oldDirectory
            } else {
                val ending = oldFilePath.name.substring(index, oldFilePath.name.length)
                ctx.getInode(command.path.resolveSibling(command.path.name + ending))
            }
        }
        val oldFile = ctx.getInode(oldFilePath)
        val temporaryFile = ctx.getInode(oldDirectory.path.resolveSibling(oldDirectory.path.name + ".tmp"))
        oldFile.move(ctx, temporaryFile)
        oldDirectory.delete(ctx)
        temporaryFile.move(ctx, newFile)
        return getInodeModel(ctx, newFile)
    }

    fun getThumbnail(ctx: RequestCtx, path: SafePath, maxDimension: Int, responseHeaders: HttpHeaders): ByteArray {
        val inode = ctx.getInode(path)
        val originalImage = try {
            ImageIO.read(inode.inputStream)!!
        } catch (_: Exception) {
            return respondOriginal(inode).also { setResponseHeadersForFile(responseHeaders, inode) }
        }
        setResponseHeadersForFile(responseHeaders, inode)
        var width = originalImage.width
        var height = originalImage.height
        if (width <= maxDimension && height <= maxDimension) {
            return respondOriginal(inode)
        }
        if (width < height) {
            width = maxDimension * width / height
            height = maxDimension
        } else {
            height = maxDimension * height / width
            width = maxDimension
        }
        val scaledImage = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        val imageBuff = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        imageBuff.graphics.drawImage(scaledImage, 0, 0, Color(0xe9, 0xec, 0xef), null)
        val buffer = ByteArrayOutputStream()
        ImageIO.write(imageBuff, "jpg", buffer)
        responseHeaders.contentType = MediaType.IMAGE_JPEG
        return buffer.toByteArray()
    }

    private fun respondOriginal(inode: Inode): ByteArray =
        try {
            inode.bytes
        } catch (_: Exception) {
            throw HttpException.badRequest("Illegal image.")
        }

    fun getFile(ctx: RequestCtx, path: SafePath, responseHeaders: HttpHeaders): ByteArray {
        val inode = ctx.getInode(path)
        val result = inode.bytes
        setResponseHeadersForFile(responseHeaders, inode)
        return result
    }

    fun getFileStream(ctx: RequestCtx, path: SafePath, requestHeaders: HttpHeaders): ResponseEntity<ResourceRegion> {
        val inode = ctx.getInode(path)
        val responseHeaders = HttpHeaders()
        val result = inode.stream(requestHeaders, responseHeaders)
        setResponseHeadersForFile(responseHeaders, inode)
        return result
    }

    fun setFile(ctx: RequestCtx, path: SafePath, expectedLastModified: Long?, inputStream: InputStream): InodeModel {
        val inode = ctx.getInode(path)
        checkLastModified(inode, expectedLastModified)
        // See https://www.baeldung.com/spring-multipartfile-to-file
        // content.transferTo(file)  // Has problems with relative paths
        inode.outputStream.use { outputStream -> inputStream.transferTo(outputStream) }
        return getInodeModel(ctx, inode)
    }

    private fun checkLastModified(inode: Inode, expectedLastModified: Long?) {
        val actualLastModified = inode.lastModified?.toMillis()
        if (actualLastModified == null) {
            throw HttpException.badRequest("File's last modified is null.")
        } else if (expectedLastModified == null) {
            throw HttpException.badRequest("Your last modified is null.")
        } else if (expectedLastModified != actualLastModified) {
            throw HttpException.badRequest(
                "File has been modified: "
                        + "Yours=" + CommonUtil.yyyy_MM_dd_HH_mm_ss_SSS_format.format(CommonUtil.convertToLocalDateTime(expectedLastModified))
                        + " != "
                        + "Theirs=" + CommonUtil.yyyy_MM_dd_HH_mm_ss_SSS_format.format(CommonUtil.convertToLocalDateTime(actualLastModified))
            )
        }
    }

    private fun getTaskCtx(ctx: RequestCtx, inode: Inode): TaskCtx? {
        return if (inode.config.isTask) {
            ctx.getTaskCtx(inode.parent.nullableValue ?: return null)
        } else null
    }

    fun getInodeModel(ctx: RequestCtx, inode: Inode): InodeModel {
        val isRoot = inode.path.isRoot
        var error: String? = null
        var friendlyName: String? = null
        var task: Task? = null
        val taskCtx = getTaskCtx(ctx, inode)
        if (taskCtx != null) {
            try {
                task = TaskHelper.createTaskModel(taskCtx, inode, inode.path.parts)
                friendlyName = if (task.usesExpression && !isRoot) "${task.date}${task.fileEnding}" else null
            } catch (e: TaskException) {
                error = e.message
            }
        }
        val operation = getOperationModel(inode)
        return InodeModel(
            error = error,
            filterHighlightTags = inode.config.filterHighlightTags?.map { it.name },
            filterOutputPath = inode.config.filter?.ctx?.output,
            friendlyName = friendlyName,
            isDirectory = inode.isDirectory,
            isFile = inode.isFile,
            isRoot = isRoot,
            isRunLast = inode.config.isRunLast,
            isVirtual = inode.isVirtual,
            lastModified = inode.lastModified?.toMillis(),
            link = if (inode is Link) LinkModel(inode.target, inode.targetInode.path) else null,
            localPath = if (inode is NativeInode) inode.publicLocalPath.toString() else null,
            mimeType = if (operation.canFileGet) inode.mimeType else null,
            operation = operation,
            parentOperation = inode.parent.nullableValue?.let { getOperationModel(it) },
            path = inode.path,
            realPath = (if (inode is Link) inode.realTargetInode else inode).path,
            size = when {
                operation.canDirectoryGet -> inode.children.size.toLong()
                inode.isFile -> inode.sizeFile
                else -> null
            },
            task = task,
        )
    }

    private fun getOperationModel(inode: InodeWithoutConfig): OperationModel =
        OperationModel(
            canDirectoryAdd = inode.contentPermission.canDirectoryAdd,
            canDirectoryGet = inode.contentPermission.canDirectoryGet,
            canFileGet = inode.contentPermission.canFileGet,
            canFileSet = inode.contentPermission.canFileSet,
            canFileStream = inode.contentPermission.canFileStream,
            canInodeCopy = inode.contentPermission.canInodeCopy,
            canInodeDelete = inode.treePermission.canInodeDelete,
            canInodeMove = inode.treePermission.canInodeMove,
            canInodeRename = inode.treePermission.canInodeRename,
            canInodeShare = inode.contentPermission.canInodeShare,
            canInodeToDirectory = inode.contentPermission.canInodeToDirectory,
            canInodeToFile = inode.contentPermission.canInodeToFile,
        )

    private fun setResponseHeadersForFile(responseHeaders: HttpHeaders, inode: Inode) {
        val lastModified = inode.lastModified
        if (lastModified != null) {
            responseHeaders.eTag = "\"" + lastModified.toMillis() + "\""
            responseHeaders[ApplicationCtx.Header.lastModified] = lastModified.toMillis().toString()
        }
        CommonUtil.setContentTypeAsString(responseHeaders, CommonUtil.appendCharset(inode.mimeType))
        try {
            responseHeaders.contentDisposition = ContentDisposition.builder("inline")
                .filename(inode.path.name)
                .build()
        } catch (_: IllegalArgumentException) {
            // Thrown if filename is empty or just contains whitespaces.
        }
    }
}
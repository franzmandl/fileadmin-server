package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.common.*
import com.franzmandl.fileadmin.dto.*
import com.franzmandl.fileadmin.dto.config.CommandId
import com.franzmandl.fileadmin.filter.ItemDtoHelper
import com.franzmandl.fileadmin.task.TaskCtx
import com.franzmandl.fileadmin.task.TaskException
import com.franzmandl.fileadmin.vfs.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.tools.imageio.ImageIOUtil
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

object FileHelper {
    fun applyAdd(ctx: RequestCtx, command: Add): InodeDto {
        val path = command.path.resolve(PathUtil.validateName(command.newInode.name))
        val inode = ctx.getInode(path)
        inode.inode0.create(ctx, command.newInode.isFile)
        inode.config.filter?.ctx?.scanItems(ctx, CommandId.Add, ErrorHandler.noop) {
            inode.config.filter.ctx.addItem(ctx, inode, ErrorHandler.noop)
        }
        return getInodeDto(ctx, null, inode)
    }

    fun applyDelete(ctx: RequestCtx, command: Delete) {
        val inode = ctx.getInode(command.path)
        inode.inode0.delete(ctx)
        inode.config.filter?.ctx?.scanItems(ctx, CommandId.Delete, ErrorHandler.noop) {
            inode.config.filter.ctx.deleteItem(inode.inode0.path)
        }
    }

    fun applyMove(ctx: RequestCtx, command: Move): InodeDto {
        val oldInode = ctx.getInode(command.path)
        val newInode = ctx.getInode(command.newPath)
        oldInode.inode0.move(ctx, newInode)
        oldInode.config.filter?.ctx?.scanItems(ctx, CommandId.Move, ErrorHandler.noop) {
            oldInode.config.filter.ctx.deleteItem(oldInode.inode0.path)
        }
        newInode.config.filter?.ctx?.scanItems(ctx, CommandId.Move, ErrorHandler.noop) {
            newInode.config.filter.ctx.addItem(ctx, newInode, ErrorHandler.noop)
        }
        return getInodeDto(ctx, null, newInode)
    }

    fun applyRename(ctx: RequestCtx, command: Rename): InodeDto {
        val oldInode = ctx.getInode(command.path)
        val newInode = ctx.getInode(command.path.resolveSibling(PathUtil.validateName(command.newName)))
        oldInode.inode0.move(ctx, newInode)
        newInode.config.filter?.ctx?.scanItems(ctx, CommandId.Rename, ErrorHandler.noop) {
            oldInode.config.filter?.ctx?.deleteItem(oldInode.inode0.path)
            newInode.config.filter.ctx.addItem(ctx, newInode, ErrorHandler.noop)
        }
        return getInodeDto(ctx, null, newInode)
    }

    fun applyShare(ctx: RequestCtx, command: Share): String {
        val inode = ctx.getInode(command.path)
        return inode.inode0.share(ctx, command.days)
    }

    fun applyToDirectory(ctx: RequestCtx, command: ToDirectory): InodeDto {
        val oldFile = ctx.getInode(command.path)
        val ending = command.path.name.lastIndexOf('.').let { index ->
            if (index == -1 || index < command.path.name.length - CommonUtil.maxFileEndingLength) "" else command.path.name.substring(index, command.path.name.length)
        }
        val newDirectory = if (ending.isEmpty()) {
            oldFile
        } else {
            ctx.getInode(command.path.resolveSibling(command.path.name.substring(0, command.path.name.length - ending.length)))
        }
        if (oldFile.inode0.sizeOfFile > 0) {
            val readmeFile = ctx.getInode(newDirectory.inode0.path.resolve("readme$ending"))
            val temporaryFile = ctx.getInode(oldFile.inode0.path.resolveSibling(oldFile.inode0.path.name + ".tmp"))
            oldFile.inode0.move(ctx, temporaryFile)
            newDirectory.inode0.create(ctx, false)
            temporaryFile.inode0.move(ctx, readmeFile)
        } else {
            oldFile.inode0.delete(ctx)
            newDirectory.inode0.create(ctx, false)
        }
        newDirectory.config.filter?.ctx?.scanItems(ctx, CommandId.ToDirectory, ErrorHandler.noop) {
            oldFile.config.filter?.ctx?.deleteItem(oldFile.inode0.path)
            newDirectory.config.filter.ctx.addItem(ctx, newDirectory, ErrorHandler.noop)
        }
        return getInodeDto(ctx, null, newDirectory)
    }

    fun applyToFile(ctx: RequestCtx, command: ToFile): InodeDto {
        val oldDirectory = ctx.getInode(command.path)
        val iterator = oldDirectory.inode0.children.iterator()
        if (!iterator.hasNext()) {
            oldDirectory.inode0.delete(ctx)
            val newFile = ctx.getInode(oldDirectory.inode0.path.resolveSibling(oldDirectory.inode0.path.name + ".txt")).apply { inode0.create(ctx, true) }
            return getInodeDto(ctx, null, newFile)
        }
        val oldFilePath = iterator.next()
        if (iterator.hasNext()) {
            throw HttpException.badRequest("""Directory "${command.path}" has more than one child.""")
        }
        val newFile = oldFilePath.name.lastIndexOf('.').let { index ->
            if (index == -1 || index < oldFilePath.name.length - CommonUtil.maxFileEndingLength) {
                oldDirectory
            } else {
                val ending = oldFilePath.name.substring(index, oldFilePath.name.length)
                ctx.getInode(command.path.resolveSibling(command.path.name + ending))
            }
        }
        val oldFile = ctx.getInode(oldFilePath)
        val temporaryFile = ctx.getInode(oldDirectory.inode0.path.resolveSibling(oldDirectory.inode0.path.name + ".tmp"))
        oldFile.inode0.move(ctx, temporaryFile)
        oldDirectory.inode0.delete(ctx)
        temporaryFile.inode0.move(ctx, newFile)
        newFile.config.filter?.ctx?.scanItems(ctx, CommandId.ToFile, ErrorHandler.noop) {
            oldDirectory.config.filter?.ctx?.deleteItem(oldDirectory.inode0.path)
            newFile.config.filter.ctx.addItem(ctx, newFile, ErrorHandler.noop)
        }
        return getInodeDto(ctx, null, newFile)
    }

    fun getImageOfPdf(ctx: RequestCtx, path: SafePath, pageIndex: Int, maxDimension: Float, responseHeaders: HttpHeaders): ByteArray {
        val inode = ctx.getInode(path)
        val buffer = ByteArrayOutputStream()
        Loader.loadPDF(RandomAccessReadBuffer(inode.inode0.inputStream)).use { document ->
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val dpi = 72 * maxDimension / mediaBox.width.coerceAtLeast(mediaBox.height)
            val renderer = PDFRenderer(document)
            val image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB)
            ImageIOUtil.writeImage(image, "jpg", buffer)
        }
        responseHeaders.contentType = MediaType.IMAGE_JPEG
        return buffer.toByteArray()
    }

    fun getResizedImage(ctx: RequestCtx, path: SafePath, maxDimension: Int, responseHeaders: HttpHeaders): ByteArray {
        val inode = ctx.getInode(path)
        val input = try {
            inode.inode0.inputStream.use { ImageIO.read(it)!! }
        } catch (_: Exception) {
            return respondOriginal(inode).also { setResponseHeadersForFile(responseHeaders, inode) }
        }
        setResponseHeadersForFile(responseHeaders, inode)
        val scaled = ImagingUtil.scale(input, maxDimension) ?: return respondOriginal(inode)
        val orientation = when (val orientation = inode.inode0.inputStream.use { ImagingUtil.getExifOrientation(it, inode.inode0.path.name) }) {
            is ExifOrientation.Known -> orientation
            is ExifOrientation.Unset -> ExifOrientation.Known.HorizontalNormal
            is ExifOrientation.Unknown -> throw HttpException.badRequest("""Unknown EXIF orientation "${orientation.value}".""")
        }
        val transformation = ImagingUtil.getTransformation(orientation, scaled.width, scaled.height)
        val output = ImagingUtil.transformImage(scaled, transformation, orientation)
        return ImagingUtil.toJpgByteArray(output)
    }

    private fun respondOriginal(inode: Inode1<*>): ByteArray =
        try {
            inode.inode0.bytes
        } catch (_: Exception) {
            throw HttpException.badRequest("Illegal image.")
        }

    fun getFile(ctx: RequestCtx, path: SafePath, responseHeaders: HttpHeaders): ByteArray {
        val inode = ctx.getInode(path)
        val result = inode.inode0.bytes
        setResponseHeadersForFile(responseHeaders, inode)
        return result
    }

    fun getFileStream(ctx: RequestCtx, path: SafePath, requestHeaders: HttpHeaders): ResponseEntity<ResourceRegion> {
        val inode = ctx.getInode(path)
        val responseHeaders = HttpHeaders()
        val result = inode.inode0.stream(requestHeaders, responseHeaders)
        setResponseHeadersForFile(responseHeaders, inode)
        return result
    }

    fun setFile(ctx: RequestCtx, path: SafePath, expectedLastModified: Long?, inputStream: InputStream): InodeDto {
        val inode = ctx.getInode(path)
        checkLastModified(inode, expectedLastModified)
        // See https://www.baeldung.com/spring-multipartfile-to-file
        // content.transferTo(file)  // Has problems with relative paths
        inode.inode0.outputStream.use { outputStream -> inputStream.transferTo(outputStream) }
        return getInodeDto(ctx, null, inode)
    }

    private fun checkLastModified(inode: Inode1<*>, expectedLastModified: Long?) {
        val actualLastModified = inode.inode0.lastModified?.toMillis()
        when {
            actualLastModified == null -> throw HttpException.badRequest("File's last modified is null.")
            expectedLastModified == null -> throw HttpException.badRequest("Your last modified is null.")
            expectedLastModified != actualLastModified -> throw HttpException.badRequest(
                "File has been modified: "
                        + "Yours=" + CommonUtil.yyyy_MM_dd_HH_mm_ss_SSS_format.format(CommonUtil.convertToLocalDateTime(expectedLastModified))
                        + " != "
                        + "Theirs=" + CommonUtil.yyyy_MM_dd_HH_mm_ss_SSS_format.format(CommonUtil.convertToLocalDateTime(actualLastModified))
            )
        }
    }

    private fun getTaskCtx(ctx: RequestCtx, inode: Inode1<*>): TaskCtx? {
        return if (inode.config.isTask) {
            ctx.getTaskCtx(inode.inode0.parent.nullableValue ?: return null)
        } else null
    }

    fun getInodeDto(ctx: RequestCtx, overrideParent: Inode1<*>?, inode: Inode1<*>): InodeDto {
        val parent = overrideParent ?: inode
        val isRoot = inode.inode0.path.isRoot
        val errors = mutableListOf<String>()
        var friendlyName: String? = null
        var task: Task? = null
        var overrideItemTime: String? = null
        val taskCtx = getTaskCtx(ctx, inode)
        if (taskCtx != null) {
            try {
                task = TaskDtoHelper.createTaskDto(taskCtx, inode, CommonUtil.getSafe(inode.inode0.path.parts, -2) ?: throw TaskException("Cannot identify current status."))
                overrideItemTime = task.date
                friendlyName = if (task.usesExpression && !isRoot) "${task.date}${task.fileEnding}" else null
            } catch (e: TaskException) {
                errors += e.message
            }
        }
        var itemDto: ItemDto? = null
        var isTimeDirectory = false
        if (parent.config.filter != null) {
            val item = parent.config.filter.ctx.getItem(inode.inode0.path)
            itemDto = ItemDtoHelper.createItemDto(parent.config.filter.ctx.output, item, parent.config.filterResult, overrideItemTime)
            isTimeDirectory = parent.config.filter.ctx.isTimeDirectory(inode.inode0.path)
            try {
                task = task ?: item?.let { TaskDtoHelper.createTaskDto(ctx, parent.config.filter.ctx, it) }
            } catch (e: TaskException) {
                errors += e.message
            }
        }
        val operation = getOperationDto(inode.inode0)
        return InodeDto(
            errors = errors,
            friendlyName = friendlyName,
            isDirectory = inode.inode0.isDirectory,
            isFile = inode.inode0.isFile,
            isRoot = isRoot,
            isRunLast = inode.config.isRunLast,
            isTimeDirectory = isTimeDirectory,
            isVirtual = inode.inode0.isVirtual,
            item = itemDto,
            lastModifiedMilliseconds = inode.inode0.lastModified?.toMillis(),
            link = if (inode.inode0 is Link) LinkDto(inode.inode0.target, inode.inode0.targetInode.inode0.path) else null,
            localPath = if (inode.inode0 is NativeInode) inode.inode0.publicLocalPath.toString() else null,
            mimeType = inode.inode0.mimeType,
            operation = operation,
            parentOperation = inode.inode0.parent.nullableValue?.let { getOperationDto(it) },
            path = inode.inode0.path,
            realPath = (if (inode.inode0 is Link) inode.inode0.realTargetInode else inode.inode0).path,
            size = when {
                operation.canDirectoryGet -> (if (overrideParent == null) inode.inode0.sizeOfDirectory.toLong() else inode.inode0.estimatedSizeOfDirectory?.toLong())
                inode.inode0.isFile -> if (overrideParent == null) inode.inode0.sizeOfFile else inode.inode0.estimatedSizeOfFile
                else -> null
            },
            task = task,
        )
    }

    private fun getOperationDto(inode: Inode0): OperationDto =
        OperationDto(
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

    private fun setResponseHeadersForFile(responseHeaders: HttpHeaders, inode: Inode1<*>) {
        val lastModified = inode.inode0.lastModified
        if (lastModified != null) {
            val milliseconds = lastModified.toMillis()
            responseHeaders.eTag = """"$milliseconds""""
            responseHeaders[ApplicationCtx.Header.lastModifiedMilliseconds] = milliseconds.toString()
        }
        CommonUtil.setContentTypeAsString(responseHeaders, CommonUtil.appendCharset(inode.inode0.mimeType))
        try {
            responseHeaders.contentDisposition = ContentDisposition.builder("inline")
                .filename(CommonUtil.stringToUri(inode.inode0.path.name))
                .build()
        } catch (_: IllegalArgumentException) {
            // Thrown if filename is empty or just contains whitespaces.
        }
    }
}
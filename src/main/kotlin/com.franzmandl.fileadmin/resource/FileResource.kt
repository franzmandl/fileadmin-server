package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.dto.*
import com.franzmandl.fileadmin.dto.config.CommandId
import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.vfs.Inode1
import com.franzmandl.fileadmin.vfs.SafePath
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.util.*

@Controller
class FileResource(
    @Autowired private val applicationCtx: ApplicationCtx
) : BaseResource() {
    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.command], method = [RequestMethod.POST])
    @ResponseBody
    fun applyCommand(
        @RequestBody commandAsString: String,
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
    ): ResponseEntity<String> {
        val requestCtx = applicationCtx.createRequestCtx(now)
        synchronized(this) {
            return when (val command: Command = JsonFormat.decodeFromString(commandAsString)) {
                is Add -> CommonUtil.createJsonResponseEntity(FileHelper.applyAdd(requestCtx, command))
                is Delete -> {
                    FileHelper.applyDelete(requestCtx, command)
                    ResponseEntity("", HttpHeaders(), HttpStatus.OK)
                }

                is Move -> CommonUtil.createJsonResponseEntity(FileHelper.applyMove(requestCtx, command))
                is Rename -> CommonUtil.createJsonResponseEntity(FileHelper.applyRename(requestCtx, command))
                is Share -> ResponseEntity(FileHelper.applyShare(requestCtx, command), CommonUtil.createJsonHttpHeaders(), HttpStatus.OK)
                is ToDirectory -> CommonUtil.createJsonResponseEntity(FileHelper.applyToDirectory(requestCtx, command))
                is ToFile -> CommonUtil.createJsonResponseEntity(FileHelper.applyToFile(requestCtx, command))
            }
        }
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.directory], method = [RequestMethod.GET])
    @ResponseBody
    fun getDirectory(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam path: SafePath,
    ): ResponseEntity<String> {
        val requestCtx = applicationCtx.createRequestCtx(now)
        val inode = requestCtx.getInode(path)
        inode.config.filter?.ctx?.scanItems(requestCtx, CommandId.GetDirectory, ErrorHandler.noop)
        val children = if (inode.inode0.isFile) {
            listOf(FileHelper.getInodeDto(requestCtx, null, inode))
        } else if (inode.inode0.contentPermission.canDirectoryGet) {
            getChildrenOfDirectory(requestCtx, inode, limit, offset)
        } else {
            throw HttpException.badRequest("Path not found.")
        }
        return CommonUtil.createJsonResponseEntity(
            Directory(
                canSearch = inode.config.filter?.ctx?.output?.let { path.startsWith(it) } ?: false,
                children = children,
                errors = inode.config.errors,
                inode = FileHelper.getInodeDto(requestCtx, null, inode),
                nameCursorPosition = inode.config.nameCursorPosition,
                newInodeTemplate = inode.config.newInodeTemplate,
            )
        )
    }

    private fun getChildrenOfDirectory(requestCtx: RequestCtx, inode: Inode1<*>, limit: Int?, offset: Int?): List<InodeDto> {
        val isPartial = offset != null || limit != null
        val children = if (isPartial) ArrayList<InodeDto>() else LinkedList()
        CommonUtil.getSequenceOfChildren(requestCtx, inode).mapTo(children) { FileHelper.getInodeDto(requestCtx, inode, it) }
        if (isPartial) {
            children.sortBy { it.path }
            if (offset != null && offset in 1..children.size) {
                children.subList(0, offset).clear()
            }
            if (limit != null && limit in 0..children.lastIndex) {
                children.subList(limit, children.size).clear()
            }
        }
        return children
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.suggestion], method = [RequestMethod.GET])
    @ResponseBody
    fun getSuggestion(
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
        @RequestParam path: SafePath,
        @RequestParam word: String,
    ): ResponseEntity<String> {
        val requestCtx = applicationCtx.createRequestCtx(now)
        val inode = requestCtx.getInode(path)
        inode.config.filter?.ctx?.scanItems(requestCtx, CommandId.GetSuggestion, ErrorHandler.noop)
        val comparableName = FilterFileSystem.toComparableName(word)
        val suggestions = mutableSetOf<String>()
        val wordLength = word.length
        for (tag in inode.config.filter?.ctx?.registry?.tags?.values ?: listOf()) {
            if (tag.suggestMinimumLength <= wordLength && tag.comparableName.contains(comparableName)) {
                suggestions += tag.name
                tag.suggestName?.let { suggestions += it }
            }
        }
        return CommonUtil.createJsonResponseEntity(suggestions)
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.inode], method = [RequestMethod.GET])
    @ResponseBody
    fun getInode(
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
        @RequestParam path: SafePath,
    ): ResponseEntity<String> {
        val requestCtx = applicationCtx.createRequestCtx(now)
        val inode = requestCtx.getInode(path)
        inode.config.filter?.ctx?.scanItems(requestCtx, CommandId.GetInode, ErrorHandler.noop)
        return CommonUtil.createJsonResponseEntity(FileHelper.getInodeDto(requestCtx, null, inode))
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.file], method = [RequestMethod.GET])
    @ResponseBody
    fun getFile(
        @RequestParam path: SafePath,
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
    ): ResponseEntity<ByteArray> {
        val responseHeaders = HttpHeaders()
        return ResponseEntity(FileHelper.getFile(applicationCtx.createRequestCtx(now), path, responseHeaders), responseHeaders, HttpStatus.OK)
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.file], method = [RequestMethod.POST])
    @ResponseBody
    fun postFile(
        @RequestParam path: SafePath,
        @RequestHeader(value = ApplicationCtx.Header.lastModifiedMilliseconds, required = false) expectedLastModified: Long?,
        @RequestParam content: MultipartFile,
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
    ): ResponseEntity<String> =
        CommonUtil.createJsonResponseEntity(content.inputStream.use { inputStream ->
            FileHelper.setFile(applicationCtx.createRequestCtx(now), path, expectedLastModified, inputStream)
        })

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.file], method = [RequestMethod.PUT])
    @ResponseBody
    fun putFile(
        @RequestParam path: SafePath,
        @RequestHeader(value = ApplicationCtx.Header.lastModifiedMilliseconds, required = false) expectedLastModified: Long?,
        @RequestBody(required = false) payload: String?,
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
    ): ResponseEntity<String> =
        CommonUtil.createJsonResponseEntity(payload.orEmpty().byteInputStream().use { inputStream ->
            FileHelper.setFile(applicationCtx.createRequestCtx(now), path, expectedLastModified, inputStream)
        })

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.fileConvertImageToImage], method = [RequestMethod.GET])
    @ResponseBody
    fun getFileConvertImageToImage(
        @RequestParam path: SafePath,
        @RequestParam maxDimension: Int,
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
    ): ResponseEntity<ByteArray> {
        val responseHeaders = HttpHeaders()
        return ResponseEntity(FileHelper.getResizedImage(applicationCtx.createRequestCtx(now), path, maxDimension, responseHeaders), responseHeaders, HttpStatus.OK)
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.fileConvertPdfToImage], method = [RequestMethod.GET])
    @ResponseBody
    fun getFileConvertPdfToImage(
        @RequestParam path: SafePath,
        @RequestParam maxDimension: Float,
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
    ): ResponseEntity<ByteArray> {
        val responseHeaders = HttpHeaders()
        return ResponseEntity(FileHelper.getImageOfPdf(applicationCtx.createRequestCtx(now), path, 0, maxDimension, responseHeaders), responseHeaders, HttpStatus.OK)
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.fileStream], method = [RequestMethod.GET])
    @ResponseBody
    fun getFileStream(
        @RequestHeader requestHeaders: HttpHeaders,
        @RequestParam path: SafePath,
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
    ): ResponseEntity<ResourceRegion> =
        FileHelper.getFileStream(applicationCtx.createRequestCtx(now), path, requestHeaders)

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.scanItems], method = [RequestMethod.GET])
    @ResponseBody
    fun scanItems(
        @RequestParam path: SafePath,
        @RequestParam(required = false) @IsoDateFormat now: LocalDate?,
    ): ResponseEntity<String> {
        val requestCtx = applicationCtx.createRequestCtx(now)
        val inode = requestCtx.getInode(path)
        return CommonUtil.createJsonResponseEntity(
            inode.config.filter?.ctx?.scanItems(requestCtx, CommandId.ForceScanItems, ErrorHandler.noop)
                ?.let { FileHelper.getInodeDto(requestCtx, null, inode) })
    }
}
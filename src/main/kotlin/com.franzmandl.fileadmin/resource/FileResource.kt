package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.model.*
import com.franzmandl.fileadmin.vfs.SafePath
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@Controller
class FileResource(
    @Autowired private val applicationCtx: ApplicationCtx
) : BaseResource() {
    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.command], method = [RequestMethod.POST])
    @ResponseBody
    fun applyCommand(
        @RequestBody commandAsString: String,
    ): ResponseEntity<String> {
        val requestCtx = applicationCtx.createRequestCtx()
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

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.directory], method = [RequestMethod.GET])
    @ResponseBody
    fun getDirectory(
        @RequestParam path: SafePath,
    ): ResponseEntity<String> {
        val requestCtx = applicationCtx.createRequestCtx()
        val inode = requestCtx.getInode(path)
        val inodes = LinkedList<InodeModel>()
        if (inode.isFile) {
            inodes.add(FileHelper.getInodeModel(requestCtx, inode))
        } else if (inode.contentPermission.canDirectoryGet) {
            CommonUtil.getSequenceOfChildren(requestCtx, inode).mapTo(inodes) { FileHelper.getInodeModel(requestCtx, it) }
        } else {
            throw HttpException.badRequest("Path not found.")
        }
        return CommonUtil.createJsonResponseEntity(
            Directory(
                canSearch = inode.config.filter?.ctx?.output?.let { path.startsWith(it) } ?: false,
                inode.config.errors,
                FileHelper.getInodeModel(requestCtx, inode),
                inodes,
                inode.config.nameCursorPosition,
                inode.config.newInodeTemplate,
            )
        )
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.suggestion], method = [RequestMethod.GET])
    @ResponseBody
    fun getSuggestion(
        @RequestParam path: SafePath,
        @RequestParam word: String,
    ): ResponseEntity<String> {
        val requestCtx = applicationCtx.createRequestCtx()
        val inode = requestCtx.getInode(path)
        val comparableName = FilterFileSystem.toComparableName(word)
        val suggestions = mutableSetOf<String>()
        inode.config.filter?.ctx?.registry?.tags?.mapNotNullTo(suggestions) {
            if (it.value.comparableName.contains(comparableName)) it.value.name else null
        }
        return CommonUtil.createJsonResponseEntity(suggestions)
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.inode], method = [RequestMethod.GET])
    @ResponseBody
    fun getInode(
        @RequestParam path: SafePath,
    ): ResponseEntity<String> {
        val requestCtx = applicationCtx.createRequestCtx()
        return CommonUtil.createJsonResponseEntity(
            FileHelper.getInodeModel(requestCtx, requestCtx.getInode(path))
        )
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.thumbnail], method = [RequestMethod.GET])
    @ResponseBody
    fun getThumbnail(
        @RequestParam path: SafePath,
        @RequestParam maxDimension: Int,
    ): ResponseEntity<ByteArray> {
        val responseHeaders = HttpHeaders()
        return ResponseEntity(FileHelper.getThumbnail(applicationCtx.createRequestCtx(), path, maxDimension, responseHeaders), responseHeaders, HttpStatus.OK)
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.file], method = [RequestMethod.GET])
    @ResponseBody
    fun getFile(
        @RequestParam path: SafePath,
    ): ResponseEntity<ByteArray> {
        val responseHeaders = HttpHeaders()
        return ResponseEntity(FileHelper.getFile(applicationCtx.createRequestCtx(), path, responseHeaders), responseHeaders, HttpStatus.OK)
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.fileStream], method = [RequestMethod.GET])
    @ResponseBody
    fun getFileStream(
        @RequestHeader requestHeaders: HttpHeaders,
        @RequestParam path: SafePath,
    ): ResponseEntity<ResourceRegion> =
        FileHelper.getFileStream(applicationCtx.createRequestCtx(), path, requestHeaders)

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.file], method = [RequestMethod.POST])
    @ResponseBody
    fun postFile(
        @RequestParam path: SafePath,
        @RequestHeader(value = ApplicationCtx.Header.lastModified, required = false) expectedLastModified: Long?,
        @RequestParam content: MultipartFile,
    ): ResponseEntity<String> =
        CommonUtil.createJsonResponseEntity(content.inputStream.use { inputStream ->
            FileHelper.setFile(applicationCtx.createRequestCtx(), path, expectedLastModified, inputStream)
        })

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Authenticated.file], method = [RequestMethod.PUT])
    @ResponseBody
    fun putFile(
        @RequestParam path: SafePath,
        @RequestHeader(value = ApplicationCtx.Header.lastModified, required = false) expectedLastModified: Long?,
        @RequestBody(required = false) optionalPayload: String?,
    ): ResponseEntity<String> =
        CommonUtil.createJsonResponseEntity(optionalPayload.orEmpty().byteInputStream().use { inputStream ->
            FileHelper.setFile(applicationCtx.createRequestCtx(), path, expectedLastModified, inputStream)
        })
}
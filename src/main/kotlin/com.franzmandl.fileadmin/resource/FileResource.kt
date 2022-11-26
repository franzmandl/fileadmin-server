package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.Config
import com.franzmandl.fileadmin.model.Directory
import com.franzmandl.fileadmin.model.Inode
import com.franzmandl.fileadmin.service.FileService
import com.franzmandl.fileadmin.ticket.TicketUtil
import com.franzmandl.fileadmin.util.JsonFormat
import com.franzmandl.fileadmin.util.ProcessResult
import com.franzmandl.fileadmin.util.Util
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import java.time.LocalDate
import javax.imageio.ImageIO
import javax.servlet.http.HttpServletResponse


@Controller
class FileResource(
    @Autowired private val config: Config
) : BaseResource() {
    private val fileService = FileService(config.paths.jail)

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.add], method = [RequestMethod.POST])
    @ResponseBody
    fun add(
        @RequestParam(value = "path") relativePath: String,
        @RequestParam(value = "basename") basename: String,
        @RequestParam(value = "isFile") isFile: Boolean
    ): ResponseEntity<String> {
        val now = LocalDate.now()
        val file = fileService.getFile("$relativePath/$basename")
        if (file.exists()) {
            throw HttpException(HttpServletResponse.SC_BAD_REQUEST, "Inode already exists.")
        }
        checkWriteable(file.parentFile)
        val result = if (isFile) {
            file.createNewFile()
        } else {
            file.mkdir()
        }
        if (!result) {
            throw HttpException(HttpServletResponse.SC_BAD_REQUEST, "Could not create inode.")
        }
        return createInodeResponseEntity(file, now)
    }

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.remove], method = [RequestMethod.GET])
    @ResponseBody
    fun remove(
        @RequestParam(value = "path") relativePath: String
    ): String {
        val file = fileService.getFile(relativePath)
        if (!file.exists()) {
            throw HttpException(HttpServletResponse.SC_BAD_REQUEST, "Inode does not exist.")
        }
        checkWriteable(file.parentFile)
        if (!file.delete()) {
            throw HttpException(
                HttpServletResponse.SC_BAD_REQUEST,
                "Operation not successful. In case of a directory make sure it is empty."
            )
        }
        return ""
    }

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.move], method = [RequestMethod.POST])
    @ResponseBody
    fun move(
        @RequestParam(value = "path") relativePath: String,
        @RequestParam(value = "newPath") newRelativePath: String
    ): ResponseEntity<String> {
        val now = LocalDate.now()
        val oldFile = fileService.getFile(relativePath)
        val newFile = fileService.getFile(newRelativePath)
        if (newFile.exists()) {
            throw HttpException(HttpServletResponse.SC_BAD_REQUEST, "Destination already exists.")
        }
        checkWriteable(oldFile.parentFile, "Insufficient permissions in source.")
        checkWriteable(newFile.parentFile, "Insufficient permissions in destination.")
        if (!oldFile.renameTo(newFile)) {
            throw HttpException(HttpServletResponse.SC_BAD_REQUEST, "Could not rename inode.")
        }
        return createInodeResponseEntity(newFile, now)
    }

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.directory], method = [RequestMethod.GET])
    @ResponseBody
    fun getDirectory(
        @RequestParam(value = "path") relativePath: String
    ): ResponseEntity<String> {
        val now = LocalDate.now()
        val files = fileService.glob(relativePath)
        val directory = fileService.getFile(relativePath)
        if (files.isEmpty() && !directory.exists()) {
            throw HttpException(HttpServletResponse.SC_NOT_FOUND, "Path not found.")
        }
        // WARNING: relativePath might contain a wildcard *.
        return createJsonResponseEntity(Directory.create(config, files, directory, now))
    }

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.inode], method = [RequestMethod.GET])
    @ResponseBody
    fun getInode(
        @RequestParam(value = "path") relativePath: String
    ): ResponseEntity<String> {
        val now = LocalDate.now()
        val file = fileService.getFile(relativePath)
        return createInodeResponseEntity(file, now)
    }

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.thumbnail], method = [RequestMethod.GET])
    @ResponseBody
    fun getThumbnail(
        @RequestParam(value = "path") relativePath: String,
        @RequestParam(value = "maxDimension") maxDimension: Int
    ): ResponseEntity<ByteArray> {
        val file = fileService.getExistingFile(relativePath)
        val responseHeaders = HttpHeaders()
        FileService.setResponseHeadersForFile(responseHeaders, file)
        fun respondOriginal(): ResponseEntity<ByteArray> {
            // Sets the same as responseHeaders.contentType
            responseHeaders["Content-Type"] = Util.getContentType(file.toPath())
            return try {
                ResponseEntity(file.readBytes(), responseHeaders, HttpStatus.OK)
            } catch (e: Exception) {
                throw HttpException(HttpServletResponse.SC_BAD_REQUEST, "Illegal image.")
            }
        }

        val originalImage = try {
            ImageIO.read(file)!!
        } catch (e: Exception) {
            return respondOriginal()
        }
        var width = originalImage.width
        var height = originalImage.height
        if (width <= maxDimension && height <= maxDimension) {
            return respondOriginal()
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
        return ResponseEntity(buffer.toByteArray(), responseHeaders, HttpStatus.OK)
    }

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.file], method = [RequestMethod.GET])
    @ResponseBody
    fun getFile(
        @RequestHeader requestHeaders: HttpHeaders,
        @RequestParam(value = "path") relativePath: String
    ): ResponseEntity<ResourceRegion> {
        return fileService.respondFile(relativePath, requestHeaders)
    }

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.file], method = [RequestMethod.POST])
    @ResponseBody
    fun postFile(
        @RequestParam(value = "path") relativePath: String,
        @RequestHeader(value = Config.Header.lastModified) expectedLastModified: Long,
        @RequestParam(value = "content") content: MultipartFile
    ): ResponseEntity<String> {
        val now = LocalDate.now()
        val file = fileService.getExistingFile(relativePath)
        checkLastModified(file, expectedLastModified)
        checkWriteable(file)
        // See https://www.baeldung.com/spring-multipartfile-to-file
        // content.transferTo(file)  // Has problems with relative paths
        content.inputStream.use { inputStream ->
            file.outputStream().use { outputStream -> inputStream.transferTo(outputStream) }
        }
        return createInodeResponseEntity(file, now)
    }

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.file], method = [RequestMethod.PUT])
    @ResponseBody
    fun putFile(
        @RequestParam(value = "path") relativePath: String,
        @RequestHeader(value = Config.Header.lastModified) expectedLastModified: Long,
        @RequestBody(required = false) optionalPayload: String?
    ): ResponseEntity<String> {
        val now = LocalDate.now()
        val file = fileService.getExistingFile(relativePath)
        checkLastModified(file, expectedLastModified)
        checkWriteable(file)
        PrintWriter(file).use { out -> out.print(optionalPayload.orEmpty()) }
        return createInodeResponseEntity(file, now)
    }

    @RequestMapping(value = [Config.RequestMappingPaths.Authenticated.share], method = [RequestMethod.GET])
    @ResponseBody
    fun shareFile(
        @RequestParam(value = "path") relativePath: String,
        @RequestParam(value = "days") days: String,
    ): ResponseEntity<String> {
        val sourceFile = fileService.getExistingFile(relativePath)
        val processResult =
            ProcessResult.create(
                ProcessBuilder(
                    config.binaries.share,
                    "--days",
                    days,
                    "--format",
                    "json",
                    sourceFile.path
                ).start()
            )
                ?: throw HttpException(HttpServletResponse.SC_BAD_REQUEST, "Process Timeout.")
        if (processResult.exitValue != 0 || processResult.stderr.isNotEmpty()) {
            throw HttpException(HttpServletResponse.SC_BAD_REQUEST, processResult.toString())
        }
        return ResponseEntity(processResult.stdout, createJsonHttpHeaders(), HttpStatus.OK)
    }

    private fun createInodeResponseEntity(file: File, now: LocalDate) =
        createJsonResponseEntity(
            Inode.create(
                config,
                file,
                TicketUtil.createTicketObjects(config, file.parentFile, now)
            )
        )

    private fun createJsonHttpHeaders(): HttpHeaders {
        val responseHeaders = HttpHeaders()
        responseHeaders.contentType = MediaType.APPLICATION_JSON
        return responseHeaders
    }

    private inline fun <reified T> createJsonResponseEntity(value: T) =
        ResponseEntity(JsonFormat.encodeToString(value), createJsonHttpHeaders(), HttpStatus.OK)

    private fun checkLastModified(file: File, expectedLastModified: Long) {
        val actualLastModified = file.lastModified()
        if (expectedLastModified != actualLastModified) {
            throw HttpException(
                HttpServletResponse.SC_BAD_REQUEST, "File has been modified: "
                        + "Yours=" + Util.yyyy_MM_dd_HH_mm_ss_SSS_format.format(
                    Util.convertToZonedDateTime(
                        expectedLastModified
                    )
                )
                        + " != "
                        + "Theirs=" + Util.yyyy_MM_dd_HH_mm_ss_SSS_format.format(
                    Util.convertToZonedDateTime(
                        actualLastModified
                    )
                )
            )
        }
    }

    private fun checkWriteable(file: File, message: String = "Insufficient permissions.") {
        if (!file.canWrite()) {
            throw HttpException(HttpServletResponse.SC_BAD_REQUEST, message)
        }
    }
}
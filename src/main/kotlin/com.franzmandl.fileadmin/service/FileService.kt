package com.franzmandl.fileadmin.service

import com.franzmandl.fileadmin.Config
import com.franzmandl.fileadmin.resource.HttpException
import com.franzmandl.fileadmin.util.Util
import org.springframework.core.io.UrlResource
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.servlet.http.HttpServletResponse
import kotlin.math.min


class FileService(
    private val jail: String
) {
    private fun getPathname(relativePath: String?): String {
        return jail + Paths.get(relativePath.orEmpty()).normalize()
    }

    fun getPath(relativePath: String?): Path {
        return Paths.get(getPathname(relativePath))
    }

    fun getUrl(relativePath: String?): String {
        return "file:" + Util.encodePath(getPathname(relativePath))
    }

    fun getUrlResource(relativePath: String?): UrlResource {
        return UrlResource(getUrl(relativePath))
    }

    fun getFile(relativePath: String?): File {
        return File(getPathname(relativePath))
    }

    fun getExistingFile(relativePath: String?): File {
        val result = getFile(relativePath)
        if (!result.exists()) {
            throw HttpException(HttpServletResponse.SC_NOT_FOUND, "Path '${relativePath}' does not exist.")
        }
        return result
    }

    private fun listFiles(relativePath: String): List<File> =
        getExistingFile(relativePath).listFiles()?.filter {
            it.exists()
        } ?: listOf()  // listFiles() returns null if insufficient permissions.

    fun glob(relativePath: String, result: LinkedList<File> = LinkedList()): List<File> {
        val wildcardIndex = relativePath.indexOf('*')
        if (wildcardIndex == -1) {
            val file = getFile(relativePath)
            if (file.isDirectory) {
                result.addAll(listFiles(relativePath))
            } else if (file.exists()) {
                // file is just an ordinary file
                result.add(file)
            }
        } else {
            val beforeWildcard = relativePath.substring(0, wildcardIndex)
            val afterWildcard = relativePath.substring(wildcardIndex + 1)
            listFiles(beforeWildcard).forEach {
                if (it.isDirectory) {
                    glob(beforeWildcard + it.name + afterWildcard, result)
                }
            }
        }
        return result
    }

    fun respondFile(relativePath: String?, requestHeaders: HttpHeaders): ResponseEntity<ResourceRegion> {
        val file = getExistingFile(relativePath)
        val responseHeaders = HttpHeaders()
        setResponseHeadersForFile(responseHeaders, file)
        responseHeaders["Content-Type"] = Util.getContentType(file.toPath())
        responseHeaders["Accept-Ranges"] = "bytes"
        // See https://melgenek.github.io/spring-video-service
        // See https://github.com/melgenek/spring-video-service
        val resource = getUrlResource(relativePath)
        val ranges = requestHeaders.range
        val contentLength = resource.contentLength()
        return if (ranges.size > 0) {
            val range = ranges[0]
            val position = range.getRangeStart(contentLength)
            val end = range.getRangeEnd(contentLength)
            val count = min(10 * 1024 * 1024.toLong(), end - position + 1)
            ResponseEntity(ResourceRegion(resource, position, count), responseHeaders, HttpStatus.PARTIAL_CONTENT)
        } else {
            ResponseEntity(ResourceRegion(resource, 0, contentLength), responseHeaders, HttpStatus.OK)
        }
    }

    companion object {
        fun setResponseHeadersForFile(responseHeaders: HttpHeaders, file: File) {
            responseHeaders.eTag = "\"" + file.lastModified() + "\""
            responseHeaders[Config.Header.lastModified] = file.lastModified().toString()
            responseHeaders.contentDisposition = ContentDisposition.builder("inline")
                .filename(file.name)
                .build()
        }
    }
}

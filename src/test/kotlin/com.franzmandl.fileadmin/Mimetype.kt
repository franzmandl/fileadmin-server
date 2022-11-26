package com.franzmandl.fileadmin

import org.apache.tika.Tika
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Paths
import javax.activation.MimetypesFileTypeMap


class Mimetype {
    private fun usingMimeTypesFileTypeMap(pathname: String): String {
        val file = File(pathname)
        val fileTypeMap = MimetypesFileTypeMap()
        return fileTypeMap.getContentType(file.name)
    }

    private fun usingGetFileNameMap(pathname: String): String {
        val file = File(pathname)
        val fileNameMap = URLConnection.getFileNameMap()
        return fileNameMap.getContentTypeFor(file.name)
    }

    private fun usingGetContentType(pathname: String): String {
        val file = File(pathname)
        val connection = file.toURI().toURL().openConnection()
        return connection.contentType
    }

    private fun usingTika(pathname: String): String {
        val file = File(pathname)
        val tika = Tika()
        return tika.detect(file)
    }

    @Disabled
    @Test
    fun usingMimeTypesFileTypeMap() {
        for ((filename, mimetype) in map) {
            assertThat(usingMimeTypesFileTypeMap(filename)).withFailMessage(filename).isEqualTo(mimetype)
        }
    }

    @Disabled
    @Test
    fun usingGetFileNameMap() {
        for ((filename, mimetype) in map) {
            assertThat(usingGetFileNameMap(filename)).withFailMessage(filename).isEqualTo(mimetype)
        }
    }

    @Disabled
    @Test
    fun usingGetContentType() {
        for ((filename, mimetype) in map) {
            assertThat(usingGetContentType(filename)).withFailMessage(filename).isEqualTo(mimetype)
        }
    }

    @Disabled
    @Test
    fun usingTika() {
        for ((filename, mimetype) in map) {
            assertThat(usingTika(filename)).withFailMessage(filename).isEqualTo(mimetype)
        }
    }

    @Test
    fun usingFilesProbeContentType() {
        for ((filename, mimetype) in map) {
            assertThat(usingFilesProbeContentType(filename)).withFailMessage(filename).isEqualTo(mimetype)
        }
    }

    companion object {
        private val map: Map<String, String> = mapOf(
            "./src/test/resources/mimetype/blue.jpg" to "image/jpeg",
            "./src/test/resources/mimetype/blue.png" to "image/png",
            "./src/test/resources/mimetype/empty.txt" to "text/plain",
            "./src/test/resources/mimetype/#blue.jpg" to "image/jpeg",
            "./src/test/resources/mimetype/#blue.png" to "image/png",
            "./src/test/resources/mimetype/#empty.txt" to "text/plain",
            "./src/test/resources/mimetype/2020 - #blue.jpg" to "image/jpeg",
            "./src/test/resources/mimetype/2020 - #blue.png" to "image/png",
            "./src/test/resources/mimetype/2020 - #empty.txt" to "text/plain"
        )

        fun usingFilesProbeContentType(pathname: String): String {
            return Files.probeContentType(Paths.get(pathname))
        }
    }
}

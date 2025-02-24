package com.franzmandl.fileadmin

import jakarta.activation.MimetypesFileTypeMap
import org.apache.tika.Tika
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class MimeTypeTest {
    @Disabled
    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testUsingMimeTypesFileTypeMap(path: Path, expected: String?) {
        val fileTypeMap = MimetypesFileTypeMap()
        assertThat(fileTypeMap.getContentType(path.fileName.toString())).isEqualTo(expected)
    }

    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testUsingGetFileNameMap(path: Path, expected: String?) {
        val fileNameMap = URLConnection.getFileNameMap()
        assertThat(fileNameMap.getContentTypeFor(path.fileName.toString())).isEqualTo(expected)
    }

    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testUsingGetContentType(path: Path, expected: String?) {
        val connection = path.toFile().toURI().toURL().openConnection()
        assertThat(connection.contentType).isEqualTo(expected)
    }

    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testUsingTika(path: Path, expected: String?) {
        val tika = Tika()
        assertThat(tika.detect(path)).isEqualTo(expected)
    }

    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testUsingFilesProbeContentType(path: Path, expected: String?) {
        assertThat(Files.probeContentType(path)).isEqualTo(expected)
    }

    companion object {
        private val pathMap: Map<String, String?> = mapOf(
            "./src/test/data/mimetype" to null,
            "./src/test/data/mimetype/blue.jpg" to "image/jpeg",
            "./src/test/data/mimetype/blue.png" to "image/png",
            "./src/test/data/mimetype/empty.txt" to "text/plain",
            "./src/test/data/mimetype/#blue.jpg" to "image/jpeg",
            "./src/test/data/mimetype/#blue.png" to "image/png",
            "./src/test/data/mimetype/#empty.txt" to "text/plain",
            "./src/test/data/mimetype/2020 - #blue.jpg" to "image/jpeg",
            "./src/test/data/mimetype/2020 - #blue.png" to "image/png",
            "./src/test/data/mimetype/2020 - #empty.txt" to "text/plain"
        )

        @JvmStatic
        fun testUsingMimeTypesFileTypeMap(): Stream<Arguments> =
            getStreamOfArguments(pathMap)

        @JvmStatic
        fun testUsingGetFileNameMap(): Stream<Arguments> =
            getStreamOfArguments(
                pathMap + mapOf(
                    "./src/test/data/mimetype/2020 - #blue.jpg" to null,
                    "./src/test/data/mimetype/2020 - #blue.png" to null,
                    "./src/test/data/mimetype/2020 - #empty.txt" to null,
                )
            )

        @JvmStatic
        fun testUsingGetContentType(): Stream<Arguments> =
            getStreamOfArguments(
                pathMap + mapOf(
                    "./src/test/data/mimetype" to "text/plain",
                )
            )

        @JvmStatic
        fun testUsingTika(): Stream<Arguments> =
            getStreamOfArguments(
                pathMap - "./src/test/data/mimetype" + mapOf(
                    "./src/test/data/mimetype/#empty.txt" to "application/octet-stream",
                )
            )

        @JvmStatic
        fun testUsingFilesProbeContentType(): Stream<Arguments> =
            getStreamOfArguments(pathMap)

        private fun getStreamOfArguments(map: Map<String, String?>): Stream<Arguments> =
            map.entries.stream().map { (path, expected) -> Arguments.of(Path.of(path), expected) }
    }
}
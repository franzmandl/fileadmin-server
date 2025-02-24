package com.franzmandl.fileadmin

import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.dto.*
import com.franzmandl.fileadmin.resource.FileResource
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting

@SpringBootTest
@ActiveProfiles("test", "jail3")
class SystemTest(
    @Autowired private val fileResource: FileResource,
    @Value("\${application.jail.path}") private val jail: String,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun beforeEach() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build()
    }

    @Test
    fun testRootDirectory() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
    }

    @Test
    fun testRootPartialDirectory() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/&offset=3&limit=2"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(2)
        assertThat(directory.children[0].path).isEqualTo(SafePath("/.fileadmin.json"))
        assertThat(directory.children[1].path).isEqualTo(SafePath("/.gitignore"))
    }

    @Test
    fun testTaskFilterBuiltin() {
        // Broken symbolic links need to be created before the test because otherwise it fails to build.
        val path = SafePath("/franz/project/40-to_do/builtin(filter 2020-01-02 {target})R - 40-dummy")
        val virtualSymbolicLink = Path.of("$jail$path")
        try {
            // When
            Files.createSymbolicLink(virtualSymbolicLink, Path.of("../../../filterJail4/example1/tags"));
            val result =
                mockMvc.perform(get(URI("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=${TestUtil.pathToUri(path.forceParent())}")))
                    .andExpect(status().isOk)
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn()
            val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
            // Then
            assertThat(directory.errors).isEmpty()
            val inode = directory.children.first { it.path == path }
            assertThat(inode.errors).isEmpty()
            assertThat(inode.task).isNotNull
            assertThat(inode.friendlyName).isEqualTo("2020-01-02 - 40-dummy")
        } finally {
            virtualSymbolicLink.deleteExisting()
        }
    }

    @Test
    fun testImport01() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/import01"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
    }

    @Test
    fun testToDirectoryAndToFile() {
        /// ToDirectory
        // When
        val toDirectoryResult =
            mockMvc.perform(
                post("${ApplicationCtx.RequestMappingPaths.authenticated}/command")
                    .content(JsonFormat.encodeToString<Command>(ToDirectory(SafePath("/readme.txt"))))
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<InodeDto>(toDirectoryResult.response.contentAsString)
        // Then
        assertThat(directory.isDirectory).isTrue
        assertThat(directory.path.name).isEqualTo("readme")
        /// ToFile
        // When
        val toFileResult =
            mockMvc.perform(
                post("${ApplicationCtx.RequestMappingPaths.authenticated}/command")
                    .content(JsonFormat.encodeToString<Command>(ToFile(SafePath("/readme"))))
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val file = JsonFormat.decodeFromString<InodeDto>(toFileResult.response.contentAsString)
        // Then
        assertThat(file.isFile).isTrue
        assertThat(file.path.name).isEqualTo("readme.txt")
    }

    @Test
    fun testGetAndPutReadmeTxt() {
        val lastModifiedMillisecondsName = "x-last-modified-milliseconds"
        /// Get
        val getResult =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file?path=/readme.txt"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("text/plain;charset=utf-8"))
                .andReturn()
        val content = getResult.response.contentAsString
        val lastModified = getResult.response.getHeaderValue(lastModifiedMillisecondsName)
        /// Put
        val putResult =
            mockMvc.perform(
                put("${ApplicationCtx.RequestMappingPaths.authenticated}/file?path=/readme.txt")
                    .header(lastModifiedMillisecondsName, lastModified)
                    .content(content)
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val file = JsonFormat.decodeFromString<InodeDto>(putResult.response.contentAsString)
        assertThat(file.isFile).isTrue
        assertThat(file.path.name).isEqualTo("readme.txt")
    }

    @Test
    fun testGetAndPutEmpty01Txt() {
        val lastModifiedMillisecondsName = "x-last-modified-milliseconds"
        /// Get
        val getResult =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file?path=/empty01.txt"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("text/plain;charset=utf-8"))
                .andReturn()
        val content = getResult.response.contentAsString
        val lastModified = getResult.response.getHeaderValue(lastModifiedMillisecondsName)
        /// Put
        val putResult =
            mockMvc.perform(
                put("${ApplicationCtx.RequestMappingPaths.authenticated}/file?path=/empty01.txt")
                    .header(lastModifiedMillisecondsName, lastModified)
                    .content(content)
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val file = JsonFormat.decodeFromString<InodeDto>(putResult.response.contentAsString)
        assertThat(file.isFile).isTrue
        assertThat(file.path.name).isEqualTo("empty01.txt")
    }
}
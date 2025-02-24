package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.Directory
import com.franzmandl.fileadmin.dto.InodeDto
import com.franzmandl.fileadmin.resource.FileResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.name
import kotlin.io.path.readText

@SpringBootTest
@ActiveProfiles("test", "jail2")
class FileResourceNativeIT(
    @Autowired private val fileResource: FileResource,
    @Value("\${application.jail.path}") private val jail: String,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun beforeEach() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build()
    }

    @Test
    fun testIllegalMapping() {
        mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/asdf"))
            .andExpect(status().isNotFound)
            .andReturn()
    }

    @Test
    fun testRootDirectory() {
        // Broken symbolic links need to be created before the test because otherwise it fails to build.
        val brokenSymbolicLink = Path.of("$jail/brokenSymbolicLink")
        val selfReferenceSymbolicLink = Path.of("$jail/selfReferenceSymbolicLink")
        try {
            // When
            Files.createSymbolicLink(brokenSymbolicLink, Path.of("doesNotExist"));
            Files.createSymbolicLink(selfReferenceSymbolicLink, Path.of(selfReferenceSymbolicLink.name));
            val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
            val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
            // Then
            assertThat(directory.errors).isEmpty()
            assertThat(directory.children).hasSize(13)
        } finally {
            brokenSymbolicLink.deleteExisting()
            selfReferenceSymbolicLink.deleteExisting()
        }
    }

    @Test
    fun testSymlink() {
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/symlink/40-to_do"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(1)
        val inode = directory.children[0]
        assertThat(inode.errors).isEmpty()
        assertThat(inode.path).isEqualTo(SafePath("/date/60-done/2020-01-02 - 60-dummy/40-to_do/0000-00-00 - 40-dummy.txt"))
    }

    @Test
    fun testEscape() {
        val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=.."))
            .andExpect(status().isBadRequest)
            .andReturn()
        assertThat(result.response.contentAsString).isEqualTo("""Illegal path: ".." matches anti pattern.""")
    }

    @Test
    fun testNotExisting() {
        val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/doesNotExist"))
            .andExpect(status().isBadRequest)
            .andReturn()
        assertThat(result.response.contentAsString).isEqualTo("Path not found.")
    }

    @Test
    fun testFileDirectory() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/date/20-backlog/2020-03-04 - 20-dummy.txt"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(1)
        val inode = directory.children[0]
        assertThat(inode.errors).isEmpty()
        assertThat(inode.friendlyName).isNull()
        assertThat(inode.isDirectory).isFalse
        assertThat(inode.isFile).isTrue
        assertThat(inode.link).isNull()
        assertThat(inode.operation.canDirectoryAdd).isFalse
        assertThat(inode.operation.canDirectoryGet).isFalse
        assertThat(inode.operation.canFileGet).isTrue
        assertThat(inode.operation.canFileSet).isTrue
        assertThat(inode.path).isEqualTo(SafePath("/date/20-backlog/2020-03-04 - 20-dummy.txt"))
        assertThat(inode.path.name).isEqualTo("2020-03-04 - 20-dummy.txt")
        assertThat(inode.size).isEqualTo(0)
    }

    @Test
    fun testRootInode() {
        // When
        val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/inode?path=/"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val inode = JsonFormat.decodeFromString<InodeDto>(result.response.contentAsString)
        // Then
        assertThat(inode.errors).isEmpty()
        assertThat(inode.friendlyName).isNull()
        assertThat(inode.isDirectory).isTrue
        assertThat(inode.isFile).isFalse
        assertThat(inode.isRoot).isTrue
        assertThat(inode.link).isNull()
        assertThat(inode.operation.canFileGet).isFalse
        assertThat(inode.operation.canFileSet).isFalse
        assertThat(inode.operation.canDirectoryAdd).isTrue
        assertThat(inode.operation.canDirectoryGet).isTrue
        assertThat(inode.path).isEqualTo(SafePath("/"))
        assertThat(inode.path.name).isEqualTo("")
        assertThat(inode.task).isNull()
    }

    @Test
    fun testExistingFile() {
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file?path=/.fileadmin.json"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        assertThat(result.response.contentAsString).isEqualTo(Path.of("./src/test/data/jail2/.fileadmin.json").readText())
    }

    @Test
    fun testNonExistingFile() {
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file?path=/doesNotExist"))
                .andExpect(status().isBadRequest)
                .andReturn()
        assertThat(result.response.contentAsString).isEqualTo("""Inode does not exist. get file "/doesNotExist"""")
    }

    @Test
    fun testExistingFileStreamWithRange() {
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file/stream?path=/.fileadmin.json").header("Range", "bytes=7-24"))
                .andExpect(status().isPartialContent)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        assertThat(result.response.contentAsString).isEqualTo(Path.of("./src/test/data/jail2/.fileadmin.json").readText().substring(7, 25))
    }

    @Test
    fun testExistingFileStreamWithoutRange() {
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file/stream?path=/.fileadmin.json"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        assertThat(result.response.contentAsString).isEqualTo(Path.of("./src/test/data/jail2/.fileadmin.json").readText())
    }

    @Test
    fun testNonExistingFileStream() {
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file/stream?path=/doesNotExist"))
                .andExpect(status().isBadRequest)
                .andReturn()
        assertThat(result.response.contentAsString).isEqualTo("""Inode does not exist. stream "/doesNotExist"""")
    }

    @Test
    fun testScanItems() {
        val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/scanItems?path=/"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assertThat(result.response.contentAsString).isEqualTo("null")
    }
}
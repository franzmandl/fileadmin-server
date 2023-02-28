package com.franzmandl.fileadmin

import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.model.ApplicationCtx
import com.franzmandl.fileadmin.model.Directory
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting

@SpringBootTest
@ActiveProfiles("test", "jail3")
class IntegrationTests(
    @Autowired private val fileResource: FileResource,
    @Value("\${application.paths.jail}") private val jail: String,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
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
            val inode = directory.inodes.first { it.path == path }
            assertThat(inode.error).isNull()
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
}
package com.franzmandl.fileadmin.filter

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting

@SpringBootTest
@ActiveProfiles("test", "jail4")
class FileResourceFilterChangeIT(
    @Autowired private val fileResource: FileResource,
    @Value("\${application.jail.path}") private val jail: String,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun beforeEach() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build()
    }

    @Test
    fun testTagOutputSymlink() {
        // Broken (virtual target) symbolic links need to be created before the test because otherwise it fails to build.
        val outputSymbolicLink = Path.of("$jail/example1/input1/2022/2022-11-22 - tag output")
        try {
            // When
            Files.createSymbolicLink(outputSymbolicLink, Path.of("../../tags"));
            val result =
                mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags"))
                    .andExpect(status().isOk)
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn()
            val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
            // Then
            assertThat(directory.errors).isEmpty()
            assertThat(directory.children).hasSize(22)
        } finally {
            outputSymbolicLink.deleteExisting()
        }
    }

    @Test
    fun testOnTheFlySuggestion() {
        // Given
        val directory = "/example1/input3"
        val name = "#OnTheFly.txt"
        val file = Path.of("$jail$directory/$name")
        // When
        mockMvc.perform(
            post("${ApplicationCtx.RequestMappingPaths.authenticated}/command")
                .content(JsonFormat.encodeToString<Command>(Add(SafePath(directory), NewInode(true, name))))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assertThat(file).exists()
        try {
            val suggestions = JsonFormat.decodeFromString<List<String>>(
                mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/suggestion?path=/example1/tags&word=OnTheFly"))
                    .andExpect(status().isOk)
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn().response.contentAsString
            )
            // Then
            assertThat(suggestions).containsExactlyInAnyOrder("OnTheFly")
        } finally {
            file.deleteExisting()
        }
    }
}
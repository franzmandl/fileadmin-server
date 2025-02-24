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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@SpringBootTest
@ActiveProfiles("test", "jail2")
class FileResourceGlobIT(
    @Autowired private val fileResource: FileResource,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun beforeEach() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build()
    }

    @Test
    fun testInodeWildcard() {
        // When
        val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/inode?path=/*"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val inode = JsonFormat.decodeFromString<InodeDto>(result.response.contentAsString)
        // Then
        assertThat(inode.errors).isEmpty()
        assertThat(inode.size).isGreaterThan(0)
        assertThat(inode.friendlyName).isNull()
        assertThat(inode.isDirectory).isTrue
        assertThat(inode.isFile).isFalse
        assertThat(inode.isRoot).isFalse
        assertThat(inode.isVirtual).isTrue
        assertThat(inode.link).isNull()
        assertThat(inode.operation.canFileGet).isFalse
        assertThat(inode.operation.canFileSet).isFalse
        assertThat(inode.operation.canDirectoryAdd).isFalse
        assertThat(inode.operation.canDirectoryGet).isTrue
        assertThat(inode.path).isEqualTo(SafePath("/*"))
        assertThat(inode.path.name).isEqualTo("*")
        assertThat(inode.task).isNull()
    }

    @Test
    fun testInodeWildcardChild() {
        // When
        val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/inode?path=/*/40-to_do"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val inode = JsonFormat.decodeFromString<InodeDto>(result.response.contentAsString)
        // Then
        assertThat(inode.errors).isEmpty()
        assertThat(inode.size).isGreaterThan(0)
        assertThat(inode.friendlyName).isNull()
        assertThat(inode.isDirectory).isTrue
        assertThat(inode.isFile).isFalse
        assertThat(inode.isRoot).isFalse
        assertThat(inode.isVirtual).isTrue
        assertThat(inode.link).isNull()
        assertThat(inode.operation.canFileGet).isFalse
        assertThat(inode.operation.canFileSet).isFalse
        assertThat(inode.operation.canDirectoryAdd).isFalse
        assertThat(inode.operation.canDirectoryGet).isTrue
        assertThat(inode.path).isEqualTo(SafePath("/*/40-to_do"))
        assertThat(inode.path.name).isEqualTo("40-to_do")
        assertThat(inode.task).isNull()
    }

    @Test
    fun testDirectoryWildcard() {
        // When
        val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/*"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSizeGreaterThan(0)
    }

    @Test
    fun testDirectoryWildcardChild() {
        // When
        val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/*/40-to_do"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSizeGreaterThan(0)
    }

    @Test
    fun testDirectoryWildcardDoesNotExist() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/*/doesNotExist"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(0)
    }
}
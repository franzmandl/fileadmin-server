package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.model.ApplicationCtx
import com.franzmandl.fileadmin.model.Directory
import com.franzmandl.fileadmin.model.InodeModel
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
class FileResourceGlobTests(
    @Autowired private val fileResource: FileResource,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build()
    }

    @Test
    fun testWildcard() {
        // When
        val result = mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/inode?path=/*"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val inode = JsonFormat.decodeFromString<InodeModel>(result.response.contentAsString)
        // Then
        assertThat(inode.error).isNull()
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
    fun testWildcardDoesNotExist() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/*/doesNotExist"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.inodes).hasSize(0)
    }
}
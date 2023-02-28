package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.model.ApplicationCtx
import com.franzmandl.fileadmin.model.Directory
import com.franzmandl.fileadmin.model.InodeModel
import com.franzmandl.fileadmin.resource.FileResource
import com.franzmandl.fileadmin.vfs.SafePath
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
@ActiveProfiles("test", "jail4")
class FileResourceFilterTests(
    @Autowired private val fileResource: FileResource,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build()
    }

    @Test
    fun testTagOutput() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.inodes).hasSize(15)
    }

    @Test
    fun testSystemUnknownTagsDirectory() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/.fileadmin.system/unknownTags.txt"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.inodes).hasSize(1)
    }

    @Test
    fun testSystemUnknownTagsFile() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file?path=/example1/tags/.fileadmin.system/unknownTags.txt"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(CommonUtil.contentTypeTextPlainUtf8))
                .andReturn()
        val lines = result.response.contentAsString.split("\n").toSet()
        // Then
        assertThat(lines).containsExactly("unknown1", "unknown2", "")
    }

    @Test
    fun testSystemUnknownTagsInode() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/inode?path=/example1/tags/.fileadmin.system/unknownTags.txt"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val inode = JsonFormat.decodeFromString<InodeModel>(result.response.contentAsString)
        // Then
        assertThat(inode.error).isNull()
        assertThat(inode.mimeType).isEqualTo(MediaType.TEXT_PLAIN_VALUE)
        assertThat(inode.lastModified).isNull()
        assertThat(inode.operation.canFileGet).isTrue
        assertThat(inode.isDirectory).isFalse
        assertThat(inode.isFile).isTrue
        assertThat(inode.isVirtual).isTrue
    }

    @Test
    fun testSystemUnusedTagsFile() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file?path=/example1/tags/.fileadmin.system/unusedTags.txt"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(CommonUtil.contentTypeTextPlainUtf8))
                .andReturn()
        val lines = result.response.contentAsString.split("\n").toSet()
        // Then
        assertThat(lines).containsExactly("AntonioElliott", "hardware", "MinaHendricks", "unused1", "unused2", "unused3", "")
    }

    @Test
    fun testTagUnknown() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/_unknown"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.inodes).hasSize(6)
        assertThat(directory.inodes.any { it.operation.canInodeRename }).isTrue
    }

    @Test
    fun testSuggestion() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/suggestion?path=/example1/tags&word=un"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val suggestions = JsonFormat.decodeFromString<List<String>>(result.response.contentAsString)
        // Then
        assertThat(suggestions).containsExactlyInAnyOrder("lostAndFound", "unknown", "unknown1", "unknown2", "unused1", "unused2", "unused3")
    }

    @Test
    fun testOperatorElse() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/person/,else"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.inodes).hasSize(2)
        assertThat(directory.inodes[0].path).isEqualTo(SafePath("/example1/input1/2022/2022-11-22 - #person.txt"))
    }
}
package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.Directory
import com.franzmandl.fileadmin.dto.InodeDto
import com.franzmandl.fileadmin.resource.FileResource
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.stream.Stream

@SpringBootTest
@ActiveProfiles("test", "jail4")
class FileResourceFilterIT(
    @Autowired private val fileResource: FileResource,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun beforeEach() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build()
    }

    @Test
    fun testTagOutputStepchild() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children.map { it.path }).containsExactlyInAnyOrder(
            SafePath("/example1/input1"),
            SafePath("/example1/input2"),
            SafePath("/example1/input3"),
            SafePath("/example1/tags"),
            SafePath("/example1/.fileadmin.json"),
        )
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
        assertThat(directory.children).hasSize(22)
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
        assertThat(directory.children).hasSize(1)
    }

    @Test
    fun testSystemUnknownTagsFile() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/file?path=/example1/tags/.fileadmin.system/unknownTags.txt"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("text/plain;charset=utf-8"))
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
        val inode = JsonFormat.decodeFromString<InodeDto>(result.response.contentAsString)
        // Then
        assertThat(inode.errors).isEmpty()
        assertThat(inode.mimeType).isEqualTo(MediaType.TEXT_PLAIN_VALUE)
        assertThat(inode.lastModifiedMilliseconds).isNull()
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
                .andExpect(content().contentType("text/plain;charset=utf-8"))
                .andReturn()
        val lines = result.response.contentAsString.split("\n").toSet()
        // Then
        assertThat(lines).containsExactly(
            "AntonioElliott",
            "EmailFrom_AntonioElliott",
            "EmailFrom_Dad",
            "EmailFrom_FranzMandl",
            "EmailFrom_KeaganValencia",
            "EmailFrom_LandenKirk",
            "EmailFrom_me",
            "EmailFrom_MinaHendricks",
            "EmailFrom_Mom",
            "EmailTo_",
            "EmailTo_AntonioElliott",
            "EmailTo_Dad",
            "EmailTo_EmployeeA1",
            "EmailTo_EmployeeA2",
            "EmailTo_FranzMandl",
            "EmailTo_KeaganValencia",
            "EmailTo_LandenKirk",
            "EmailTo_me",
            "EmailTo_MinaHendricks",
            "EmailTo_Mom",
            "hardware",
            "MinaHendricks",
            "Mom",
            "printer1a",
            "To",
            "unused1",
            "unused2",
            "unused3",
            ""
        )
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
        assertThat(directory.children).hasSize(6)
        val children = directory.children.sortedBy { it.path }
        assertThat(children[0].operation.canInodeRename).isTrue()
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
        assertThat(directory.children).hasSize(2)
        val children = directory.children.sortedBy { it.path }
        assertThat(children[0].path).isEqualTo(SafePath("/example1/input1/2022/2022-11-22 - #person.txt"))
        assertThat(children[0].item?.time).isEqualTo("2022-11-22")
    }

    @Test
    fun testOperatorMax2Evaluate() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/,max(/2/)/,evaluate"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(3)
        val children = directory.children.sortedBy { it.path }
        assertThat(children[0].path.absoluteString).startsWith("/example1/input")
        assertThat(children[0].item?.result?.priority).isNotNull()
    }

    @Test
    fun testOperatorMimeTypeJavascriptEvaluate() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/,text/MimeType(/.*/javascript\$/)/,evaluate"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(2)
        val children = directory.children.sortedBy { it.path }
        assertThat(children[0].path).isEqualTo(SafePath("/example1/input2/#phone1.js"))
        assertThat(children[0].item?.time).isNull()
    }

    @Test
    fun testOperatorTextName() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/,operator/text/Name("))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(1)
        val children = directory.children.sortedBy { it.path }
        assertThat(children[0].path).isEqualTo(SafePath("/example1/tags/,operator/text/Name(/)"))
    }

    @Test
    fun testOperatorTextContent() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/,operator/text/Content("))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(1)
        assertThat(directory.children[0].path).isEqualTo(SafePath("/example1/tags/,operator/text/Content(/)"))
        assertThat(directory.children[0].size).isNull()
    }

    @Test
    fun testOperatorTextPathExample1Input2Evaluate() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/,text/Path(/^/example1/input2/)/,evaluate"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(2)
        val children = directory.children.sortedBy { it.path }
        assertThat(children[0].path).isEqualTo(SafePath("/example1/input2/#phone1.js"))
    }

    @Test
    fun testOperatorTextContentAccumsanEvaluate() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/,text/Content(/accuMsan/)/,evaluate"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(3)
        val children = directory.children.sortedBy { it.path }
        assertThat(children[0].path).isEqualTo(SafePath("/example1/input1/2022/2022-11-22 - content"))
        assertThat(children[0].size).isNotNull()
        assertThat(children[0].item?.time).isEqualTo("2022-11-22")
        assertThat(children[1].path).isEqualTo(SafePath("/example1/input1/2022/2022-11-22 - content1.txt"))
        assertThat(children[1].size).isNotNull()
        assertThat(children[1].item?.time).isEqualTo("2022-11-22")
    }

    @Test
    fun testOperatorTextIllegalRegex() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/,text/Content(/accuMsan/max(/100"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.children).hasSize(1)
        val children = directory.children.sortedBy { it.path }
        assertThat(children[0].path).isEqualTo(SafePath("/example1/tags/,text/Content(/accuMsan/max(/100/)"))
    }

    @Test
    fun testOperatorTextIllegalRegexEvaluate() {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/example1/tags/,text/Content(/accuMsan/max(/100/)/,evaluate"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).containsExactly(
            """Illegal regex "accuMsan/max(/100": Unclosed group near index 17
accuMsan/max(/100"""
        )
        assertThat(directory.children).hasSize(0)
    }

    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testSuggestion(word: String, expected: Set<String>) {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/suggestion?path=/example1/tags&word=$word"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val suggestions = JsonFormat.decodeFromString<List<String>>(result.response.contentAsString)
        // Then
        assertThat(suggestions).containsExactlyInAnyOrderElementsOf(expected)
    }

    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testTimeDirectory(path: String, expected: Boolean) {
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=$path"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.errors).isEmpty()
        assertThat(directory.inode.isTimeDirectory).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun testSuggestion(): Stream<Arguments> =
            Stream.of(
                Arguments.of("un_", setOf("lostAndFound", "prune", "unknown", "unknown1", "unknown2", "unused1", "unused2")),
                Arguments.of("Email", setOf("Email")),
                Arguments.of("EmailFrom_", setOf("EmailFrom_")),
                Arguments.of(
                    "EmailFrom_M__", setOf(
                        "EmailFrom_me",
                        "EmailFrom_MinaHendricks",
                        "EmailFrom_Mom",
                    )
                ),
                Arguments.of("gr", setOf<String>()),
                Arguments.of("gro", setOf("group_CompanyA")),
            )

        @JvmStatic
        fun testTimeDirectory(): Stream<Arguments> =
            Stream.of(
                Arguments.of("/example1", false),
                Arguments.of("/example1/input1", true),
                Arguments.of("/example1/input1/2022", true),
                Arguments.of("/example1/input1/2022/2022-11-22 - content1.txt", false),
            )
    }
}
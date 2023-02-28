package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.model.ApplicationCtx
import com.franzmandl.fileadmin.model.Directory
import com.franzmandl.fileadmin.model.InodeModel
import com.franzmandl.fileadmin.resource.FileResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
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
import java.time.LocalDate
import java.time.Period

@SpringBootTest
@ActiveProfiles("test", "jail2")
class FileResourceTaskTests(
    @Autowired private val fileResource: FileResource,
) {
    private lateinit var mockMvc: MockMvc
    private val now = LocalDate.now()
    private val status = "40-to_do"
    private val defaultStatuses = sortedSetOf("20-backlog", status, TaskUtil.doneStatus, "80-aborted")

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build()
    }

    private fun sortedInodes(inodes: List<InodeModel>): List<InodeModel> =
        inodes.sortedWith { a, b ->
            a.path.name.compareTo(b.path.name)
        }

    private fun assertTask(
        inode: InodeModel,
        name: String,
        friendlyName: String?
    ) {
        assertThat(inode.error).isNull()
        assertThat(inode.friendlyName).isEqualTo(friendlyName)
        val task = inode.task
        if (task == null) {
            fail()
        } else {
            assertThat(task.actions).hasSize(3)
            defaultStatuses.filter { it != status }.forEach {
                assertThat(task.actions).containsEntry(it, "../$it/$name")
            }
        }
    }

    private fun assertRepeatingTask(
        inode: InodeModel,
        currentStatus: String,
        name: String,
        friendlyName: String? = null,
        doneName: String = name
    ) {
        assertThat(inode.path.name).isEqualTo(name)
        assertThat(inode.error).isNull()
        assertThat(inode.friendlyName).isEqualTo(friendlyName)
        val task = inode.task
        if (task == null) {
            fail()
        } else {
            assertThat(task.actions).hasSize(3)
            defaultStatuses.filter { it != currentStatus && it != TaskUtil.doneStatus }.forEach {
                assertThat(task.actions).containsEntry(it, "../$it/$name")
            }
            if (currentStatus != TaskUtil.doneStatus) {
                assertThat(task.actions).containsEntry(TaskUtil.doneStatus, doneName)
            }
        }
    }

    private fun assertDirectory(directory: Directory, name: String = "<nowDay> - <cursor>.txt", isFile: Boolean = true) {
        assertThat(directory.errors).isEmpty()
        assertThat(directory.nameCursorPosition).isNull()
        assertThat(directory.newInodeTemplate.isFile).isEqualTo(isFile)
        assertThat(directory.newInodeTemplate.name).isEqualTo(name)
    }

    @Test
    fun testDateDirectory() {
        // Given
        val project = "date"
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/$project/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertDirectory(directory)
        assertThat(directory.inodes).hasSize(4)
        directory.inodes.forEach {
            assertTask(it, it.path.name, null)
        }
    }

    @Test
    fun testDateRepeatDirectory() {
        // Given
        val project = "dateRepeat"
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/$project/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertDirectory(directory)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(3)
        assertRepeatingTask(inodes[0], status, "2020-03-04R - 40-dummy.txt", null, "${now}R - 40-dummy.txt")
        assertRepeatingTask(
            inodes[1],
            status,
            "2020-03-04R1D - 40-dummy.txt",
            null,
            "${now + Period.ofDays(1)}R1D - 40-dummy.txt"
        )
        assertRepeatingTask(
            inodes[2],
            status,
            "2020-03-04R2W - 40-dummy.txt",
            null,
            "${now + Period.ofWeeks(2)}R2W - 40-dummy.txt"
        )
    }

    @Test
    fun testDateForceRepeatDirectory() {
        // Given
        val project = "dateForceRepeat"
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/$project/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertDirectory(directory)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(3)
        assertRepeatingTask(inodes[0], status, "2020-03-04R! - 40-dummy.txt")
        assertRepeatingTask(inodes[1], status, "2020-03-04R!1D - 40-dummy.txt", null, "2020-03-05R!1D - 40-dummy.txt")
        assertRepeatingTask(inodes[2], status, "2020-03-04R!2W - 40-dummy.txt", null, "2020-03-18R!2W - 40-dummy.txt")
    }

    @Test
    fun testExprDirectory() {
        // Given
        val project = "expr"
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/$project/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertDirectory(directory)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(8)
        assertTask(inodes[0], inodes[0].path.name, "0000-01-01 - 40-dummy.txt")
        assertTask(inodes[1], inodes[1].path.name, "2000-01-01 - 40-dummy.txt")
        assertTask(inodes[2], inodes[2].path.name, "2020-08-04 - 40-dummy.txt")
        assertTask(inodes[3], inodes[3].path.name, "9999-01-01 - 40-dummy.txt")
        assertTask(inodes[4], inodes[4].path.name, "2020-03-02 - 40-dummy.txt")
        assertTask(inodes[5], inodes[5].path.name, "2020-03-02 - 40-dummy")
        assertTask(inodes[6], inodes[6].path.name, "2023-02-01 - 40-dummy.txt")
        assertTask(inodes[7], inodes[7].path.name, "9999-01-01 - 40-dummy.txt")
    }

    @Test
    fun testExprRepeatDirectory() {
        // Given
        val project = "exprRepeat"
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/$project/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertDirectory(directory)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(2)
        assertRepeatingTask(
            inodes[0],
            status,
            "bin(bin2 2000 01 01 {args})R - 40-dummy.txt",
            "2000-01-01 - 40-dummy.txt",
            "bin(expr 2000-01-01)R - 40-dummy.txt"
        )
        assertRepeatingTask(
            inodes[1],
            status,
            "bin(bin2 2000 01 01 {args})R1D - 40-dummy.txt",
            "2000-01-01 - 40-dummy.txt",
            "bin(expr 2000-01-02)R1D - 40-dummy.txt"
        )
    }

    @Test
    fun testProject1Directory() {
        // Given
        val project = "project1"
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/$project/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertDirectory(directory, isFile = false)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(2)

        assertThat(inodes[0].path.name).isEqualTo("0000-00-00 - 40-dummy.txt")
        assertThat(inodes[0].error).isNull()
        assertThat(inodes[0].friendlyName).isNull()
        assertThat(inodes[0].task?.actions).hasSize(0)

        assertThat(inodes[1].path.name).isEqualTo("0000-00-00R1M - 40-dummy.txt")
        assertThat(inodes[1].error).isNull()
        assertThat(inodes[1].friendlyName).isNull()
        assertThat(inodes[1].task?.actions).hasSize(1)
        assertThat(inodes[1].task?.actions).containsEntry(
            TaskUtil.doneStatus,
            "${now + Period.ofMonths(1)}R1M - 40-dummy.txt"
        )
    }

    private fun assertErrorTask(
        inode: InodeModel,
        name: String,
        error: String?,
    ) {
        assertThat(inode.path.name).isEqualTo(name)
        assertThat(inode.error).isEqualTo(error)
        if (error != null) {
            assertThat(inode.task).isNull()
        }
    }

    @Test
    fun testErrorDirectory() {
        // Given
        val project = "error"
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/$project/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertDirectory(directory)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(5)
        assertErrorTask(
            inodes[0],
            "2021-02-01R!1YE-1M - switch repeat and effective.txt",
            "[2021-02-01R!1YE-1M - switch repeat and effective.txt] Syntax error: 1:14:mismatched input 'E' expecting {'+', '&', ' ', DIGITS, FILE_ENDING}"
        )
        assertErrorTask(inodes[1], "2022-01-01 & id(1) - duplicate id.txt", null)
        assertErrorTask(
            inodes[2],
            "2022-06-15 & ref(1) - reference duplicate id.txt",
            "Semantic error: Duplicate id 1"
        )
        assertErrorTask(inodes[3], "2022-12-31 & id(1) - duplicate id.txt", null)
        assertErrorTask(
            inodes[4],
            "no date.txt",
            "[no date.txt] Syntax error: 1:3:no viable alternative at input ' date'"
        )
    }

    @Test
    fun testWildcardDirectory() {
        // Given
        val project = "*"
        // When
        val result =
            mockMvc.perform(get("${ApplicationCtx.RequestMappingPaths.authenticated}/directory?path=/$project/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertDirectory(directory)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(39)
        for (inode in inodes) {
            if (inode.error == null) {
                assertThat(inode.task).withFailMessage(inode.path.name).isNotNull
            }
        }
    }
}
package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.Config
import com.franzmandl.fileadmin.model.Directory
import com.franzmandl.fileadmin.model.Inode
import com.franzmandl.fileadmin.model.Settings
import com.franzmandl.fileadmin.ticket.TicketUtil
import com.franzmandl.fileadmin.util.JsonFormat
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
class FileResourceTests(
    @Autowired val fileResource: FileResource,
) {
    lateinit var mockMvc: MockMvc
    private val now = LocalDate.now()
    private val defaultStatuses = sortedSetOf("20-backlog", "40-to_do", TicketUtil.doneStatus, "80-aborted")

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileResource).build()
    }

    @Test
    fun testIllegalMapping() {
        mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/asdf"))
            .andExpect(status().isNotFound)
            .andReturn()
    }

    @Test
    fun testRootDir() {
        // Given
        val size = 8
        // When
        val result = mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.inodes).hasSize(size)
    }

    @Test
    fun testFileDir() {
        // Given
        val size = 1
        // When
        val result =
            mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/date/20-backlog/2020-03-04 - 20-dummy.txt"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertThat(directory.inodes).hasSize(size)
        val inode = directory.inodes[0]
        assertThat(inode.basename).isEqualTo("2020-03-04 - 20-dummy.txt")
        assertThat(inode.friendlyName).isNull()
        assertThat(inode.dirname).isEqualTo("date/20-backlog")
        assertThat(inode.realDirname).isEqualTo("date/20-backlog")
        assertThat(inode.path).isEqualTo("/date/20-backlog/2020-03-04 - 20-dummy.txt")
        assertThat(inode.isDirectory).isFalse
        assertThat(inode.isFile).isTrue
        assertThat(inode.target).isNull()
        assertThat(inode.canRead).isTrue
        assertThat(inode.canWrite).isTrue
        assertThat(inode.size).isEqualTo(0)
        assertThat(inode.error).isNull()
    }

    @Test
    fun testRootInode() {
        // When
        val result = mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/inode?path=/"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val inode = JsonFormat.decodeFromString<Inode>(result.response.contentAsString)
        // Then
        assertThat(inode.basename).isEqualTo("")
        assertThat(inode.canRead).isTrue
        assertThat(inode.canWrite).isTrue
        assertThat(inode.dirname).isEqualTo("")
        assertThat(inode.error).isNull()
        assertThat(inode.friendlyName).isNull()
        assertThat(inode.isDirectory).isTrue
        assertThat(inode.isFile).isFalse
        assertThat(inode.path).isEqualTo("/")
        assertThat(inode.realDirname).isEqualTo("")
        assertThat(inode.target).isNull()
        assertThat(inode.ticket).isNull()
        assertThat(inode.isRoot).isTrue
    }

    @Test
    fun testVirtualInode() {
        // When
        val result = mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/inode?path=/*"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val inode = JsonFormat.decodeFromString<Inode>(result.response.contentAsString)
        // Then
        assertThat(inode.basename).isEqualTo("*")
        assertThat(inode.canRead).isFalse
        assertThat(inode.canWrite).isFalse
        assertThat(inode.dirname).isEqualTo("")
        assertThat(inode.error).isNull()
        assertThat(inode.friendlyName).isNull()
        assertThat(inode.isDirectory).isFalse
        assertThat(inode.isFile).isFalse
        assertThat(inode.path).isEqualTo("/*")
        assertThat(inode.realDirname).isEqualTo("")
        assertThat(inode.target).isNull()
        assertThat(inode.ticket).isNull()
        assertThat(inode.isRoot).isFalse
        assertThat(inode.isVirtual).isTrue
    }

    private fun sortedInodes(inodes: List<Inode>): List<Inode> {
        return inodes.sortedWith { a, b ->
            a.basename.compareTo(b.basename)
        }
    }

    private fun assertTicket(
        inode: Inode,
        currentStatus: String,
        basename: String,
        friendlyName: String?
    ) {
        assertThat(inode.error).isNull()
        assertThat(inode.friendlyName).isEqualTo(friendlyName)
        val ticket = inode.ticket
        if (ticket == null) {
            fail()
        } else {
            assertThat(ticket.actions).hasSize(3)
            defaultStatuses.filter { it != currentStatus }.forEach {
                assertThat(ticket.actions).containsEntry(it, "../$it/$basename")
            }
        }
    }

    private fun assertPeriodTicket(
        inode: Inode,
        currentStatus: String,
        basename: String,
        friendlyName: String? = null,
        doneBasename: String = basename
    ) {
        assertThat(inode.basename).isEqualTo(basename)
        assertThat(inode.error).isNull()
        assertThat(inode.friendlyName).isEqualTo(friendlyName)
        val ticket = inode.ticket
        if (ticket == null) {
            fail()
        } else {
            assertThat(ticket.actions).hasSize(3)
            defaultStatuses.filter { it != currentStatus && it != TicketUtil.doneStatus }.forEach {
                assertThat(ticket.actions).containsEntry(it, "../$it/$basename")
            }
            if (currentStatus != TicketUtil.doneStatus) {
                assertThat(ticket.actions).containsEntry(TicketUtil.doneStatus, doneBasename)
            }
        }
    }

    private fun assertSettings(settings: Settings, basename: String = "<now> - .txt", isFile: Boolean = true) {
        assertThat(settings.newInodeTemplate.basename).isEqualTo(basename)
        assertThat(settings.newInodeTemplate.isFile).isEqualTo(isFile)
    }

    @Test
    fun testDateTicketDir() {
        // Given
        val ticketDir = "date"
        val status = "40-to_do"
        val size = 4
        // When
        val result =
            mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/$ticketDir/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertSettings(directory.settings)
        assertThat(directory.inodes).hasSize(size)
        directory.inodes.forEach {
            assertTicket(it, status, it.basename, null)
        }
    }

    @Test
    fun testDatePeriodTicketDir() {
        // Given
        val ticketDir = "datePeriod"
        val status = "40-to_do"
        val size = 3
        // When
        val result =
            mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/$ticketDir/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertSettings(directory.settings)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(size)
        assertPeriodTicket(inodes[0], status, "2020-03-04P - 40-dummy.txt", null, "${now}P - 40-dummy.txt")
        assertPeriodTicket(
            inodes[1],
            status,
            "2020-03-04P1D - 40-dummy.txt",
            null,
            "${now + Period.ofDays(1)}P1D - 40-dummy.txt"
        )
        assertPeriodTicket(
            inodes[2],
            status,
            "2020-03-04P2W - 40-dummy.txt",
            null,
            "${now + Period.ofWeeks(2)}P2W - 40-dummy.txt"
        )
    }

    @Test
    fun testForceDatePeriodTicketDir() {
        // Given
        val ticketDir = "forceDatePeriod"
        val status = "40-to_do"
        val size = 3
        // When
        val result =
            mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/$ticketDir/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertSettings(directory.settings)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(size)
        assertPeriodTicket(inodes[0], status, "2020-03-04P! - 40-dummy.txt")
        assertPeriodTicket(inodes[1], status, "2020-03-04P!1D - 40-dummy.txt", null, "2020-03-05P!1D - 40-dummy.txt")
        assertPeriodTicket(inodes[2], status, "2020-03-04P!2W - 40-dummy.txt", null, "2020-03-18P!2W - 40-dummy.txt")
    }

    @Test
    fun testExprTicketDir() {
        // Given
        val ticketDir = "expr"
        val status = "40-to_do"
        val size = 7
        // When
        val result =
            mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/$ticketDir/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertSettings(directory.settings)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(size)
        assertTicket(inodes[0], status, inodes[0].basename, "0000-01-01 - 40-dummy.txt")
        assertTicket(inodes[1], status, inodes[1].basename, "2000-01-01 - 40-dummy.txt")
        assertTicket(inodes[2], status, inodes[2].basename, "2020-08-04 - 40-dummy.txt")
        assertTicket(inodes[3], status, inodes[3].basename, "9999-01-01 - 40-dummy.txt")
        assertTicket(inodes[4], status, inodes[4].basename, "2020-03-02 - 40-dummy.txt")
        assertTicket(inodes[5], status, inodes[5].basename, "2020-03-02 - 40-dummy")
        assertTicket(inodes[6], status, inodes[6].basename, "9999-01-01 - 40-dummy.txt")
    }

    @Test
    fun testExprPeriodTicketDir() {
        // Given
        val ticketDir = "exprPeriod"
        val status = "40-to_do"
        val size = 2
        // When
        val result =
            mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/$ticketDir/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertSettings(directory.settings)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(size)
        assertPeriodTicket(
            inodes[0],
            status,
            "bin(bin2 2000 01 01 {args})P - 40-dummy.txt",
            "2000-01-01 - 40-dummy.txt",
            "bin(expr 2000-01-01)P - 40-dummy.txt"
        )
        assertPeriodTicket(
            inodes[1],
            status,
            "bin(bin2 2000 01 01 {args})P1D - 40-dummy.txt",
            "2000-01-01 - 40-dummy.txt",
            "bin(expr 2000-01-02)P1D - 40-dummy.txt"
        )
    }

    @Test
    fun testProjectTicketDir() {
        // Given
        val ticketDir = "project1"
        val status = "40-to_do"
        val size = 2
        // When
        val result =
            mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/$ticketDir/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertSettings(directory.settings, isFile = false)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(size)

        assertThat(inodes[0].basename).isEqualTo("0000-00-00 - 40-dummy.txt")
        assertThat(inodes[0].error).isNull()
        assertThat(inodes[0].friendlyName).isNull()
        assertThat(inodes[0].ticket!!.actions).hasSize(0)

        assertThat(inodes[1].basename).isEqualTo("0000-00-00P1M - 40-dummy.txt")
        assertThat(inodes[1].error).isNull()
        assertThat(inodes[1].friendlyName).isNull()
        assertThat(inodes[1].ticket!!.actions).hasSize(1)
        assertThat(inodes[1].ticket!!.actions).containsEntry(
            TicketUtil.doneStatus,
            "${now + Period.ofMonths(1)}P1M - 40-dummy.txt"
        )
    }

    private fun assertErrorTicket(
        inode: Inode,
        basename: String,
        error: String?,
    ) {
        assertThat(inode.basename).isEqualTo(basename)
        assertThat(inode.error).isEqualTo(error)
        if (error != null) {
            assertThat(inode.ticket).isNull()
        }
    }

    @Test
    fun testErrorTicketDir() {
        // Given
        val ticketDir = "error"
        val status = "40-to_do"
        val size = 5
        // When
        val result =
            mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/$ticketDir/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertSettings(directory.settings)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(size)
        assertErrorTicket(
            inodes[0],
            "2021-02-01P!1YE-1M - switch period and effective.txt",
            "[2021-02-01P!1YE-1M - switch period and effective.txt] Syntax error: 1:14:mismatched input 'E' expecting {'+', '&', ' ', DIGITS, FILE_ENDING}"
        )
        assertErrorTicket(inodes[1], "2022-01-01 & id(1) - duplicate id.txt", null)
        assertErrorTicket(
            inodes[2],
            "2022-06-15 & ref(1) - reference duplicate id.txt",
            "Semantic error: Duplicate id 1"
        )
        assertErrorTicket(inodes[3], "2022-12-31 & id(1) - duplicate id.txt", null)
        assertErrorTicket(
            inodes[4],
            "no date.txt",
            "[no date.txt] Syntax error: 1:3:no viable alternative at input ' date'"
        )
    }

    @Test
    fun testWildcardTicketDir() {
        // Given
        val ticketDir = "*"
        val status = "40-to_do"
        val size = 27
        // When
        val result =
            mockMvc.perform(get("${Config.RequestMappingPaths.authenticated}/directory?path=/$ticketDir/$status"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val directory = JsonFormat.decodeFromString<Directory>(result.response.contentAsString)
        // Then
        assertSettings(directory.settings)
        val inodes = sortedInodes(directory.inodes)
        assertThat(inodes).hasSize(size)
        for (inode in inodes) {
            if (inode.error == null) {
                assertThat(inode.ticket).withFailMessage(inode.basename).isNotNull
            }
        }
    }
}

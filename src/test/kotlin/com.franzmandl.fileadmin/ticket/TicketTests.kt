package com.franzmandl.fileadmin.ticket

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.Period

class TicketTests {
    private val dummyFile = File(".")
    private val dummyBasenameEnding = " - title.txt"
    private val ticketBinaries = File("./src/test/resources/ticket/bin")
    private val now = LocalDate.now()
    private val ticketRegistry = TicketRegistry(listOf(), ticketBinaries, now)
    private val dummyTicketDate = createTicketDate("0000-00-00 - dummy.txt")
    private var expectedCanHandlePeriod = true
    private var expectedUsesExpression = false
    private var expectedUsesOperators = false

    @BeforeEach
    fun setUp() {
        expectedCanHandlePeriod = true
        expectedUsesExpression = false
        expectedUsesOperators = false
    }

    private fun days(days: Int) = now + Period.ofDays(days)

    private fun createTicketDate(basename: String) =
        TicketDate(dummyFile, TicketUtil.createTree(basename), ticketRegistry, HashSet())

    private fun assertPeriodTicket(trigger: String, expectedDoneTrigger: String, expectedDate: LocalDate): TicketDate {
        val basename = "$trigger$dummyBasenameEnding"
        val ticketDate = createTicketDate(basename)
        assertThat(ticketDate.canHandlePeriod).isEqualTo(expectedCanHandlePeriod)
        assertThat(ticketDate.hasPeriod).isTrue
        assertThat(ticketDate.usesOperators).isEqualTo(expectedUsesOperators)
        assertThat(ticketDate.usesExpression).isEqualTo(expectedUsesExpression)
        assertThat(expectedDate).isEqualTo(ticketDate.date)
        assertThat(ticketDate.getStatusPath("dummy")).isEqualTo("../dummy/$basename")
        assertThat(ticketDate.getStatusPath(TicketUtil.doneStatus)).isEqualTo("$expectedDoneTrigger - title.txt")
        return ticketDate
    }

    private fun assertPeriodTicket(trigger: String, expectedDoneTrigger: String, year: Int, month: Int, day: Int) =
        assertPeriodTicket(trigger, expectedDoneTrigger, LocalDate.of(year, month, day))

    private fun assertTicket(trigger: String, expectedDate: LocalDate): TicketDate {
        val basename = "$trigger$dummyBasenameEnding"
        val ticketDate = createTicketDate(basename)
        assertThat(ticketDate.canHandlePeriod).isEqualTo(expectedCanHandlePeriod)
        assertThat(ticketDate.hasPeriod).isFalse
        assertThat(ticketDate.usesOperators).isEqualTo(expectedUsesOperators)
        assertThat(ticketDate.usesExpression).isEqualTo(expectedUsesExpression)
        assertThat(expectedDate).isEqualTo(ticketDate.date)
        assertThat(ticketDate.getStatusPath("dummy")).isEqualTo("../dummy/$basename")
        assertThat(ticketDate.getStatusPath(TicketUtil.doneStatus)).isEqualTo("../${TicketUtil.doneStatus}/$basename")
        return ticketDate
    }

    private fun assertTicket(trigger: String, year: Int, month: Int, day: Int) =
        assertTicket(trigger, LocalDate.of(year, month, day))

    @Test
    fun testDateTrigger() {
        assertTicket("2020-08-04", 2020, 8, 4)
        assertTicket("2020-08-04E3Y2M1D", 2023, 10, 5)
        assertTicket("2020-08-04E-3Y2M1D", 2017, 6, 3)
        assertTicket("2020-08-04E2W", 2020, 8, 18)
        assertTicket("2020-08-04E-2W", 2020, 7, 21)
        assertTicket("2020-08-00", 2020, 8, 1)
        assertTicket("2020-00-00", 2020, 1, 1)
        assertTicket("0000-00-00", 0, 1, 1)
    }

    @Test
    fun testOperatorDateTrigger() {
        expectedUsesOperators = true
        assertTicket("2020-08-04 + 2021-09-01", 2020, 8, 4)
        assertTicket("2021-09-01 + 2020-08-04", 2020, 8, 4)
        assertTicket("2020-08-04 & 2021-09-01", 2021, 9, 1)
        assertTicket("2021-09-01 & 2020-08-04", 2021, 9, 1)
    }

    @Test
    fun testDateTriggerPeriod() {
        assertPeriodTicket("2020-08-04P", "${now}P", 2020, 8, 4)
        assertPeriodTicket("2020-08-04P!", "2020-08-04P!", 2020, 8, 4)
        assertPeriodTicket("0000-00-00P!", "0000-00-00P!", 0, 1, 1)
        assertPeriodTicket("2020-08-04P3Y2M1D", "${now + Period.of(3, 2, 1)}P3Y2M1D", 2020, 8, 4)
        assertPeriodTicket("2020-08-04P5W", "${now + Period.ofWeeks(5)}P5W", 2020, 8, 4)
        assertPeriodTicket("2020-08-04P14D", "${now + Period.ofDays(14)}P14D", 2020, 8, 4)
        assertPeriodTicket("2020-08-04 12.34P14D", "${now + Period.ofDays(14)} 12.34P14D", 2020, 8, 4)
        assertPeriodTicket("2020-08-04 12.34.56P14D", "${now + Period.ofDays(14)} 12.34.56P14D", 2020, 8, 4)
    }

    @Test
    fun testOperatorDateTriggerPeriod() {
        expectedUsesOperators = true
        assertPeriodTicket("${days(-3)}P!5D + ${days(3)}P!5D", "${days(2)}P!5D + ${days(3)}P!5D", days(-3))
    }

    @Test
    fun testExprTrigger() {
        expectedUsesExpression = true
        assertTicket("waiting", 9999, 1, 1)
        assertTicket("bin(bin1 arg11 arg12 {args})", 2020, 3, 2)
        expectedCanHandlePeriod = false
        assertTicket("bin(bin1 arg11 arg12)", 2020, 3, 2)
    }

    @Test
    fun testExprTriggerPeriod() {
        expectedUsesExpression = true
        assertPeriodTicket("waiting P", "waiting P", 9999, 1, 1)
        assertPeriodTicket("waiting()P", "waiting()P", 9999, 1, 1)
        assertPeriodTicket("waiting(a)P", "waiting(a)P", 9999, 1, 1)
        assertPeriodTicket("waiting(a b 'c d')P", "waiting(a b 'c d')P", 9999, 1, 1)
    }

    @Test
    fun testIsWaiting() {
        assertThat(createTicketDate("2022-02-02$dummyBasenameEnding").isWaiting).isFalse
        assertThat(createTicketDate("waiting$dummyBasenameEnding").isWaiting).isTrue
        assertThat(createTicketDate("waiting P$dummyBasenameEnding").isWaiting).isTrue
        assertThat(createTicketDate("waiting(a b 'c d')P$dummyBasenameEnding").isWaiting).isTrue
    }

    @Test
    fun testSimpleTicketRegistry() {
        val date = LocalDate.of(2020, 8, 4)
        val idTicketFile = File("$date & id(1) - test")
        val refTicketFile = File("ref(1) - test")
        val ticketRegistry = TicketRegistry(listOf(idTicketFile, refTicketFile), ticketBinaries, now)
        assertThat(date).isEqualTo(ticketRegistry.getOrCreateTicketDate(idTicketFile).date)
        assertThat(date).isEqualTo(ticketRegistry.getOrCreateTicketDate(refTicketFile).date)
    }

    @Test
    fun testPeriodTicketRegistry() {
        val now = LocalDate.of(2020, 9, 5)
        val date = LocalDate.of(2020, 8, 4)
        val idTicketFile = File("${date}P1W & id(1) - test")
        val refTicketFile = File("${date}P2W & ref(1) - test")
        val ticketRegistry = TicketRegistry(listOf(idTicketFile, refTicketFile), ticketBinaries, now)
        val idTicketDate = ticketRegistry.getOrCreateTicketDate(idTicketFile)
        val refTicketDate = ticketRegistry.getOrCreateTicketDate(refTicketFile)
        assertThat(idTicketDate.getStatusPath(TicketUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(1)}P1W & id(1) - test")
        assertThat(refTicketDate.getStatusPath(TicketUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(2)}P2W & ref(1) - test")
    }

    @Test
    fun testDoublePeriodTicketRegistry() {
        val now = LocalDate.of(2020, 9, 5)
        val date1 = LocalDate.of(2020, 8, 4)
        val date2 = LocalDate.of(2020, 8, 4)
        val idTicketFile1 = File("${date1}P1W & id(1) - test")
        val refTicketFile1 = File("${date1}P2W & ref(1) - test")
        val idTicketFile2 = File("${date2}P3W & id(2) - test")
        val refTicketFile2 = File("${date2}P6W & ref(2) - test")
        val ticketRegistry =
            TicketRegistry(listOf(idTicketFile1, refTicketFile2, idTicketFile2, refTicketFile1), ticketBinaries, now)
        val idTicketDate1 = ticketRegistry.getOrCreateTicketDate(idTicketFile1)
        val refTicketDate1 = ticketRegistry.getOrCreateTicketDate(refTicketFile1)
        val idTicketDate2 = ticketRegistry.getOrCreateTicketDate(idTicketFile2)
        val refTicketDate2 = ticketRegistry.getOrCreateTicketDate(refTicketFile2)
        assertThat(idTicketDate1.getStatusPath(TicketUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(1)}P1W & id(1) - test")
        assertThat(refTicketDate1.getStatusPath(TicketUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(2)}P2W & ref(1) - test")
        assertThat(idTicketDate2.getStatusPath(TicketUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(3)}P3W & id(2) - test")
        assertThat(refTicketDate2.getStatusPath(TicketUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(6)}P6W & ref(2) - test")
    }

    @Test
    fun testDuplicateIdTicketRegistry() {
        assertThrows(TicketException::class.java) {
            TicketRegistry(
                listOf(File("2020-01-01 & id(1) - test1"), File("2021-02-02 & id(1) - test2")),
                ticketBinaries,
                now
            ).getById(1, dummyTicketDate, HashSet())
        }
    }
}

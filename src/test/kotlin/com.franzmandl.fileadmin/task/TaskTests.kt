package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.TestUtil
import com.franzmandl.fileadmin.model.ApplicationCtx
import com.franzmandl.fileadmin.vfs.Inode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.Period

@SpringBootTest
@ActiveProfiles("test", "jail2")
class TaskTests(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val dummyFile = TestUtil.createNativeInode("project1")
    private val dummyNameEnding = " - title.txt"
    private val now = LocalDate.now()
    private val taskCtx = createTaskCtx(now, listOf())
    private val dummyTaskDate = createTaskDate("0000-00-00 - dummy.txt")
    private var expectedCanRepeat = true
    private var expectedPriority = 0
    private var expectedUsesExpression = false
    private var expectedUsesOperators = false

    @BeforeEach
    fun setUp() {
        expectedCanRepeat = true
        expectedPriority = 0
        expectedUsesExpression = false
        expectedUsesOperators = false
    }

    private fun createTaskCtx(now: LocalDate, files: Iterable<Inode>): TaskCtx =
        TaskCtx(applicationCtx.createRequestCtx(now), sortedSetOf(), files)

    private fun days(days: Int) = now + Period.ofDays(days)

    private fun createTaskDate(name: String) =
        TaskDate(taskCtx, dummyFile, TaskUtil.createTree(name), HashSet())

    private fun assertRepeatingTask(trigger: String, expectedDoneTrigger: String, expectedDate: LocalDate): TaskDate {
        val name = "$trigger$dummyNameEnding"
        val taskDate = createTaskDate(name)
        assertThat(taskDate.canRepeat).isEqualTo(expectedCanRepeat)
        assertThat(taskDate.isRepeating).isTrue
        assertThat(taskDate.priority).isEqualTo(expectedPriority)
        assertThat(taskDate.usesOperators).isEqualTo(expectedUsesOperators)
        assertThat(taskDate.usesExpression).isEqualTo(expectedUsesExpression)
        assertThat(expectedDate).isEqualTo(taskDate.date)
        assertThat(taskDate.getStatusPath("dummy")).isEqualTo("../dummy/$name")
        assertThat(taskDate.getStatusPath(TaskUtil.doneStatus)).isEqualTo("$expectedDoneTrigger - title.txt")
        return taskDate
    }

    private fun assertRepeatingTask(trigger: String, expectedDoneTrigger: String, year: Int, month: Int, day: Int) =
        assertRepeatingTask(trigger, expectedDoneTrigger, LocalDate.of(year, month, day))

    private fun assertTask(trigger: String, expectedDate: LocalDate): TaskDate {
        val name = "$trigger$dummyNameEnding"
        val taskDate = createTaskDate(name)
        assertThat(taskDate.canRepeat).isEqualTo(expectedCanRepeat)
        assertThat(taskDate.isRepeating).isFalse
        assertThat(taskDate.priority).isEqualTo(expectedPriority)
        assertThat(taskDate.usesOperators).isEqualTo(expectedUsesOperators)
        assertThat(taskDate.usesExpression).isEqualTo(expectedUsesExpression)
        assertThat(expectedDate).isEqualTo(taskDate.date)
        assertThat(taskDate.getStatusPath("dummy")).isEqualTo("../dummy/$name")
        assertThat(taskDate.getStatusPath(TaskUtil.doneStatus)).isEqualTo("../${TaskUtil.doneStatus}/$name")
        return taskDate
    }

    private fun assertTask(trigger: String, year: Int, month: Int, day: Int) =
        assertTask(trigger, LocalDate.of(year, month, day))

    @Test
    fun testDateTrigger() {
        assertTask("2020-08-04", 2020, 8, 4)
        assertTask("2020-08-04E3Y2M1D", 2023, 10, 5)
        assertTask("2020-08-04E-3Y2M1D", 2017, 6, 3)
        assertTask("2020-08-04E2W", 2020, 8, 18)
        assertTask("2020-08-04E-2W", 2020, 7, 21)
        assertTask("2020-08-00", 2020, 8, 1)
        assertTask("2020-00-00", 2020, 1, 1)
        assertTask("0000-00-00", 0, 1, 1)
        expectedPriority = 25
        assertTask("2020-08-04P25", 2020, 8, 4)
        assertTask("2020-08-04E3Y2M1DP25", 2023, 10, 5)
    }

    @Test
    fun testOperatorDateTrigger() {
        expectedUsesOperators = true
        assertTask("2020-08-04 + 2021-09-01", 2020, 8, 4)
        assertTask("2021-09-01 + 2020-08-04", 2020, 8, 4)
        assertTask("2020-08-04 & 2021-09-01", 2021, 9, 1)
        assertTask("2021-09-01 & 2020-08-04", 2021, 9, 1)
        expectedPriority = 2
        assertTask("2020-08-04P1 + 2021-09-01P2", 2020, 8, 4)
        assertTask("2020-08-04P1 & 2021-09-01P2", 2021, 9, 1)
    }

    @Test
    fun testDateTriggerRepeat() {
        assertRepeatingTask("2020-08-04R", "${now}R", 2020, 8, 4)
        assertRepeatingTask("2020-08-04R!", "2020-08-04R!", 2020, 8, 4)
        assertRepeatingTask("0000-00-00R!", "0000-00-00R!", 0, 1, 1)
        assertRepeatingTask("2020-08-04R3Y2M1D", "${now + Period.of(3, 2, 1)}R3Y2M1D", 2020, 8, 4)
        assertRepeatingTask("2020-08-04R5W", "${now + Period.ofWeeks(5)}R5W", 2020, 8, 4)
        assertRepeatingTask("2020-08-04R14D", "${now + Period.ofDays(14)}R14D", 2020, 8, 4)
        assertRepeatingTask("2020-08-04 12.34R14D", "${now + Period.ofDays(14)} 12.34R14D", 2020, 8, 4)
        assertRepeatingTask("2020-08-04 12.34.56R14D", "${now + Period.ofDays(14)} 12.34.56R14D", 2020, 8, 4)
    }

    @Test
    fun testOperatorDateTriggerRepeat() {
        expectedUsesOperators = true
        assertRepeatingTask("${days(-3)}R!5D + ${days(3)}R!5D", "${days(2)}R!5D + ${days(3)}R!5D", days(-3))
    }

    @Test
    fun testExprBinTrigger() {
        expectedUsesExpression = true
        assertTask("bin(bin1 arg11 arg12 {args})", 2020, 3, 2)
        expectedCanRepeat = false
        assertTask("bin(bin1 arg11 arg12)", 2020, 3, 2)
    }

    @Test
    fun testExprBinTriggerRepeat() {
        expectedUsesExpression = true
        assertRepeatingTask("bin(bin1 arg11 arg12 {args})R", "2020-03-02R", 2020, 3, 2)
        expectedCanRepeat = false
        assertRepeatingTask("bin(bin1 arg11 arg12)R", "2020-03-02R", 2020, 3, 2)
    }

    @Test
    fun testExprBuiltinTrigger() {
        expectedUsesExpression = true
        expectedCanRepeat = false
        assertTask("builtin(non_empty 2020-03-02 'project1/40-to_do')", 2020, 3, 2)
        assertTask("builtin(non_empty 2020-03-02 'project1/40-to_do/0000-00-00 - 40-dummy.txt')", 9999, 1, 1)
    }

    @Test
    fun testExprBuiltinTriggerRepeat() {
        expectedUsesExpression = true
        expectedCanRepeat = false
        assertRepeatingTask("builtin(non_empty 2020-03-02 'project1/40-to_do')R", "2020-03-02R", 2020, 3, 2)
        assertRepeatingTask("builtin(non_empty 2020-03-02 'project1/40-to_do/0000-00-00 - 40-dummy.txt')R", "9999-01-01R", 9999, 1, 1)
    }

    @Test
    fun testExprTrigger() {
        expectedUsesExpression = true
        assertTask("waiting", 9999, 1, 1)
    }

    @Test
    fun testExprTriggerRepeat() {
        expectedUsesExpression = true
        assertRepeatingTask("waiting R", "waiting R", 9999, 1, 1)
        assertRepeatingTask("waiting()R", "waiting()R", 9999, 1, 1)
        assertRepeatingTask("waiting(a)R", "waiting(a)R", 9999, 1, 1)
        assertRepeatingTask("waiting(a b 'c d')R", "waiting(a b 'c d')R", 9999, 1, 1)
    }

    @Test
    fun testIsWaiting() {
        assertThat(createTaskDate("2022-02-02$dummyNameEnding").isWaiting).isFalse
        assertThat(createTaskDate("waiting$dummyNameEnding").isWaiting).isTrue
        assertThat(createTaskDate("waiting R$dummyNameEnding").isWaiting).isTrue
        assertThat(createTaskDate("waiting(a b 'c d')R$dummyNameEnding").isWaiting).isTrue
    }

    @Test
    fun testSimpleTaskRegistry() {
        val date = LocalDate.of(2020, 8, 4)
        val idTaskFile = TestUtil.createNativeInode("$date & id(1) - test")
        val refTaskFile = TestUtil.createNativeInode("ref(1) - test")
        val localCtx = createTaskCtx(now, listOf(idTaskFile, refTaskFile))
        assertThat(date).isEqualTo(localCtx.registry.getOrCreateTaskDate(idTaskFile).date)
        assertThat(date).isEqualTo(localCtx.registry.getOrCreateTaskDate(refTaskFile).date)
    }

    @Test
    fun testRepeatingTaskRegistry() {
        val now = LocalDate.of(2020, 9, 5)
        val date = LocalDate.of(2020, 8, 4)
        val idTaskFile = TestUtil.createNativeInode("${date}R1W & id(1) - test")
        val refTaskFile = TestUtil.createNativeInode("${date}R2W & ref(1) - test")
        val localCtx = createTaskCtx(now, listOf(idTaskFile, refTaskFile))
        val idTaskDate = localCtx.registry.getOrCreateTaskDate(idTaskFile)
        val refTaskDate = localCtx.registry.getOrCreateTaskDate(refTaskFile)
        assertThat(idTaskDate.getStatusPath(TaskUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(1)}R1W & id(1) - test")
        assertThat(refTaskDate.getStatusPath(TaskUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(2)}R2W & ref(1) - test")
    }

    @Test
    fun testDoubleRepeatingTaskRegistry() {
        val now = LocalDate.of(2020, 9, 5)
        val date1 = LocalDate.of(2020, 8, 4)
        val date2 = LocalDate.of(2020, 8, 4)
        val idTaskFile1 = TestUtil.createNativeInode("${date1}R1W & id(1) - test")
        val refTaskFile1 = TestUtil.createNativeInode("${date1}R2W & ref(1) - test")
        val idTaskFile2 = TestUtil.createNativeInode("${date2}R3W & id(2) - test")
        val refTaskFile2 = TestUtil.createNativeInode("${date2}R6W & ref(2) - test")
        val localCtx =
            createTaskCtx(now, listOf(idTaskFile1, refTaskFile2, idTaskFile2, refTaskFile1))
        val idTaskDate1 = localCtx.registry.getOrCreateTaskDate(idTaskFile1)
        val refTaskDate1 = localCtx.registry.getOrCreateTaskDate(refTaskFile1)
        val idTaskDate2 = localCtx.registry.getOrCreateTaskDate(idTaskFile2)
        val refTaskDate2 = localCtx.registry.getOrCreateTaskDate(refTaskFile2)
        assertThat(idTaskDate1.getStatusPath(TaskUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(1)}R1W & id(1) - test")
        assertThat(refTaskDate1.getStatusPath(TaskUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(2)}R2W & ref(1) - test")
        assertThat(idTaskDate2.getStatusPath(TaskUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(3)}R3W & id(2) - test")
        assertThat(refTaskDate2.getStatusPath(TaskUtil.doneStatus)).isEqualTo("${now + Period.ofWeeks(6)}R6W & ref(2) - test")
    }

    @Test
    fun testDuplicateIdTaskRegistry() {
        assertThrows(TaskException::class.java) {
            createTaskCtx(
                now,
                listOf(
                    TestUtil.createNativeInode("2020-01-01 & id(1) - test1"),
                    TestUtil.createNativeInode("2021-02-02 & id(1) - test2")
                )
            ).registry.getById(1, dummyTaskDate, HashSet())
        }
    }
}
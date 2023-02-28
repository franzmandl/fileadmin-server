package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.generated.task.TaskLexer
import com.franzmandl.fileadmin.generated.task.TaskParser
import com.franzmandl.fileadmin.generated.task.TaskParser.*
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.UnsafePath
import org.antlr.v4.runtime.*
import java.time.LocalDate
import java.time.Period
import java.util.*
import java.util.concurrent.TimeUnit

object TaskUtil {
    const val doneStatus = "60-done"
    val maxDate: LocalDate = LocalDate.of(9999, 1, 1)
    val minDate: LocalDate = LocalDate.of(0, 1, 1)

    fun visitEffective(ctx: EffectiveContext?): Period = when {
        ctx?.periodYearsMonthsDays() != null -> visitPeriodYearsMonthsDays(ctx.periodYearsMonthsDays())
        ctx?.periodWeeks() != null -> visitPeriodWeeks(ctx.periodWeeks())
        else -> Period.ZERO
    }.multipliedBy(if (ctx?.negative != null) -1 else 1)

    fun visitRepeat(ctx: RepeatContext?): Period = when {
        ctx?.periodYearsMonthsDays() != null -> visitPeriodYearsMonthsDays(ctx.periodYearsMonthsDays())
        ctx?.periodWeeks() != null -> visitPeriodWeeks(ctx.periodWeeks())
        else -> Period.ZERO
    }

    private fun visitPeriodYearsMonthsDays(ctx: PeriodYearsMonthsDaysContext): Period = Period.of(
        parseInt(ctx.periodYears()?.value?.text ?: "0"),
        parseInt(ctx.periodMonths()?.value?.text ?: "0"),
        parseInt(ctx.periodDays()?.value?.text ?: "0")
    )

    private fun visitPeriodWeeks(ctx: PeriodWeeksContext): Period =
        Period.ofWeeks(parseInt(ctx.value.text ?: "0"))

    fun visitIntArg(ctx: ArgsContext?) = ctx?.arg()?.mapIndexed { index, argCtx ->
        if (index == 0 && argCtx.string() != null) {
            parseInt(argCtx.string().text)
        } else {
            throw TaskException("[${argCtx.text}] Semantic error: only one integer arg is accepted")
        }
    } ?: throw TaskException("[${ctx?.text}] Semantic error: one integer arg is required")

    fun parseInt(string: String): Int = try {
        string.toInt()
    } catch (e: NumberFormatException) {
        throw TaskException("Parse error: Not a number '$string'")
    }

    fun parseDate(date: String, original: String): LocalDate =
        CommonUtil.parseDate(date) ?: throw TaskException("[$original] Parse error: Not a date")

    fun parseArguments(args: List<Arg>, period: Period?): LinkedList<String> {
        val mutablePeriodArgs = LinkedList<String>()
        if (period != null) {
            if (period.years != 0) {
                mutablePeriodArgs.add("${period.years}")
                mutablePeriodArgs.add("years")
            }
            if (period.months != 0) {
                mutablePeriodArgs.add("${period.months}")
                mutablePeriodArgs.add("months")
            }
            if (period.days != 0 || mutablePeriodArgs.isEmpty()) {
                mutablePeriodArgs.add("${period.days}")
                mutablePeriodArgs.add("days")
            }
        }
        val mutableArgs = LinkedList<String>()
        args.forEach { arg ->
            when (arg) {
                is StringArg -> mutableArgs.add(arg.value)
                is KeywordArg -> mutableArgs.add(mutablePeriodArgs.joinToString(" "))
                is KeywordArgs -> mutableArgs.addAll(mutablePeriodArgs)
            }
        }
        return mutableArgs
    }

    fun visitExprTriggerBin(args: List<String>, original: String): String {
        val process = ProcessBuilder(args).start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroy()
            throw TaskException("[$original] Binary error: Process timeout")
        }
        val exitValue = process.exitValue()
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val (line, stdout) = process.inputStream.bufferedReader().use { it.readLine() to it.readText() }
        if (exitValue != 0 || stderr.isNotEmpty() || stdout.isNotEmpty()) {
            throw TaskException("[$original] Binary error: $exitValue:$stdout:$stderr")
        }
        if (line == null) {
            throw TaskException("[$original] Binary error: Line is null")
        }
        return line
    }

    fun visitExprTriggerBuiltin(requestCtx: RequestCtx, task: Inode, args: LinkedList<String>, original: String): LocalDate =
        when (CommonUtil.popOrNull(args)) {
            null -> throw TaskException("[$original] Builtin error: Arguments are empty")
            "filter" -> {
                val ifTrue = parseDate(CommonUtil.popOrNull(args) ?: throw TaskException("[$original] Builtin error: Argument 'ifTrue' missing"), original)
                val path = UnsafePath(CommonUtil.popOrNull(args) ?: throw TaskException("[$original] Builtin error: Argument 'path' missing"))
                if (args.isNotEmpty()) {
                    throw TaskException("[$original] Builtin error: Too many arguments")
                }
                val inode = requestCtx.getInode(task.path.parent?.resolve(path) ?: throw TaskException("[$original] Builtin error: Path breaks out of jail"))
                val system = inode.config.filter ?: throw TaskException("[$original] Builtin error: Path has no filter")
                if (inode.path != system.ctx.output) {
                    throw TaskException("[$original] Builtin error: Path is no filter output")
                }
                if (system.requiresAction { throw TaskException(it) }) ifTrue else maxDate
            }

            "non_empty" -> {
                val ifTrue = parseDate(CommonUtil.popOrNull(args) ?: throw TaskException("[$original] Builtin error: Argument 'ifTrue' missing"), original)
                val path = UnsafePath(CommonUtil.popOrNull(args) ?: throw TaskException("[$original] Builtin error: Argument 'path' missing"))
                if (args.isNotEmpty()) {
                    throw TaskException("[$original] Builtin error: Too many arguments")
                }
                val inode = requestCtx.getInode(task.path.parent?.resolve(path) ?: throw TaskException("[$original] Builtin error: Path breaks out of jail"))
                when {
                    inode.contentPermission.canFileGet -> if (inode.sizeFile > 0) ifTrue else maxDate
                    inode.contentPermission.canDirectoryGet -> if (inode.children.isNotEmpty()) ifTrue else maxDate
                    else -> throw TaskException("[$original] Builtin error: Neither file nor directory or insufficient permissions")
                }
            }

            else -> throw TaskException("[$original] Builtin error: No builtin found")
        }

    class ParseErrorListener(private val input: String) : BaseErrorListener() {
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String?,
            e: RecognitionException?
        ) {
            throw TaskException("[$input] Syntax error: $line:$charPositionInLine:$msg")
        }
    }

    private fun createParser(input: String): TaskParser {
        val lexer = TaskLexer(CharStreams.fromString(input))
        lexer.removeErrorListeners()
        lexer.addErrorListener(ParseErrorListener(input))
        val parser = TaskParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(ParseErrorListener(input))
        return parser
    }

    fun createTree(input: String): StartContext =
        createParser(input).start()
}
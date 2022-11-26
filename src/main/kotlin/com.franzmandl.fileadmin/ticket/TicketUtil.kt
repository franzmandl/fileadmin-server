package com.franzmandl.fileadmin.ticket

import com.franzmandl.fileadmin.Config
import com.franzmandl.fileadmin.generated.ticket.TicketLexer
import com.franzmandl.fileadmin.generated.ticket.TicketParser
import com.franzmandl.fileadmin.generated.ticket.TicketParser.*
import com.franzmandl.fileadmin.model.Settings
import org.antlr.v4.runtime.*
import java.io.File
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

object TicketUtil {
    const val doneStatus = "60-done"

    fun visitEffective(ctx: EffectiveContext?): Period = when {
        ctx?.periodYearsMonthsDays() != null -> visitPeriodYearsMonthsDays(ctx.periodYearsMonthsDays())
        ctx?.periodWeeks() != null -> visitPeriodWeeks(ctx.periodWeeks())
        else -> Period.ZERO
    }.multipliedBy(if (ctx?.negative != null) -1 else 1)

    fun visitPeriod(ctx: PeriodContext?): Period = when {
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
            throw TicketException("[${argCtx.text}] Semantic error: only one integer arg is accepted")
        }
    } ?: throw TicketException("[${ctx?.text}] Semantic error: one integer arg is required")

    fun parseInt(string: String) = try {
        string.toInt()
    } catch (e: NumberFormatException) {
        throw TicketException("Parse error: Not a number '$string'")
    }

    private fun parseDate(yearString: String, monthString: String, dayString: String): LocalDate {
        val year = parseInt(yearString)
        val month = parseInt(monthString)
        val day = parseInt(dayString)
        return LocalDate.of(year, max(1, month), max(1, day))
    }

    fun parseDate(date: String, original: String): LocalDate {
        try {
            val dateParts = date.split("-")
            if (dateParts.size != 3) {
                throw TicketException("[$original] Parse error: size=${dateParts.size} of '$date'")
            }
            return parseDate(dateParts[0], dateParts[1], dateParts[2])
        } catch (e: DateTimeParseException) {
            throw TicketException("[$original] Parse error: $e")
        }
    }

    fun visitExprTriggerBin(args: List<Arg>, period: Period?, original: String): String {
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
        return runProcess(mutableArgs, original)
    }

    fun prepareBinary(ticketBinaries: File, binaryName: String, original: String): String {
        val binaries = ticketBinaries.listFiles { _, b -> b == binaryName }
        if (binaries == null || binaries.size != 1) {
            throw TicketException("[$original] Binary '$binaryName' not found")
        }
        return binaries[0].absolutePath
    }

    private fun runProcess(args: List<String>, original: String): String {
        val builder = ProcessBuilder()
        builder.command(args)
        val process = builder.start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroy()
            throw TicketException("[$original] Timeout")
        }
        val exitValue = process.exitValue()
        val stderr = process.errorStream.bufferedReader().readText()
        val line = process.inputStream.bufferedReader().readLine()
        val stdout = process.inputStream.bufferedReader().readText()
        if (exitValue != 0 || stderr.isNotEmpty() || stdout.isNotEmpty()) {
            throw TicketException("[$original] Protocol error: $exitValue:$stdout:$stderr")
        }
        if (line == null) {
            throw TicketException("[$original] Protocol error: Line is null")
        }
        return line
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
            throw TicketException("[$input] Syntax error: $line:$charPositionInLine:$msg")
        }
    }

    private fun createParser(input: String): TicketParser {
        val lexer = TicketLexer(CharStreams.fromString(input))
        lexer.removeErrorListeners()
        lexer.addErrorListener(ParseErrorListener(input))
        val parser = TicketParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(ParseErrorListener(input))
        return parser
    }

    fun createTree(input: String): StartContext = createParser(input).start()

    fun createTicketObjects(config: Config, file: File, now: LocalDate) =
        if (Settings.create(config, file.parentFile).isTickets) {
            TicketObjects(
                TicketRegistry(file.listFiles()!!.asList(), config.files.ticketBinaries, now),
                file.parentFile.listFiles()!!.filter { it.isDirectory }.map { it.name }.toSortedSet()
            )
        } else {
            null
        }
}

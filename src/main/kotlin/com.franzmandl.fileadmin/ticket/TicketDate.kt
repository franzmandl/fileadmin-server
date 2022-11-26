package com.franzmandl.fileadmin.ticket

import com.franzmandl.fileadmin.generated.ticket.TicketParser.*
import com.franzmandl.fileadmin.util.Util
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

class TicketDate(
    val file: File,
    val tree: StartContext,
    val ticketRegistry: TicketRegistry,
    private val callers: MutableSet<TicketDate>
) {
    private val mutableArgs = HashMap<ExprTriggerContext, List<Arg>>()
    val args: Map<ExprTriggerContext, List<Arg>> = mutableArgs
    val date: LocalDate
    val fileEnding: String = tree.FILE_ENDING().text
    var canHandlePeriod = true
        private set
    var hasPeriod = false
        private set
    var isWaiting = false
        private set
    var usesExpression = false
        private set
    var usesOperators = false
        private set

    init {
        // Do method calls in an init-block otherwise object fields might not get initialized correctly.
        date = visitStart(tree)
    }

    fun getLastModified() = Util.convertToLocalDate(file.lastModified())

    fun canHandleStatus(status: String) = status != TicketUtil.doneStatus || !hasPeriod || canHandlePeriod

    fun getStatusPath(status: String) =
        if (hasPeriod && status == TicketUtil.doneStatus)
            TicketDoneBasename(this).basename
        else
            "../$status/${tree.text}"

    private fun visitStart(ctx: StartContext) = visitTrigger(ctx.trigger(), true)

    private data class TriggerResult(
        val date: LocalDate,
        val effectiveCtx: EffectiveContext? = null,
        val periodCtx: PeriodContext? = null
    )

    private fun visitTrigger(ctx: TriggerContext, isFirstLevel: Boolean): LocalDate {
        val result = when {
            ctx.dateTrigger() != null -> visitDateTrigger(ctx.dateTrigger())
            ctx.exprTrigger() != null -> visitExprTrigger(ctx.exprTrigger(), isFirstLevel)
            ctx.and != null -> TriggerResult(
                visitAnd(
                    visitTrigger(ctx.lhs, isFirstLevel),
                    visitTrigger(ctx.rhs, isFirstLevel),
                    isFirstLevel
                )
            )

            ctx.or != null -> TriggerResult(
                visitOr(
                    visitTrigger(ctx.lhs, isFirstLevel),
                    visitTrigger(ctx.rhs, isFirstLevel),
                    isFirstLevel
                )
            )

            ctx.nestedTrigger() != null -> TriggerResult(visitTrigger(ctx.nestedTrigger().trigger(), isFirstLevel))
            else -> throw IllegalStateException()
        }
        hasPeriod = hasPeriod || (isFirstLevel && result.periodCtx != null)
        return result.date + TicketUtil.visitEffective(result.effectiveCtx)
    }

    private fun visitDateTrigger(ctx: DateTriggerContext) =
        TriggerResult(TicketUtil.parseDate(ctx.DATE().text, ctx.DATE().text), ctx.effective(), ctx.period())

    private fun visitExprTrigger(ctx: ExprTriggerContext, isFirstLevel: Boolean): TriggerResult {
        usesExpression = true
        return TriggerResult(
            when (ctx.id().text) {
                "bin" -> {
                    val args = visitExprTriggerBinArgs(ctx.args(), ctx.text)
                    mutableArgs[ctx] = args
                    TicketUtil.parseDate(TicketUtil.visitExprTriggerBin(args, null, ctx.args().text), ctx.args().text)
                }

                "id" -> {
                    minDate
                }

                "now" -> {
                    visitZeroArgs(ctx.args(), ctx.id().text)
                    ticketRegistry.now
                }

                "ref" -> {
                    ticketRegistry.getById(TicketUtil.visitIntArg(ctx.args())[0], this, callers)
                }

                "waiting" -> {
                    isWaiting = isFirstLevel
                    maxDate
                }

                else -> throw TicketException("[${ctx.text}] Semantic error: Illegal builtin '${ctx.id().text}'")
            }, ctx.effective(), ctx.period()
        )
    }

    private fun visitZeroArgs(ctx: ArgsContext?, builtinName: String) = ctx?.arg()?.forEach { _ ->
        throw TicketException("[${ctx.text}] Semantic error: $builtinName does not accept arguments")
    }

    private fun visitExprTriggerBinArgs(ctx: ArgsContext?, original: String): List<Arg> {
        canHandlePeriod = false
        return ctx?.arg()?.mapIndexed { index, argCtx ->
            when {
                index == 0 && argCtx.QUOTED_STRING() != null -> StringArg(
                    TicketUtil.prepareBinary(
                        ticketRegistry.ticketBinaries,
                        visitQuotedString(argCtx.QUOTED_STRING()),
                        original
                    )
                )

                index == 0 && argCtx.string() != null -> StringArg(
                    TicketUtil.prepareBinary(
                        ticketRegistry.ticketBinaries,
                        argCtx.string().text,
                        original
                    )
                )

                index == 0 -> throw TicketException("[${argCtx.text}] Placeholder not allowed at first position")
                argCtx.QUOTED_STRING() != null -> StringArg(visitQuotedString(argCtx.QUOTED_STRING()))
                argCtx.string() != null -> StringArg(argCtx.string().text)
                argCtx.trigger() != null -> visitTriggerArg(argCtx.trigger())
                else -> throw IllegalStateException()
            }
        } ?: listOf()
    }

    private fun visitTriggerArg(triggerCtx: TriggerContext): Arg = when {
        triggerCtx.exprTrigger() != null -> visitExprTriggerArg(triggerCtx.exprTrigger())
        else -> StringArg("${visitTrigger(triggerCtx, false)}")
    }

    private fun visitExprTriggerArg(exprTriggerCtx: ExprTriggerContext) =
        when (exprTriggerCtx.id()?.text) {
            "arg" -> {
                canHandlePeriod = true
                KeywordArg
            }

            "args" -> {
                canHandlePeriod = true
                KeywordArgs
            }

            "line" -> visitLineArg(exprTriggerCtx.args())
            "target" -> visitTargetArg()
            "ticket" -> StringArg(file.absolutePath)
            else -> StringArg("${visitExprTrigger(exprTriggerCtx, false).date}")
        }

    private var lines: List<String>? = null

    private fun visitLineArg(argsCtx: ArgsContext): StringArg {
        if (argsCtx.arg(0) == null || argsCtx.arg(1) != null) {
            throw TicketException("[${argsCtx.text}] Semantic error: line accepts exactly one argument")
        }
        val lineIndex = TicketUtil.parseInt(argsCtx.arg(0).string().text) - 1
        fun getLines(): List<String> {
            if (!file.isFile) {
                throw TicketException("[${argsCtx.text}] Semantic error: '${file.absolutePath}' must be a file")
            }
            if (!file.canRead()) {
                throw TicketException("[${argsCtx.text}] Semantic error: cannot read form file '${file.absolutePath}'")
            }
            return file.readLines()
        }

        val lines = lines ?: getLines()
        this.lines = lines
        if (lineIndex >= lines.size) {
            throw TicketException("[${argsCtx.text}] Semantic error: file '${file.absolutePath}' only has ${lines.size} lines, not ${lineIndex + 1}")
        }
        return StringArg(lines[lineIndex])
    }

    private fun visitTargetArg(): StringArg {
        val nioPath = file.toPath()
        if (!Files.isSymbolicLink(nioPath)) {
            throw TicketException("Semantic error: file '${file.absolutePath}' is not a symbolic link.")
        }
        return StringArg(Files.readSymbolicLink(nioPath).toString())
    }

    private fun visitQuotedString(terminal: TerminalNode) = terminal.text.substring(1, terminal.text.length - 1)

    private fun visitAnd(lhs: LocalDate, rhs: LocalDate, isFirstLevel: Boolean): LocalDate {
        usesOperators = usesOperators || isFirstLevel
        return if (lhs < rhs) rhs else lhs
    }

    private fun visitOr(lhs: LocalDate, rhs: LocalDate, isFirstLevel: Boolean): LocalDate {
        usesOperators = usesOperators || isFirstLevel
        return if (lhs < rhs) lhs else rhs
    }

    companion object {
        val maxDate: LocalDate = LocalDate.of(9999, 1, 1)
        val minDate: LocalDate = LocalDate.of(0, 1, 1)
    }
}

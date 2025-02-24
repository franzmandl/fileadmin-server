package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.generated.task.TaskParser.*
import com.franzmandl.fileadmin.vfs.Inode1
import com.franzmandl.fileadmin.vfs.Link
import com.franzmandl.fileadmin.vfs.NativeInode
import org.antlr.v4.runtime.tree.TerminalNode
import java.time.LocalDate
import java.util.*

class TaskDate(
    val taskDateCtx: TaskDateCtx,
    val inode: Inode1<*>,
    val tree: StartContext,
    private val callers: MutableSet<TaskDate>,
) {
    private val mutableArgs = mutableMapOf<ExprTriggerContext, List<Arg>>()
    val args: Map<ExprTriggerContext, List<Arg>> = mutableArgs
    val date: LocalDate
    val fileEnding: String = tree.FILE_ENDING().text
    var canRepeat = true
        private set
    var isRepeating = false
        private set
    var isWaiting = false
        private set
    var priority: Int? = null
        private set
    var usesExpression = false
        private set
    var usesOperators = false
        private set

    init {
        // Do method calls in an init-block otherwise object fields might not get initialized correctly.
        date = visitStart(tree)
    }

    fun getLastModified() = inode.inode0.lastModified?.let { CommonUtil.convertToLocalDate(it) }

    private fun visitStart(ctx: StartContext) = visitTrigger(ctx.trigger(), true)

    private data class TriggerResult(
        val date: LocalDate,
        val effectiveCtx: EffectiveContext? = null,
        val priorityCtx: PriorityContext? = null,
        val repeatCtx: RepeatContext? = null
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
            else -> error("")
        }
        isRepeating = isRepeating || (isFirstLevel && result.repeatCtx != null)
        if (isFirstLevel && result.priorityCtx != null) {
            priority = TaskUtil.parseInt(result.priorityCtx.value.text) * (if (result.priorityCtx.negative != null) -1 else 1)
        }
        return result.date + TaskUtil.visitEffective(result.effectiveCtx)
    }

    private fun visitDateTrigger(ctx: DateTriggerContext) =
        TriggerResult(TaskUtil.parseDate(ctx.DATE().text, ctx.DATE().text), ctx.effective(), ctx.priority(), ctx.repeat())

    private fun visitExprTrigger(ctx: ExprTriggerContext, isFirstLevel: Boolean): TriggerResult {
        usesExpression = true
        return TriggerResult(
            when (ctx.id().text) {
                "bin" -> {
                    val original = ctx.args().text
                    val args = visitExprTriggerArgs(ctx.args())
                    val firstArg = args.firstOrNull() ?: throw TaskException("[$original] Binary error: Arguments are empty.")
                    if (firstArg !is StringArg) {
                        throw TaskException("[$original] Binary error: Placeholder not allowed as first argument.")
                    }
                    args[0] = StringArg(taskDateCtx.prepareBinary(firstArg.value, original))
                    mutableArgs[ctx] = args
                    TaskUtil.parseDate(TaskUtil.visitExprTriggerBin(TaskUtil.parseArguments(args, null), original), original)
                }

                "builtin" -> {
                    val args = visitExprTriggerArgs(ctx.args())
                    mutableArgs[ctx] = args
                    TaskUtil.visitExprTriggerBuiltin(taskDateCtx.request, inode, TaskUtil.parseArguments(args, null), ctx.args().text)
                }

                "id" -> {
                    TaskUtil.minDate
                }

                "now" -> {
                    visitZeroArgs(ctx.args(), ctx.id().text)
                    taskDateCtx.request.now
                }

                "ref" -> {
                    taskDateCtx.getById(TaskUtil.visitStringArg(ctx.args())[0], this, callers)
                }

                "waiting" -> {
                    isWaiting = isFirstLevel
                    TaskUtil.maxDate
                }

                else -> throw TaskException("""[${ctx.text}] Semantic error: Illegal expression "${ctx.id().text}".""")
            }, ctx.effective(), ctx.priority(), ctx.repeat()
        )
    }

    private fun visitZeroArgs(ctx: ArgsContext?, expression: String) = ctx?.arg()?.forEach { _ ->
        throw TaskException("""[${ctx.text}] Semantic error: "$expression" does not accept arguments.""")
    }

    private fun visitExprTriggerArgs(ctx: ArgsContext?): MutableList<Arg> {
        canRepeat = false
        return ctx?.arg()?.mapTo(LinkedList()) { argCtx ->
            when {
                argCtx.QUOTED_STRING() != null -> StringArg(visitQuotedString(argCtx.QUOTED_STRING()))
                argCtx.string() != null -> StringArg(argCtx.string().text)
                argCtx.trigger() != null -> visitTriggerArg(argCtx.trigger())
                else -> error("")
            }
        } ?: LinkedList()
    }

    private fun visitTriggerArg(ctx: TriggerContext): Arg = when {
        ctx.exprTrigger() != null -> visitExprTriggerArg(ctx.exprTrigger())
        else -> StringArg("${visitTrigger(ctx, false)}")
    }

    private fun visitExprTriggerArg(ctx: ExprTriggerContext) =
        when (ctx.id()?.text) {
            "arg" -> {
                canRepeat = true
                KeywordArg
            }

            "args" -> {
                canRepeat = true
                KeywordArgs
            }

            "line" -> visitLineArg(ctx.args())

            "target" -> StringArg(
                when {
                    inode.inode0 is Link -> inode.inode0.target.toString()
                    inode.inode0 is NativeInode && inode.inode0.isLink -> inode.inode0.linkTarget.toString()
                    else -> throw TaskException("[${ctx.text}] Semantic error: Inode is neither a link nor a native symbolic link.")
                }
            )

            "task" -> StringArg(
                when (inode.inode0) {
                    is NativeInode -> inode.inode0.publicLocalPath.toString()
                    else -> throw TaskException("[${ctx.text}] Semantic error: Inode must be native.")
                }
            )

            else -> StringArg("${visitExprTrigger(ctx, false).date}")
        }

    private var lines: List<String>? = null

    private fun visitLineArg(ctx: ArgsContext): StringArg {
        if (ctx.arg(0) == null || ctx.arg(1) != null) {
            throw TaskException("[${ctx.text}] Semantic error: line accepts exactly one argument.")
        }
        val lineIndex = TaskUtil.parseInt(ctx.arg(0).string().text) - 1
        val lines = lines ?: try {
            inode.inode0.lines
        } catch (e: HttpException) {
            throw TaskException("[${ctx.text}] Semantic error: ${e.message}")
        }
        this.lines = lines
        if (lineIndex >= lines.size) {
            throw TaskException("[${ctx.text}] Semantic error: Inode only has ${lines.size} lines, not ${lineIndex + 1}.")
        }
        return StringArg(lines[lineIndex])
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
}
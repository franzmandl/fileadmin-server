package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.generated.task.TaskParser.*
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import java.time.Period

class TaskDoneName(
    private val taskDate: TaskDate,
) {
    private val stringBuilder = StringBuilder()
    val name = visitStart(taskDate.tree)

    private fun visitStart(ctx: StartContext): String {
        if (taskDate.isRepeating) {
            visitTrigger(ctx.trigger())
            stringBuilder.append(ctx.FILE_ENDING().text)
        } else {
            stringBuilder.append(ctx.text)
        }
        return stringBuilder.toString()
    }

    private fun visitTrigger(ctx: TriggerContext) {
        when {
            ctx.dateTrigger() != null -> visitDateTrigger(ctx.dateTrigger())
            ctx.exprTrigger() != null -> visitExprTrigger(ctx.exprTrigger())
            ctx.and != null -> visitBinary(ctx, ctx.and)
            ctx.or != null -> visitBinary(ctx, ctx.or)
            ctx.nestedTrigger() != null -> visitNestedTrigger(ctx.nestedTrigger())
            else -> throw IllegalStateException()
        }
    }

    private fun visitDateTrigger(ctx: DateTriggerContext) {
        val period = TaskUtil.visitRepeat(ctx.repeat())
        val force = ctx.repeat()?.exclm != null
        val date = if (force) TaskUtil.parseDate(ctx.DATE().text, ctx.DATE().text) else taskDate.taskCtx.request.now
        if ((taskDate.usesOperators && date > taskDate.taskCtx.request.now) || (force && period == Period.ZERO)) {
            stringBuilder.append(ctx.DATE().text)
        } else {
            stringBuilder.append("${date + period}")
        }
        appendTimeEffectiveRepeat(ctx.time(), ctx.effective(), ctx.priority(), ctx.repeat())
    }

    private fun visitExprTrigger(ctx: ExprTriggerContext) {
        when (ctx.id().text) {
            "bin" -> {
                val period = TaskUtil.visitRepeat(ctx.repeat())
                val args = taskDate.args[ctx] ?: throw IllegalStateException()
                stringBuilder.append(TaskUtil.visitExprTriggerBin(TaskUtil.parseArguments(args, period), ctx.args().text))
                appendTimeEffectiveRepeat(ctx.time(), ctx.effective(), ctx.priority(), ctx.repeat())
            }

            "builtin" -> {
                val period = TaskUtil.visitRepeat(ctx.repeat())
                val args = taskDate.args[ctx] ?: throw IllegalStateException()
                stringBuilder.append(TaskUtil.visitExprTriggerBuiltin(taskDate.taskCtx.request, taskDate.inode, TaskUtil.parseArguments(args, period), ctx.args().text))
                appendTimeEffectiveRepeat(ctx.time(), ctx.effective(), ctx.priority(), ctx.repeat())
            }

            else -> stringBuilder.append(ctx.text)
        }
    }

    private fun appendTimeEffectiveRepeat(
        timeCtx: TimeContext?,
        effectiveCtx: EffectiveContext?,
        priorityCtx: PriorityContext?,
        repeatCtx: RepeatContext?,
    ) {
        appendIfNotNull(timeCtx)
        appendIfNotNull(effectiveCtx)
        appendIfNotNull(priorityCtx)
        appendIfNotNull(repeatCtx)
    }

    private fun appendIfNotNull(ctx: ParserRuleContext?) {
        if (ctx != null) {
            stringBuilder.append(ctx.text)
        }
    }

    private fun visitBinary(ctx: TriggerContext, operator: Token) {
        visitTrigger(ctx.lhs)
        stringBuilder.append(ctx.lhsSpace.text).append(operator.text).append(ctx.rhsSpace.text)
        visitTrigger(ctx.rhs)
    }

    private fun visitNestedTrigger(ctx: NestedTriggerContext) {
        stringBuilder.append(ctx.markL.text)
        visitTrigger(ctx.trigger())
        stringBuilder.append(ctx.markR.text)
    }
}
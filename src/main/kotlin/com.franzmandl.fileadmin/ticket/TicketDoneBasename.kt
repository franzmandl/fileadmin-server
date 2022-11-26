package com.franzmandl.fileadmin.ticket

import com.franzmandl.fileadmin.generated.ticket.TicketParser.*
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import java.time.Period

class TicketDoneBasename(private val ticketDate: TicketDate) {
    private val stringBuilder = StringBuilder()
    val basename = visitStart(ticketDate.tree)

    private fun visitStart(ctx: StartContext): String {
        if (ticketDate.hasPeriod) {
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
        val period = TicketUtil.visitPeriod(ctx.period())
        val force = ctx.period()?.exclm != null
        val date = if (force) TicketUtil.parseDate(ctx.DATE().text, ctx.DATE().text) else ticketDate.ticketRegistry.now
        if ((ticketDate.usesOperators && date > ticketDate.ticketRegistry.now) || (force && period == Period.ZERO)) {
            stringBuilder.append(ctx.DATE().text)
        } else {
            stringBuilder.append("${date + period}")
        }
        appendTimeEffectivePeriod(ctx.time(), ctx.effective(), ctx.period())
    }

    private fun visitExprTrigger(ctx: ExprTriggerContext) {
        when (ctx.id().text) {
            "bin" -> {
                val period = TicketUtil.visitPeriod(ctx.period())
                val args = ticketDate.args[ctx] ?: throw IllegalStateException()
                stringBuilder.append(TicketUtil.visitExprTriggerBin(args, period, ctx.args().text))
                appendTimeEffectivePeriod(ctx.time(), ctx.effective(), ctx.period())
            }

            else -> stringBuilder.append(ctx.text)
        }
    }

    private fun appendTimeEffectivePeriod(
        timeCtx: TimeContext?,
        effectiveCtx: EffectiveContext?,
        periodCtx: PeriodContext?,
    ) {
        appendIfNotNull(timeCtx)
        appendIfNotNull(effectiveCtx)
        appendIfNotNull(periodCtx)
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

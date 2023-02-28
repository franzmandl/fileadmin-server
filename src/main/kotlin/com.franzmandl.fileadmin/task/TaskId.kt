package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.generated.task.TaskParser.*

class TaskId(
    tree: StartContext,
) {
    var id: Int? = null

    init {
        visitStart(tree)
    }

    private fun visitStart(ctx: StartContext) {
        visitTrigger(ctx.trigger())
    }

    private fun visitTrigger(ctx: TriggerContext) {
        when {
            ctx.exprTrigger() != null -> visitExprTrigger(ctx.exprTrigger())
            ctx.and != null || ctx.or != null -> {
                visitTrigger(ctx.lhs)
                visitTrigger(ctx.rhs)
            }

            ctx.nestedTrigger() != null -> visitTrigger(ctx.nestedTrigger().trigger())
        }
    }

    private fun visitExprTrigger(ctx: ExprTriggerContext) {
        if (ctx.id().text == "id") {
            id = if (id == null) {
                TaskUtil.visitIntArg(ctx.args())[0]
            } else {
                throw TaskException("Semantic error: Multiple ids provided")
            }
        }
    }
}
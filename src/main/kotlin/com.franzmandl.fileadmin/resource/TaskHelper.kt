package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.model.Task
import com.franzmandl.fileadmin.task.TaskCtx
import com.franzmandl.fileadmin.task.TaskDate
import com.franzmandl.fileadmin.task.TaskUtil
import com.franzmandl.fileadmin.vfs.Inode

object TaskHelper {
    fun createTaskModel(
        ctx: TaskCtx,
        inode: Inode,
        pathParts: List<String>,
    ): Task {
        val taskDate = ctx.registry.getOrCreateTaskDate(inode)
        val actions = HashMap<String, String>()
        ctx.statuses.forEach { setIfNotStatus(actions, it, taskDate, pathParts) }
        if (TaskUtil.doneStatus !in actions && !isTaskStatus(
                TaskUtil.doneStatus,
                pathParts
            ) && taskDate.isRepeating && taskDate.canRepeat
        ) {
            actions[TaskUtil.doneStatus] = taskDate.getStatusPath(TaskUtil.doneStatus)
        }
        return Task(
            actions,
            "${taskDate.date}",
            taskDate.isRepeating,
            taskDate.isWaiting,
            taskDate.priority,
            taskDate.usesExpression,
            taskDate.fileEnding
        )
    }

    private fun setIfNotStatus(
        actions: HashMap<String, String>,
        status: String,
        taskDate: TaskDate,
        pathParts: List<String>
    ) {
        if (!isTaskStatus(status, pathParts) && taskDate.canHandleStatus(status)) {
            actions[status] = taskDate.getStatusPath(status)
        }
    }

    private fun isTaskStatus(status: String, pathParts: List<String>): Boolean =
        pathParts[pathParts.size - 2] == status
}
package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.dto.Task
import com.franzmandl.fileadmin.filter.FilterCtx
import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.filter.Item
import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.task.*
import com.franzmandl.fileadmin.vfs.Inode1
import java.time.LocalDate

object TaskDtoHelper {
    fun createTaskDto(ctx: TaskCtx, inode: Inode1<*>, currentStatus: String): Task {
        val doneStatusName = ctx.request.application.task.doneStatusName
        val taskDate = ctx.registry.getOrCreateTaskDate(inode)
        val actions = CommonUtil.appendNullable(
            ctx.statuses.asSequence(),
            if (doneStatusName !in ctx.statuses && taskDate.isRepeating && taskDate.canRepeat) sequenceOf(doneStatusName) else null
        )
            .filter { it != currentStatus }
            .filter { it != doneStatusName || !taskDate.isRepeating || taskDate.canRepeat }
            .associateWith { getStatusPath(doneStatusName, taskDate, it) }
        return createTaskDto(actions, taskDate)
    }

    fun getStatusPath(doneStatusName: String, taskDate: TaskDate, status: String) =
        if (taskDate.isRepeating && status == doneStatusName)
            TaskDoneName(taskDate).name
        else
            "../$status/${taskDate.tree.text}"

    fun createTaskDto(requestCtx: RequestCtx, filterCtx: FilterCtx, item: Item): Task? {
        if (filterCtx.registry.systemTags.task !in item.nameTags.ancestors) {
            return null
        }
        val statuses = filterCtx.registry.systemTags.task.getSequenceOfLeafs(Tag.ChildrenParameter.notComputed).toSet()
        val currentStatuses = item.nameTags.tags.intersect(statuses)
        if (currentStatuses.size != 1) {
            return null
        }
        val currentStatus = currentStatuses.first()
        val parts = Regex("${Regex.escape("${FilterFileSystem.tagPrefix}${currentStatus.name}")}(?!${FilterFileSystem.tagNameEndingLetterRegex})")
            .split(item.inode.inode0.path.name, 3)
        if (parts.size != 2) {
            return null
        }
        val taskDate = TaskDate(object : TaskDateCtx {
            override val request = requestCtx
            override fun getById(id: String, caller: TaskDate, callers: MutableSet<TaskDate>): LocalDate = throw TaskException("IDs are not supported.")
            override fun prepareBinary(binaryName: String, original: String): String = throw TaskException("Binaries are not supported.")
        }, item.inode, TaskUtil.createTree(item.inode.inode0.path.name), mutableSetOf())
        val actions = statuses.asSequence()
            .filter { it != currentStatus }
            .filter { it != filterCtx.registry.systemTags.taskDone || !taskDate.isRepeating || taskDate.canRepeat }
            .associate {
                it.name to if (taskDate.isRepeating && it == filterCtx.registry.systemTags.taskDone)
                    TaskDoneName(taskDate).name
                else
                    parts[0] + FilterFileSystem.tagPrefix + it.name + parts[1]
            }
        return createTaskDto(actions, taskDate)
    }

    private fun createTaskDto(actions: Map<String, String>, taskDate: TaskDate) =
        Task(
            actions,
            taskDate.date.toString(),
            taskDate.isRepeating,
            taskDate.isWaiting,
            taskDate.priority,
            taskDate.usesExpression,
            taskDate.fileEnding
        )
}
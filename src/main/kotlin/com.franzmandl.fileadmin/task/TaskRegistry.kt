package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.generated.task.TaskParser.StartContext
import com.franzmandl.fileadmin.vfs.Inode1
import com.franzmandl.fileadmin.vfs.SafePath
import java.time.LocalDate

class TaskRegistry(
    private val context: TaskDateCtx,
    tasks: Iterable<Inode1<*>>,
) {
    private val pathToTaskDate = mutableMapOf<SafePath, TaskDate>()
    private val idToTask: Map<String, Payload<Inode1<*>>>
    private val trees: Map<SafePath, Pair<Inode1<*>, Payload<StartContext>>> = tasks.associate { task ->
        task.inode0.path to try {
            task to Value(TaskUtil.createTree(task.inode0.path.name))
        } catch (e: TaskException) {
            task to e
        }
    }

    init {
        val map = mutableMapOf<String, Payload<Inode1<*>>>()
        trees.forEach { (_, pair) ->
            val (task, payload) = pair
            if (payload is Value) {
                val id = TaskId(payload.value).id
                if (id != null) {
                    map[id] = if (id in map) {
                        TaskException("Semantic error: Duplicate id $id.")
                    } else {
                        Value(task)
                    }
                }
            }
        }
        idToTask = map
    }

    fun getById(id: String, caller: TaskDate, callers: MutableSet<TaskDate>): LocalDate {
        if (caller in callers) {
            throw TaskException("Semantic error: Cycle detected.")
        }
        callers += caller
        val payload = idToTask[id] ?: throw TaskException("""Semantic error: id "$id" not found.""")
        return when (payload) {
            is TaskException -> throw payload
            is Value -> getOrCreateTaskDate(payload.value, callers).date
        }
    }

    fun getOrCreateTaskDate(task: Inode1<*>, callers: MutableSet<TaskDate> = HashSet()): TaskDate {
        val foundTaskDate = pathToTaskDate[task.inode0.path]
        if (foundTaskDate != null) {
            return foundTaskDate
        }
        return when (val payload = (trees[task.inode0.path] ?: throw TaskException("""Server error: Tree not found for "${task.inode0.path}".""")).second) {
            is TaskException -> throw payload
            is Value -> {
                val taskDate = TaskDate(context, task, payload.value, callers)
                pathToTaskDate[task.inode0.path] = taskDate
                taskDate
            }
        }
    }
}
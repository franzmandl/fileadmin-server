package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.generated.task.TaskParser.StartContext
import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.SafePath
import java.time.LocalDate

class TaskRegistry(
    private val context: TaskCtx,
    tasks: Iterable<Inode>,
) {
    private val pathToTaskDate = HashMap<SafePath, TaskDate>()
    private val idToTask: Map<Int, Payload<Inode>>
    private val trees: Map<SafePath, Pair<Inode, Payload<StartContext>>> = tasks.associate { task ->
        task.path to try {
            task to Value(TaskUtil.createTree(task.path.name))
        } catch (e: TaskException) {
            task to e
        }
    }

    init {
        val map = HashMap<Int, Payload<Inode>>()
        trees.forEach { (_, pair) ->
            val (task, payload) = pair
            if (payload is Value) {
                val id = TaskId(payload.value).id
                if (id != null) {
                    map[id] = if (id in map) {
                        TaskException("Semantic error: Duplicate id $id")
                    } else {
                        Value(task)
                    }
                }
            }
        }
        idToTask = map
    }

    fun getById(id: Int, caller: TaskDate, callers: MutableSet<TaskDate>): LocalDate {
        if (caller in callers) {
            throw TaskException("Semantic error: Cycle detected")
        }
        callers.add(caller)
        val payload = idToTask[id] ?: throw TaskException("Semantic error: id $id not found")
        return when (payload) {
            is TaskException -> throw payload
            is Value -> getOrCreateTaskDate(payload.value, callers).date
        }
    }

    fun getOrCreateTaskDate(task: Inode, callers: MutableSet<TaskDate> = HashSet()): TaskDate {
        val foundTaskDate = pathToTaskDate[task.path]
        if (foundTaskDate != null) {
            return foundTaskDate
        }
        return when (val payload = (trees[task.path] ?: throw TaskException("Server error: Tree not found for ${task.path}")).second) {
            is TaskException -> throw payload
            is Value -> {
                val taskDate = TaskDate(context, task, payload.value, callers)
                pathToTaskDate[task.path] = taskDate
                taskDate
            }
        }
    }
}
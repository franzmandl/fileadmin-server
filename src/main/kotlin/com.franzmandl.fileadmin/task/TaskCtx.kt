package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.Inode1
import com.franzmandl.fileadmin.vfs.NativeInode
import com.franzmandl.fileadmin.vfs.PathUtil
import com.franzmandl.fileadmin.vfs.SafePath
import java.time.LocalDate
import java.util.*

class TaskCtx(
    override val request: RequestCtx,
    val statuses: SortedSet<String>,
    tasks: Iterable<Inode1<*>>,
) : TaskDateCtx {
    private val binariesFileSystem = request.application.task.binariesFileSystem
    val registry = TaskRegistry(this, tasks)

    override fun getById(id: String, caller: TaskDate, callers: MutableSet<TaskDate>): LocalDate = registry.getById(id, caller, callers)

    override fun prepareBinary(binaryName: String, original: String): String {
        if (binariesFileSystem == null) {
            throw TaskException("[$original] No task binaries specified.")
        }
        val binary = request.createPathFinderCtx(false).createPathFinder(SafePath(listOf(PathUtil.validateName(binaryName)))).find(binariesFileSystem)
        if (binary.inode0 !is NativeInode) {
            throw TaskException("[$original] Binary must be native.")
        }
        if (!binary.inode0.exists) {
            throw TaskException("""[$original] Binary "$binaryName" not found.""")
        }
        return binary.inode0.publicLocalPath.toString()
    }
}
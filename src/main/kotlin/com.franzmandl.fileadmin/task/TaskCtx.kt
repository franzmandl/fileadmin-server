package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.NativeInode
import com.franzmandl.fileadmin.vfs.PathUtil
import com.franzmandl.fileadmin.vfs.SafePath
import java.util.*

class TaskCtx(
    val request: RequestCtx,
    val statuses: SortedSet<String>,
    tasks: Iterable<Inode>,
) {
    private val binaries = request.application.paths.taskBinaries
    val registry = TaskRegistry(this, tasks)

    fun prepareBinary(binaryName: String, original: String): String {
        if (binaries == null) {
            throw TaskException("[$original] No task binaries specified")
        }
        val binary = request.createPathFinderCtx().createPathFinder(SafePath(listOf(PathUtil.validateName(binaryName)))).find(binaries)
        if (binary !is NativeInode) {
            throw TaskException("[$original] Binary must be native.")
        }
        if (!binary.exists) {
            throw TaskException("[$original] Binary '$binaryName' not found")
        }
        return binary.publicLocalPath.toString()
    }
}
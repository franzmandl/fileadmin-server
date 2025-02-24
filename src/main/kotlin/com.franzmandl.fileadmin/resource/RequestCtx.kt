package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.config.ConfigRoot
import com.franzmandl.fileadmin.filter.Input
import com.franzmandl.fileadmin.task.TaskCtx
import com.franzmandl.fileadmin.vfs.*
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import java.util.*

class RequestCtx(
    val application: ApplicationCtx,
    val now: LocalDate,
) {
    private val configFileCache = mutableMapOf<Pair<SafePath, FileTime?>, ConfigRoot>()
    val fileSystems = mutableMapOf<SafePath, VirtualFileSystem>()
    val scannedInputs = mutableSetOf<Input>()
    val stepchildren = mutableMapOf<SafePath, MutableSet<SafePath>>()
    private val taskCtxCache = mutableMapOf<SafePath, TaskCtx>()

    fun cacheConfigFile(inode: Inode0): ConfigRoot {
        val lastModified = inode.lastModified ?: return JsonFormat.decodeFromString(inode.text)
        return configFileCache.computeIfAbsent(inode.path to lastModified) { JsonFormat.decodeFromString(inode.text) }
    }

    fun createPathFinderCtx(filterIsScanning: Boolean): PathFinder.Ctx =
        PathFinder.Ctx(filterIsScanning, this)

    fun getFileSystem(path: SafePath): VirtualFileSystem? {
        if (path.parts.lastOrNull() == "*") {
            return GlobFileSystem
        }
        return fileSystems[path]
    }

    fun getInode(path: SafePath): Inode1<*> =
        createPathFinderCtx(false).createPathFinder(path).find()

    fun getTaskCtx(statusDirectory: Inode0): TaskCtx? {
        val projectDirectory = statusDirectory.parent.nullableValue ?: return null
        return taskCtxCache.computeIfAbsent(statusDirectory.path) {
            val statuses = TreeSet<String>()
            for (status in CommonUtil.getSequenceOfChildren(this, projectDirectory)) {
                if (status.inode0.contentPermission.canDirectoryAdd) {
                    statuses += status.inode0.path.name
                }
            }
            TaskCtx(this, statuses, CommonUtil.getSequenceOfChildren(this, statusDirectory).toList())
        }
    }
}
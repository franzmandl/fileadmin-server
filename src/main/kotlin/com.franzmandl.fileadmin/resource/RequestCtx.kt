package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.model.ApplicationCtx
import com.franzmandl.fileadmin.model.config.ConfigRoot
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
    val stepchildren = mutableMapOf<SafePath, MutableSet<SafePath>>()
    private val taskCtxCache = mutableMapOf<SafePath, TaskCtx>()

    fun cacheConfigFile(inode: Inode): ConfigRoot {
        val lastModified = inode.lastModified ?: JsonFormat.decodeFromString(inode.text)
        return configFileCache.computeIfAbsent(inode.path to lastModified) { JsonFormat.decodeFromString(inode.text) }
    }

    fun createPathFinderCtx(): PathFinder.Ctx =
        PathFinder.Ctx(mutableSetOf(), this, LinkedList())

    fun getFileSystem(path: SafePath): VirtualFileSystem? {
        if (path.parts.lastOrNull() == "*") {
            return GlobFileSystem
        }
        return fileSystems[path]
    }

    fun getInode(path: SafePath) =
        createPathFinderCtx().createPathFinder(path).find()

    fun getTaskCtx(statusDirectory: InodeWithoutConfig): TaskCtx? {
        val projectDirectory = statusDirectory.parent.nullableValue ?: return null
        return taskCtxCache.computeIfAbsent(statusDirectory.path) {
            val statuses = TreeSet<String>()
            for (status in CommonUtil.getSequenceOfChildren(this, projectDirectory)) {
                if (status.contentPermission.canDirectoryAdd) {
                    statuses.add(status.path.name)
                }
            }
            TaskCtx(this, statuses, CommonUtil.getSequenceOfChildren(this, statusDirectory).toList())
        }
    }
}
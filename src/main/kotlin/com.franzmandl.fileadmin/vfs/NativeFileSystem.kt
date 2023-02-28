package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.OptionalValue
import java.nio.file.Path

class NativeFileSystem(
    private val rootLocalPath: Path,
) : RootFileSystem {
    private fun createNativeInode(finder: PathFinder, parent: OptionalValue<InodeWithoutConfig>, path: SafePath): NativeInode =
        NativeInode(
            rootLocalPath.resolve(path.relativeString),
            parent,
            path,
            stepchildren = finder.ctx.request.stepchildren[path] ?: setOf(),
            finder,
        )

    override fun getInode(finder: PathFinder): Inode {
        var inode: NativeInode? = null
        for (subPath in CommonUtil.getPartsList(finder.destination.parts, true)) {
            val path = SafePath(subPath)
            val configInode = createNativeInode(finder, Inode.parentOfConfig, path.resolve(finder.ctx.request.application.configFileName))
            if (configInode.contentPermission.canFileGet) {
                finder.visitInode(configInode)
            }
            inode = createNativeInode(finder, finder.parent, path)
            if (inode.isLink) {
                val target = inode.linkTarget
                val targetPath = path.parent?.resolve(target)
                if (targetPath != null) {
                    if (targetPath == finder.destination) {
                        // Self referencing symbolic link.
                        return createNativeInode(finder, finder.parent, finder.destination)
                    }
                    val remaining = finder.destination.parts.subList(subPath.size, finder.destination.parts.size)
                    val targetInode = finder.ctx.copy().createPathFinder(targetPath.resolve(remaining)).find()
                    return when {
                        remaining.isEmpty() -> Link(if (targetInode is Link) targetInode.realTargetInode else targetInode, target, targetInode, inode)
                        else -> targetInode
                    }
                }
            }
            if (!inode.exists) {
                return finder.ctx.request.getFileSystem(path)?.getInode(finder, finder.destination.parts.subList(subPath.size, finder.destination.parts.size))
                    ?: createNativeInode(finder, finder.parent, finder.destination)
            }
            finder.parent = OptionalValue.Success(inode)
        }
        return inode ?: throw IllegalStateException("There must have been at least one iteration and therefore it cannot be null. destination='${finder.destination}'")
    }
}
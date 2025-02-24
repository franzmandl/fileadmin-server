package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.OptionalValue
import java.nio.file.Path

class NativeFileSystem(
    private val rootLocalPath: Path,
) : RootFileSystem {
    private fun createNativeInode(finder: PathFinder, path: SafePath): NativeInode =
        NativeInode(rootLocalPath.resolve(path.relativeString), finder.parent, path)

    override fun getInode(finder: PathFinder): Inode1<*> {
        var inode: Inode1<NativeInode>? = null
        for (subPath in CommonUtil.getSequenceOfParts(finder.destination.parts, true)) {
            val path = SafePath(subPath)
            val configInode = createNativeInode(finder, path.resolve(finder.ctx.request.application.config.fileName))
            if (configInode.contentPermission.canFileGet) {
                finder.visitConfigFile(configInode)
            }
            inode = finder.build(createNativeInode(finder, path), null)
            if (inode.inode0.isLink) {
                val target = inode.inode0.linkTarget
                val targetPath = path.parent?.resolve(target)
                if (targetPath != null) {
                    if (targetPath == finder.destination) {
                        // Self referencing symbolic link.
                        return finder.build(createNativeInode(finder, finder.destination), null)
                    }
                    val remaining = finder.destination.sliceParts(subPath)
                    val targetInode = finder.ctx.copy(finder.destination).createPathFinder(targetPath.resolve(remaining)).find()
                    return when {
                        remaining.isEmpty() -> Inode1(
                            inode.config,
                            Link(
                                if (targetInode.inode0 is Link) targetInode.inode0.realTargetInode else targetInode.inode0,
                                target,
                                targetInode,
                                inode.inode0
                            )
                        )

                        else -> targetInode
                    }
                }
            }
            if (!inode.inode0.exists) {
                return finder.ctx.request.getFileSystem(path)?.getInode(finder, finder.destination.sliceParts(subPath))
                    ?: finder.build(createNativeInode(finder, finder.destination), null)
            }
            finder.parent = OptionalValue.Success(inode.inode0)
        }
        return inode ?: error("""There must have been at least one iteration and therefore it cannot be null. destination="${finder.destination}".""")
    }
}
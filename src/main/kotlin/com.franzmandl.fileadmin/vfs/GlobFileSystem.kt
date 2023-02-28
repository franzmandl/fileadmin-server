package com.franzmandl.fileadmin.vfs

object GlobFileSystem : VirtualFileSystem {
    override fun getInode(finder: PathFinder, remaining: List<String>): Inode {
        val children = mutableSetOf<SafePath>()
        val inode = finder.parent.value
        if (inode.contentPermission.canDirectoryGet) {
            for (child in inode.children) {
                val subInode = finder.ctx.createPathFinder(child.resolve(remaining)).find()
                if (subInode.contentPermission.canDirectoryGet) {
                    children.addAll(subInode.children)
                }
            }
        }
        return VirtualDirectory.createFromFinder(finder, VirtualDirectory.TreeOperation.Default, children)
    }
}
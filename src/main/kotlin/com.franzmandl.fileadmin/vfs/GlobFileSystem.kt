package com.franzmandl.fileadmin.vfs

object GlobFileSystem : VirtualFileSystem {
    override fun getInode(finder: PathFinder, remaining: List<String>): Inode1<*> {
        val children = mutableSetOf<SafePath>()
        val inode = finder.parent.value
        if (inode.contentPermission.canDirectoryGet) {
            for (childPath in inode.children) {
                val child = finder.ctx.createPathFinder(childPath.resolve(remaining)).find()
                if (child.inode0.contentPermission.canDirectoryGet) {
                    children += child.inode0.children
                }
            }
        }
        return finder.build(VirtualDirectory.createFromFinderAndChildren(finder, VirtualDirectory.TreeOperation.Default, children), null)
    }
}
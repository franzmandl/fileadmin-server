package com.franzmandl.fileadmin.vfs

interface RootFileSystem {
    fun getInode(finder: PathFinder): Inode
}
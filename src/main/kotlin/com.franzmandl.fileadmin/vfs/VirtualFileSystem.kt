package com.franzmandl.fileadmin.vfs

interface VirtualFileSystem {
    fun getInode(finder: PathFinder, remaining: List<String>): Inode1<*>
}
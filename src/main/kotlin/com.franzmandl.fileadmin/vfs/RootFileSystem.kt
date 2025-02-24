package com.franzmandl.fileadmin.vfs

fun interface RootFileSystem {
    fun getInode(finder: PathFinder): Inode1<*>
}
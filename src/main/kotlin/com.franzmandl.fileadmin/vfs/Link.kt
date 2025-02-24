package com.franzmandl.fileadmin.vfs

class Link(
    val realTargetInode: Inode0,
    val target: UnsafePath,
    val targetInode: Inode1<*>,
    private val treeInode: TreeInode,
) : Inode0, ContentInode by realTargetInode, TreeInode by treeInode
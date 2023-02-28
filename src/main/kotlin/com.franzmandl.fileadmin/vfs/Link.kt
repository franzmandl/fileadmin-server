package com.franzmandl.fileadmin.vfs

class Link(
    val realTargetInode: Inode,
    val target: UnsafePath,
    val targetInode: Inode,
    private val treeInode: TreeInode,
) : Inode, ContentInode by realTargetInode, TreeInode by treeInode
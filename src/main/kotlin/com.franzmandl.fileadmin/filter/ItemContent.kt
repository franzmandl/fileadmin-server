package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.model.config.ConditionVersion1
import com.franzmandl.fileadmin.vfs.Inode
import com.franzmandl.fileadmin.vfs.PathFinder

object ItemContent {
    interface Visitor {
        val condition: ConditionVersion1
        val onError: (String) -> Unit
        val pruneNames: Set<String>
        fun onDirectory(inode: Inode): Boolean
        fun onFile(inode: Inode): Boolean
        fun onName(inode: Inode): Boolean
    }

    fun visit(ctx: PathFinder.Ctx, inode: Inode, visitor: Visitor) {
        visitInode(inode, visitor)
        if (inode.contentOperation.canDirectoryGet) {
            for (descendant in CommonUtil.getSequenceOfDescendants(ctx, inode, visitor.condition.defaultMinDepth - 1, visitor.condition.defaultMaxDepth - 1, visitor.pruneNames, visitor.onError)) {
                if(!visitor.onName(descendant)) {
                    return
                }
                visitInode(descendant, visitor)
            }
        }
    }

    private fun visitInode(inode: Inode, visitor: Visitor) {
        try {
            val commonCondition = visitor.condition.commonCondition.evaluate(inode.path)
            when {
                inode.contentOperation.canFileGet && (commonCondition || visitor.condition.fileCondition.evaluate(inode.path)) ->
                    if(!visitor.onFile(inode)) {
                        return
                    }

                inode.contentOperation.canDirectoryGet && (commonCondition || visitor.condition.directoryCondition.evaluate(inode.path)) ->
                    if(!visitor.onDirectory(inode)) {
                        return
                    }
            }
        } catch (e: HttpException) {
            visitor.onError(e.message)
        }
    }
}
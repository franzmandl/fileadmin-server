package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.vfs.Inode1
import com.franzmandl.fileadmin.vfs.PathCondition
import com.franzmandl.fileadmin.vfs.PathFinder

object ItemContent {
    interface Visitor {
        val condition: PathCondition
        val errorHandler: ErrorHandler
        val pruneNames: Set<String>
        fun onDirectory(inode: Inode1<*>): Boolean
        fun onFile(inode: Inode1<*>): Boolean
        fun onName(inode: Inode1<*>): Boolean
    }

    fun visit(ctx: PathFinder.Ctx, rootInode: Inode1<*>, visitor: Visitor) {
        for (inode in visitor.condition.getSequenceOfDescendants(
            ctx, rootInode,
            PathCondition.Parameter(createPayload = PathCondition.Parameter.createNullPayload, evaluatePatterns = false, errorHandler = visitor.errorHandler, rootPayload = null),
        )) {
            if (visitor.condition.isContentPruned(inode.inode1.inode0.path.name)) {
                continue
            }
            val component = inode.component
            if (component == null) {
                // inode is the root.
                val commonResult = visitor.condition.rootPatterns.common.evaluatePessimistic(inode.inode1.inode0.path)
                visitInode(
                    inode.inode1, visitor,
                    directoryResult = commonResult || visitor.condition.rootPatterns.directory.evaluatePessimistic(inode.inode1.inode0.path),
                    fileResult = commonResult || visitor.condition.rootPatterns.file.evaluatePessimistic(inode.inode1.inode0.path)
                )
            } else {
                if (!visitor.onName(inode.inode1)) {
                    return
                }
                val commonResult = component.patterns.common.evaluatePessimistic(inode.inode1.inode0.path)
                visitInode(
                    inode.inode1, visitor,
                    directoryResult = commonResult || component.patterns.directory.evaluatePessimistic(inode.inode1.inode0.path),
                    fileResult = commonResult || component.patterns.file.evaluatePessimistic(inode.inode1.inode0.path),
                )
            }
        }
    }

    private fun visitInode(inode: Inode1<*>, visitor: Visitor, directoryResult: Boolean, fileResult: Boolean) {
        try {
            when {
                fileResult && inode.inode0.contentOperation.canFileGet ->
                    if (!visitor.onFile(inode)) {
                        return
                    }

                directoryResult && inode.inode0.contentOperation.canDirectoryGet ->
                    if (!visitor.onDirectory(inode)) {
                        return
                    }
            }
        } catch (e: HttpException) {
            visitor.errorHandler.onError(e.message)
        }
    }
}
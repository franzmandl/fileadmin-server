package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.OptionalValue
import com.franzmandl.fileadmin.resource.RequestCtx
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.attribute.FileTime

class VirtualDirectory(
    private val childSet: ChildSet,
    override val contentOperation: ContentOperation,
    override val parent: OptionalValue<Inode0>,
    override val path: SafePath,
    override val treeOperation: TreeOperation,
) : Inode0 {
    override val children: Set<SafePath> get() = childSet.children
    override val contentPermission: ContentInode.Operation = contentOperation
    override fun copy(ctx: RequestCtx, target: Inode1<*>) = throw HttpException.notSupported().copy(path).to(target.inode0.path).build()
    override fun create(ctx: RequestCtx, isFile: Boolean) = throw HttpException.notSupported().create(path).build()
    override fun delete(ctx: RequestCtx) = treeOperation.delete(ctx, this)
    override val estimatedSizeOfDirectory = childSet.estimatedSize
    override val inputStream: InputStream get() = throw HttpException.notSupported().getFile(path).build()
    override val isDirectory: Boolean = true
    override val isFile: Boolean = false
    override val isVirtual: Boolean = true
    override val lastModified: FileTime? = null
    override val mimeType: String = CommonUtil.mimeTypeDirectory
    override fun move(ctx: RequestCtx, target: Inode1<*>) = treeOperation.move(ctx, this, target)
    override val outputStream: OutputStream get() = throw HttpException.notSupported().setFile(path).build()
    override val sizeOfDirectory: Int get() = childSet.size
    override val sizeOfFile: Long get() = throw HttpException.notSupported().sizeOfFile(path).build()
    override fun share(ctx: RequestCtx, days: Int): String = throw HttpException.notSupported().share(path).build()
    override fun stream(requestHeaders: HttpHeaders, responseHeaders: HttpHeaders): ResponseEntity<ResourceRegion> = throw HttpException.notSupported().stream(path).build()
    override val treePermission: TreeInode.Operation = treeOperation

    interface ChildSet {
        val children: Set<SafePath>
        val estimatedSize: Int?
        val size: Int

        class Simple(
            override val children: Set<SafePath>,
            override val estimatedSize: Int?,
            override val size: Int,
        ) : ChildSet {
            companion object {
                fun createSameSize(children: Set<SafePath>): Simple =
                    Simple(children, children.size, children.size)
            }
        }
    }

    object ContentOperation : ContentInode.Operation {
        override val canDirectoryAdd: Boolean = false
        override val canDirectoryGet: Boolean = true
        override val canFileGet: Boolean = false
        override val canFileSet: Boolean = false
        override val canFileStream: Boolean = false
        override val canInodeCopy: Boolean = false
        override val canInodeShare: Boolean = false
        override val canInodeToDirectory: Boolean = false
        override val canInodeToFile: Boolean = false
    }

    interface TreeOperation : TreeInode.Operation {
        override val canInodeDelete: Boolean get() = false
        override val canInodeMove: Boolean get() = false
        override val canInodeRename: Boolean get() = false

        fun delete(ctx: RequestCtx, inode: Inode0): Unit =
            throw HttpException.notSupported().delete(inode.path).build()

        fun move(ctx: RequestCtx, inode: Inode0, target: Inode1<*>): Unit =
            throw HttpException.notSupported().move(inode.path).to(target.inode0.path).build()

        object Default : TreeOperation
    }

    companion object {
        fun createFromFinderAndChildSet(
            finder: PathFinder,
            treeOperation: TreeOperation,
            childSet: ChildSet
        ): VirtualDirectory =
            VirtualDirectory(childSet, ContentOperation, finder.parent, finder.destination, treeOperation)

        fun createFromFinderAndChildren(
            finder: PathFinder,
            treeOperation: TreeOperation,
            children: Set<SafePath>
        ): VirtualDirectory =
            createFromFinderAndChildSet(finder, treeOperation, ChildSet.Simple.createSameSize(children))

        fun createFromFinderAndNames(finder: PathFinder, treeOperation: TreeOperation, names: Iterable<String>): VirtualDirectory =
            VirtualDirectory(
                ChildSet.Simple.createSameSize(names.mapTo(mutableSetOf()) { finder.destination.resolve(it) }),
                ContentOperation, finder.parent, finder.destination, treeOperation,
            )
    }
}
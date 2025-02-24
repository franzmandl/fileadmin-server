package com.franzmandl.fileadmin

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.dto.NewInode
import com.franzmandl.fileadmin.filter.Item
import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.vfs.*
import org.assertj.core.api.Assertions.fail
import java.io.File
import java.nio.file.Path

object TestUtil {
    val failErrorHandler = ErrorHandler { fail<Nothing>("An error occurred: $it") }
    val jail4Example1ItemPaths = setOf(
        "/example1/input1/2022/2022-11-22 - #desktop1",
        "/example1/input1/2022/2022-11-22 - content",
        "/example1/input1/2022/2022-11-22 - #device.txt",
        "/example1/input1/2022/2022-11-22 - #FranzMandl.txt",
        "/example1/input1/2022/2022-11-22 - #import1.txt",
        "/example1/input1/2022/2022-11-22 - #import2.txt",
        "/example1/input1/2022/2022-11-22 - #import3.txt",
        "/example1/input1/2022/2022-11-22 - #legacy.txt",
        "/example1/input1/2022/2022-11-22 - #me.txt",
        "/example1/input1/2022/2022-11-22 - #person.txt",
        "/example1/input1/2022/2022-11-22 - #prune.txt",
        "/example1/input1/2022/2022-11-22 - #unknown1#unknown2.txt",
        "/example1/input1/2022/2022-11-22 - #unknown1.txt",
        "/example1/input1/2022/2022-11-22 - #unknown2.txt",
        "/example1/input1/2022/2022-11-22 - content1.txt",
        "/example1/input1/2022/2022-11-22 - content2.txt",
        "/example1/input1/2022/2022-11-22 - untagged.txt",
        "/example1/input2/#phone1.js",
        "/example1/input3/2022-11-22 - task-20 #BacklogTask.txt",
        "/example1/input3/2022-11-22 - task-40 #To_DoTask.txt",
        "/example1/input3/2022-11-22 - task-60 #DoneTask.txt",
        "/example1/input3/2022-11-22 - task-80 #AbortedTask.txt",
        "/example1/input3/2022-11-22R - task-40 #To_DoTask.txt",
        "/example1/input3/2022-11-22R1D - task-40 #To_DoTask.txt",
    )
    val systemTags = mapOf(
        "!lostAndFound" to null,
        "!unknown" to null,
        "directory" to null,
        "emptyContent" to null,
        "emptyName" to null,
        "emptyParentPath" to null,
        "file" to null,
        "input" to null,
        "TaskStatus" to mapOf("DoneTask" to null),
        "prune" to null,
    )

    class ListErrorHandler : ErrorHandler {
        val errors = mutableListOf<String>()

        override fun onError(message: String): Nothing? {
            errors.add(message)
            return null
        }
    }

    fun createNativeInode(string: String): Inode1<NativeInode> =
        Inode1(
            nativeInodeConfig, NativeInode(
                Path.of(string),
                Inode0.parentOfRoot,
                SafePath(
                    when {
                        string.startsWith(File.separator) -> string
                        string.startsWith(".${File.separator}") -> string.substring(1)
                        string == "." -> File.separator
                        else -> "${File.separator}$string"
                    }
                ),
            )
        )

    fun pathToUri(path: SafePath): String =
        CommonUtil.stringToUri(path.toString())

    private val nativeInodeConfig = Inode1.Config(
        errors = listOf(),
        filter = null,
        filterResult = null,
        isRunLast = false,
        isTask = false,
        nameCursorPosition = 0,
        newInodeTemplate = NewInode(true, ""),
        samePaths = setOf(),
        stepchildren = setOf(),
    )

    operator fun <E> List<E>.times(i: Int): List<E> {
        val result = ArrayList<E>(size * i)
        for (ignore in 0..<i) {
            result += this
        }
        return result
    }

    fun visitedInodeToString(inode: PathCondition.VisitedInode<List<String>>) =
        "${
            when (inode) {
                is PathCondition.LeafInode -> "leaf"
                is PathCondition.ExternalInode -> "external"
                is PathCondition.InternalInode -> "internal"
            }
        }:${inode.inode1.inode0.path.absoluteString}${if (inode is PathCondition.ExternalInode) inode.payload.joinToString("/", ":") else ""}"

    class TagHierarchy : HashMap<String, TagHierarchy?>() {
        fun toKotlinCode(): String = toKotlinCode(StringBuilder()).toString()

        private fun toKotlinCode(builder: StringBuilder): StringBuilder {
            builder.append("mapOf(")
            for ((key, value) in entries) {
                builder.append('"').append(key).append('"').append(" to ")
                if (value != null) {
                    value.toKotlinCode(builder)
                } else {
                    builder.appendLine("null,")
                }
            }
            return builder.appendLine("),")
        }
    }

    fun getTagHierarchy(tags: Collection<Tag>): TagHierarchy {
        val builder = TagHierarchy()
        for (tag in tags) {
            if (tag.parents.isEmpty() && tag.isAParents?.isEmpty() != false) {
                builder[tag.friendlyName] = getTagHierarchy(tag)
            }
        }
        return builder
    }

    fun getTagHierarchy(tag: Tag): TagHierarchy? {
        val builder = TagHierarchy()
        for (child in tag.getSequenceOfChildren(Tag.ChildrenParameter.all)) {
            builder[child.friendlyName] = getTagHierarchy(child)
        }
        return builder.takeIf { it.isNotEmpty() }
    }

    fun getItemPath(item: Item): String =
        item.inode.inode0.path.absoluteString
}
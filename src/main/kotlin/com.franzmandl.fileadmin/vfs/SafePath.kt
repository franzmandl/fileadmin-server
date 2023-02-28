package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.HttpException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File
import java.nio.file.Path
import java.util.*

/**
 * Is a relative path to some root directory.
 * Is separated by separatorChar.
 * Does not contain '.' or '..'.
 * Never starts or ends with separatorChar.
 * A serialized safe path is a string with a leading '/' and therefore appears absolute.
 * Example: paths from URL
 */
@Serializable(with = SafePath.Serializer::class)
data class SafePath(
    val parts: List<String>,
) {
    constructor(string: String) : this(validateString(string))

    val absoluteString = "/" + parts.joinToString("/")
    val array = parts.toTypedArray()
    val relativeString = parts.joinToString(File.separator)

    fun forceParent(): SafePath =
        parent ?: throw IllegalStateException("Parent was null.")

    fun forceResolve(path: UnsafePath): SafePath =
        resolve(path) ?: throw HttpException.badRequest("Illegal path: '$path' breaks out of jail.")

    val isRoot: Boolean
        get() = parts.isEmpty()

    /**
     * @return "" if isRoot
     */
    val name: String
        get() = parts.lastOrNull() ?: ""

    val parent: SafePath?
        get() = if (isRoot) null else SafePath(parts.subList(0, parts.lastIndex))

    /**
     * @return null, if breaks out of jail.
     */
    fun resolve(path: UnsafePath): SafePath? {
        if (path.isAbsolute) {
            return null
        }
        val localPath = Path.of(relativeString).resolve(path.publicLocalPath).normalize()
        if (localPath.nameCount > 0 && localPath.getName(0) == PathUtil.parentLocalPath) {
            return null
        }
        return SafePath(splitLocalPath(localPath))
    }

    /**
     * @param name Must already have been validated.
     */
    fun resolve(name: String): SafePath =
        SafePath(LinkedList(parts).apply { add(name) })

    /**
     * @param names Must already have been validated.
     */
    fun resolve(names: List<String>): SafePath =
        if (names.isEmpty()) this else SafePath(parts + names)

    /**
     * @param names Must already have been validated.
     */
    fun resolveSibling(name: String): SafePath =
        SafePath(LinkedList(parts).apply {
            if (isEmpty()) {
                throw IllegalStateException("Parts are empty.")
            }
            set(lastIndex, name)
        })

    fun startsWith(ancestor: SafePath): Boolean {
        if (parts.size < ancestor.parts.size) {
            return false
        }
        for (index in ancestor.parts.indices) {
            if (parts[index] != ancestor.parts[index]) {
                return false
            }
        }
        return true
    }

    override fun toString(): String =
        absoluteString

    object Serializer : KSerializer<SafePath> {
        override val descriptor = PrimitiveSerialDescriptor(SafePath::class.toString(), PrimitiveKind.STRING)
        override fun deserialize(decoder: Decoder) = SafePath(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: SafePath) = encoder.encodeString(value.absoluteString)
    }

    companion object {
        private val antiRegex = Regex("^$|^[^${PathUtil.separator.pattern}]|[${PathUtil.separator.pattern}]\\.{1,2}[${PathUtil.separator.pattern}]|[${PathUtil.separator.pattern}]\\.{1,2}$")

        private fun validateString(string: String): List<String> {
            val slashString = PathUtil.separator.replaceTo(string)
            if (antiRegex.containsMatchIn(slashString)) {
                throw HttpException.badRequest("Illegal path: '$string' matches anti pattern.")
            }
            val localPath = Path.of(PathUtil.separator.replaceFrom(slashString.trim('/')))
            if (localPath.isAbsolute) {
                // Might happen on Windows.
                throw HttpException.badRequest("Illegal path: '$string' is absolute.")
            }
            return splitLocalPath(localPath)
        }

        private fun splitLocalPath(localPath: Path): List<String> {
            val localPathString = localPath.toString()
            return if (localPathString.isEmpty()) listOf() else localPathString.split(File.separator)
        }
    }
}
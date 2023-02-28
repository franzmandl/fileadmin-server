package com.franzmandl.fileadmin.vfs

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path

/**
 * Can be absolute or relative, outside or inside the jail.
 * Is separated by separatorChar, but might also contain '/' or '\'.
 * Might contain '.' or '..'.
 * Example: paths from config files, symlink targets
 */
@Serializable(with = UnsafePath.Serializer::class)
data class UnsafePath(
    private val localPath: Path,
) {
    constructor(string: String) : this(Path.of(string))

    val isAbsolute: Boolean
        get() = localPath.isAbsolute

    /**
     * Should only be used in exceptional cases.
     */
    val publicLocalPath = localPath

    override fun toString(): String =
        localPath.toString()

    object Serializer : KSerializer<UnsafePath> {
        override val descriptor = PrimitiveSerialDescriptor(UnsafePath::class.toString(), PrimitiveKind.STRING)
        override fun deserialize(decoder: Decoder) = UnsafePath(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: UnsafePath) = encoder.encodeString(value.localPath.toString())
    }
}
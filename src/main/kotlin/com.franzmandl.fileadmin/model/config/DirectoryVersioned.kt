package com.franzmandl.fileadmin.model.config

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.vfs.SafePath
import com.franzmandl.fileadmin.vfs.UnsafePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.nio.file.Path
import java.nio.file.PathMatcher

@Serializable
sealed class ConfigRoot {
    abstract val imports: List<UnsafePath?>?
}

@Serializable
sealed class ConfigValue : ConfigRoot()

@Serializable
sealed class DirectoryVersioned : ConfigRoot() {
    abstract fun toLatestVersion(): DirectoryVersion1
}

@Serializable
@SerialName("DirectoryVersion1")
data class DirectoryVersion1(
    val filter: FilterVersioned? = null,
    override val imports: List<UnsafePath?>? = null,
    val tags: List<TagVersioned?>? = null,
    val values: List<ConfigValue?>? = null,
) : DirectoryVersioned() {
    override fun toLatestVersion(): DirectoryVersion1 = this
}

@Serializable
sealed class DirectoryValueVersioned : ConfigValue() {
    abstract fun toLatestVersion(): DirectoryValueVersion1
}

@Serializable
@SerialName("DirectoryValueVersion1")
data class DirectoryValueVersion1(
    val condition: ConditionVersioned? = null,
    override val imports: List<UnsafePath?>? = null,
    val nameCursorPosition: Int? = null,
    val newInodeTemplate: NewInodeVersioned? = null,
    val runLast: RunLastVersioned? = null,
    val tasks: TasksVersioned? = null,
) : DirectoryValueVersioned() {
    override fun toLatestVersion(): DirectoryValueVersion1 = this
}

@Serializable
sealed class ConditionVersioned {
    abstract fun toLatestVersion(): ConditionVersion1
}

class CompiledCondition(
    val nameGlobs: List<PathMatcher>?,
    val nameRegex: Regex?,
    val pathRegex: Regex?,
) {
    constructor(
        nameGlobString: String?,
        nameRegexString: String?,
        pathRegexString: String?,
    ) : this(
        nameGlobString?.split(orSeparator)?.map { CommonUtil.createGlob(it) },
        nameRegexString?.let { Regex(it) },
        pathRegexString?.let { Regex(it) },
    )

    fun evaluate(path: SafePath): Boolean =
        nameGlobs?.let { CommonUtil.evaluateGlobs(it, Path.of(path.name)) } ?: false
                || nameRegex?.containsMatchIn(path.name) ?: false
                || pathRegex?.containsMatchIn(path.absoluteString) ?: false

    companion object {
        private const val orSeparator = '|'
    }
}

@Serializable
@SerialName("ConditionVersion1")
data class ConditionVersion1(
    val directoryNameGlob: String? = null,
    val directoryNameRegex: String? = null,
    val directoryPathRegex: String? = null,
    val fileNameGlob: String? = null,
    val fileNameRegex: String? = null,
    val filePathRegex: String? = null,
    /**
     * 0 ... The parent directory of the config file.
     * 1 ... Siblings of the config file and config file itself.
     * 2 ... And so forth.
     */
    val nameGlob: String? = null,
    val nameRegex: String? = null,
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
    val pathRegex: String? = null,
) : ConditionVersioned() {
    @Transient
    val commonCondition = CompiledCondition(nameGlob, nameRegex, pathRegex)

    @Transient
    val directoryCondition = CompiledCondition(directoryNameGlob, directoryNameRegex, directoryPathRegex)

    @Transient
    val fileCondition = CompiledCondition(fileNameGlob, fileNameRegex, filePathRegex)

    @Transient
    val defaultMinDepth = minDepth ?: 0

    @Transient
    val defaultMaxDepth = maxDepth ?: defaultMinDepth

    override fun toLatestVersion(): ConditionVersion1 = this

    companion object {
        val default = ConditionVersion1()
    }
}

@Serializable
sealed class NewInodeVersioned : ConfigValue() {
    abstract fun toLatestVersion(): NewInodeVersion1
}

@Serializable
@SerialName("NewInodeVersion1")
data class NewInodeVersion1(
    val condition: ConditionVersioned? = null,
    val isFile: Boolean? = null,
    val name: String? = null,
    override val imports: List<UnsafePath?>? = null,
) : NewInodeVersioned() {
    override fun toLatestVersion(): NewInodeVersion1 = this
}

@Serializable
sealed class RunLastVersioned : ConfigValue() {
    abstract fun toLatestVersion(): RunLastVersion1
}

@Serializable
@SerialName("RunLastVersion1")
data class RunLastVersion1(
    val condition: ConditionVersioned? = null,
    val enable: Boolean? = null,
    override val imports: List<UnsafePath?>? = null,
) : RunLastVersioned() {
    override fun toLatestVersion(): RunLastVersion1 = this
}

@Serializable
sealed class TasksVersioned : ConfigValue() {
    abstract fun toLatestVersion(): TasksVersion1
}

@Serializable
@SerialName("TasksVersion1")
data class TasksVersion1(
    val condition: ConditionVersioned? = null,
    val enable: Boolean? = null,
    override val imports: List<UnsafePath?>? = null,
) : TasksVersioned() {
    override fun toLatestVersion(): TasksVersion1 = this
}
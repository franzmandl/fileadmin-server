package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.ErrorHandler
import java.util.concurrent.ConcurrentHashMap

interface TagRegistry {
    val pruneContentRegex: Regex
    val systemTags: SystemTags<Tag.Mutable>
    fun getTag(name: String): Tag.Mutable?
    fun getOrCreateTag(
        name: String,
        parameter: Tag.Parameter,
        errorHandler: ErrorHandler,
        vararg kwargs: Unit,
        suggestMinimumLength: Int? = null,
        suggestName: String? = null,
        onInit: ((Tag.Mutable) -> Unit)? = null,
    ): Tag.Mutable

    class Phase1(
        private val coerceSuggestMinimumLength: (name: String, suggestMinimumLength: Int?) -> Int,
        errorHandler: ErrorHandler,
        systemTagNames: SystemTags<String>,
    ) : TagRegistry {
        private val mutableTags = ConcurrentHashMap<String, Tag.Mutable>()
        override val systemTags = SystemTags.map(systemTagNames, Tag.Parameter.system) { name, parameter -> getOrCreateTag(name, parameter, errorHandler) }
        override val pruneContentRegex = Regex("${Regex.escape("${FilterFileSystem.tagPrefix}${systemTags.prune.name}")}(?!${FilterFileSystem.tagNameEndingLetterRegex})")

        init {
            systemTags.taskDone.addParent(systemTags.task, false)
        }

        override fun getTag(name: String): Tag.Mutable? = mutableTags[name]

        override fun getOrCreateTag(
            name: String,
            parameter: Tag.Parameter,
            errorHandler: ErrorHandler,
            vararg kwargs: Unit,
            suggestMinimumLength: Int?,
            suggestName: String?,
            onInit: ((Tag.Mutable) -> Unit)?,
        ): Tag.Mutable =
            getTag(name)?.also {
                it.parameter = it.parameter.merge(parameter)
                if (suggestName != null) {
                    if (it.suggestName == null) {
                        it.suggestName = suggestName
                    } else if (it.suggestName != suggestName) {
                        errorHandler.onError("""SuggestName was already set to "$suggestName" for tag "$name".""")
                    }
                }
            } ?: Tag.Mutable(name, parameter, coerceSuggestMinimumLength(name, suggestMinimumLength), suggestName).also {
                mutableTags[name] = it
                if (onInit != null) {
                    onInit(it)
                }
            }

        fun startPhase2(): Phase2 =
            Phase2(this, mutableTags, (systemTags.prune.getSequenceOfChildren(Tag.ChildrenParameter.all) + systemTags.prune.getSequenceOfTwins(true)).toSet())
    }

    class Phase2(
        phase1: Phase1,
        private val mutableTags: MutableMap<String, Tag.Mutable>,
        val pruneSystemTags: Set<Tag>,
    ) : TagRegistry by phase1 {
        val tags: Map<String, Tag> = mutableTags

        fun clearUnknownIf(condition: Boolean) {
            if (condition) {
                for (tag in systemTags.unknown.getSequenceOfChildren(Tag.ChildrenParameter.defined)) {
                    mutableTags.remove(tag.name)
                }
                systemTags.unknown.clearChildren(Tag.ChildrenParameter.defined)
            }
        }

        fun check(errorHandler: ErrorHandler) {
            for (tag in mutableTags.values) {
                if (tag.parameter.isPlaceholder) {
                    errorHandler.onError("""Tag "${tag.name}" is still a placeholder and was defined in config files: ${tag.getConfigFiles().keys.joinToString { it.absoluteString }}""")
                }
            }
        }
    }
}
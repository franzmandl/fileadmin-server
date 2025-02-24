package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.dto.config.InodeTagPartVersion1
import com.franzmandl.fileadmin.dto.config.InodeTagVersioned
import com.franzmandl.fileadmin.dto.config.Mapper
import com.franzmandl.fileadmin.dto.config.TagVersion1
import com.franzmandl.fileadmin.resource.RequestCtx
import com.franzmandl.fileadmin.vfs.Inode1
import com.franzmandl.fileadmin.vfs.PathCondition

class InodeTag(
    private val partsList: List<List<Part>>,
    val rootTag: Tag.Mutable,
) {
    fun getOrCreateTag(ctx: RequestCtx, registry: TagRegistry, parent: Tag.Mutable, inode: Inode1<*>, component: PathCondition.Component, errorHandler: ErrorHandler): Tag.Mutable {
        var tag = parent
        for (part in partsList[component.index]) {
            tag = getOrCreateTag(part, ctx, registry, tag, inode, component, errorHandler)
        }
        return tag
    }

    private fun getOrCreateTag(
        part: Part,
        ctx: RequestCtx,
        registry: TagRegistry,
        parent: Tag.Mutable,
        inode: Inode1<*>,
        component: PathCondition.Component,
        errorHandler: ErrorHandler,
    ): Tag.Mutable {
        val name = FilterFileSystem.toTagName(CommonUtil.replaceChecked(part.regex, inode.inode0.path.name, part.template.name).let {
            if (it == null) {
                if (part.checkReplacement) {
                    errorHandler.onError("${inode.inode0.path}: Name was not replaced at component index ${component.index}, part index ${part.index}.")
                }
                return@getOrCreateTag parent
            } else it
        })
        val suggestName = part.latest.suggestNameReplacement?.let { suggestNameReplacement ->
            FilterFileSystem.toTagName(CommonUtil.replaceChecked(part.regex, inode.inode0.path.name, suggestNameReplacement).let {
                if (it == null) {
                    if (part.checkReplacement) {
                        errorHandler.onError("${inode.inode0.path}: Suggest name was not replaced at component index ${component.index}, part index ${part.index}.")
                    }
                    return@getOrCreateTag parent
                } else it
            })
        }
        val prependParent = part.latest.prependParent ?: (component.index != 0 || part.index != 0)
        val prefix = if (prependParent) parent.suggestName ?: parent.name else ""
        return registry.getOrCreateTag(
            prefix + name, part.parameter, errorHandler,
            suggestMinimumLength = prefix.length + coerceSuggestMinimumLength(ctx, part.latest, suggestName ?: name),
            suggestName = if (suggestName != null) prefix + suggestName else null,
        ).apply {
            addParent(parent, false)
        }
    }

    private fun coerceSuggestMinimumLength(ctx: RequestCtx, part: InodeTagPartVersion1, name: String): Int =
        ctx.application.filter.coerceSuggestMinimumLength(name, part.suggestMinimumLength ?: ctx.application.filter.inodeTagOperandSuggestMinimumLength)

    class Part(
        val checkReplacement: Boolean,
        val index: Int,
        val latest: InodeTagPartVersion1,
        val parameter: Tag.Parameter,
        val regex: Regex,
        val template: TagVersion1,
    ) {
        companion object {
            private val knownPatterns = mapOf(
                "standard" to Regex("""^(.*?)$"""),
                "withoutEnding" to Regex("""^(.*?)(?:\.[^.]{2,})?$"""),
            )

            fun createRegex(pattern: String): Regex =
                if (pattern.startsWith("^") && pattern.endsWith("$")) {
                    Regex(pattern)
                } else {
                    knownPatterns[pattern] ?: throw IllegalArgumentException("Illegal pattern: $pattern")
                }
        }
    }

    companion object {
        fun create(mapper: Mapper, versioned: InodeTagVersioned?, size: Int, rootTag: Tag.Mutable, errorHandler: ErrorHandler): InodeTag? {
            val latest = mapper.fromVersioned(versioned ?: return null).takeIf { it.enabled != false } ?: return null
            val partsArray = Array<MutableList<Part>>(size) { mutableListOf() }
            for (versionedPart in latest.parts ?: listOf()) {
                val part = mapper.fromVersioned(versionedPart ?: continue)
                if (part.index in partsArray.indices) {
                    val latestTemplate = mapper.fromVersioned(part.template)
                    val parts = partsArray[part.index]
                    parts += Part(
                        checkReplacement = part.checkReplacement ?: true, index = parts.size, latest = part,
                        parameter = Tag.Parameter.standard.copy(latestTemplate),
                        regex = Part.createRegex(part.pattern), template = latestTemplate,
                    )
                } else {
                    errorHandler.onError("Index out of bounds (index=${part.index}, size=$size)")
                }
            }
            return InodeTag(partsArray.asList(), rootTag)
        }
    }
}
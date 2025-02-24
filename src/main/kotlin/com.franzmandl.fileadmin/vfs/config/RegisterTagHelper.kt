package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.dto.config.TagRuleVersion1
import com.franzmandl.fileadmin.dto.config.TagVersion1
import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.filter.TagRegistry
import com.franzmandl.fileadmin.vfs.Inode0
import com.franzmandl.fileadmin.vfs.PathFinder
import com.franzmandl.fileadmin.vfs.SafePath

class RegisterTagHelper(
    private val ctx: PathFinder.Ctx,
) {
    fun registerTags(registry: TagRegistry, configTags: Iterable<ConfigTag>) =
        registerTags(registry, configTags, listOf(), setOf(), true, mapOf())

    private fun registerTags(
        registry: TagRegistry,
        configTags: Iterable<ConfigTag>,
        implicitParents: List<Tag.Mutable>,
        descendantsRules: Set<TagRuleVersion1>,
        isRoot: Boolean,
        relationshipParentsMap: Map<String, Collection<Tag.Mutable>>,
    ) {
        for (configTag in configTags) {
            val linkedTags = createLinkedTags(registry, configTag.configFile, configTag.latest, descendantsRules)
            val symbolResolver = linkedTags.createTagSymbolResolver(implicitParents, configTag.latest.name)
            val tags = getOrCreateTags(
                registry,
                TagNameHelper.getSequenceOfAliases(
                    ctx, configTag.configFile.path, configTag.latest.name,
                    TagNameHelper.createNameResolverWithSymbolResolver(symbolResolver)
                ),
                configTag.configFile, if (isRoot) Tag.Parameter.root else Tag.Parameter.standard, latest = configTag.latest
            ).toList()
            val mutableRelationshipParentsMap = relationshipParentsMap.toMutableMap()
            for (relationship in configTag.relationships) {
                val definition = ctx.filterRelationshipDefinitions[relationship.name]
                if (definition == null) {
                    ctx.onError("""${configTag.configFile.path.absoluteString}: Unknown relationship "${relationship.name}".""")
                    continue
                }
                for (tag in tags) {
                    handleRelationship(registry, configTag.configFile, tag, definition, relationship.tags, mutableRelationshipParentsMap)
                }
            }
            linkedTags.link(tags, configTag.configFile.path, configTag.latest, implicitParents)
            registerTags(registry, configTag.children, tags, CommonUtil.appendNullable(descendantsRules, configTag.latest.descendantsRules), false, mutableRelationshipParentsMap)
        }
    }

    private fun createLinkedTags(
        registry: TagRegistry,
        configFile: Inode0,
        latest: TagVersion1,
        descendantsRules: Set<TagRuleVersion1>,
    ): LinkedTags {
        val isATagsList = registerIsA(registry, configFile, latest, descendantsRules)
        val explicitParentsList = registerParents(registry, configFile, getParentNames(configFile, latest, descendantsRules))
        val overrideParentsList = latest.overrideParents?.let { registerParents(registry, configFile, it) }
        return LinkedTags(ctx, explicitParentsList, isATagsList, overrideParentsList)
    }

    private fun getParentNames(configFile: Inode0, latest: TagVersion1, rules: Set<TagRuleVersion1>): List<String> {
        if (TagRuleVersion1.ParentsNonNull in rules && latest.parents == null) {
            ctx.onError("""${configFile.path.absoluteString}: parents is null for tag "${latest.name}".""")
        }
        return latest.parents ?: listOf()
    }

    private fun registerParents(registry: TagRegistry, configFile: Inode0, names: List<String>): List<List<Tag.Mutable>> {
        val parents = mutableListOf<List<Tag.Mutable>>()
        for (aliases in names) {
            parents += getOrCreateTags(
                registry,
                TagNameHelper.getSequenceOfAliases(ctx, configFile.path, aliases, TagNameHelper.nameResolverWithValidation),
                configFile, Tag.Parameter.placeholder, latest = null
            ).toList()
        }
        return parents
    }

    private fun registerIsA(registry: TagRegistry, configFile: Inode0, latest: TagVersion1, rules: Set<TagRuleVersion1>): List<List<Tag.Mutable>> {
        val isATags = mutableListOf<List<Tag.Mutable>>()
        if (TagRuleVersion1.IsANonNull in rules && latest.isA == null) {
            ctx.onError("""${configFile.path.absoluteString}: isA is null for tag "${latest.name}".""")
        }
        for (aliases in latest.isA ?: listOf()) {
            isATags += getOrCreateTags(
                registry,
                TagNameHelper.getSequenceOfAliases(ctx, configFile.path, aliases, TagNameHelper.nameResolverWithValidation),
                configFile, Tag.Parameter.placeholder, latest = null
            ).toList()
        }
        return isATags
    }

    private fun handleRelationship(
        registry: TagRegistry,
        configFile: Inode0,
        subject: Tag.Mutable,
        definition: ConfigRelationshipDefinition,
        configTags: List<ConfigTag>,
        parentsMap: MutableMap<String, Collection<Tag.Mutable>>,
    ) {
        val latest = definition.template.copy(canRename = false)
        val linkedTags = createLinkedTags(registry, configFile, latest, setOf())
        val symbolResolver =
            RelationshipSymbolResolver(definition, subject.name, linkedTags.createTagSymbolResolver(listOf(), latest.name))
        val tags = getOrCreateTags(
            registry,
            TagNameHelper.getSequenceOfAliases(ctx, configFile.path, latest.name, TagNameHelper.createNameResolverWithSymbolResolver(symbolResolver)),
            configFile, Tag.Parameter.standard, latest = latest,
        ).toList()
        val implicitParents = mutableListOf<Tag.Mutable>()
        if (definition.latest.roots != null) {
            val relationshipParents = parentsMap[definition.latest.name]
            if (relationshipParents != null) {
                implicitParents += relationshipParents
            } else {
                for (aliases in definition.latest.roots) {
                    implicitParents += getOrCreateTags(
                        registry,
                        TagNameHelper.getSequenceOfAliases(ctx, configFile.path, aliases, TagNameHelper.nameResolverWithValidation),
                        configFile, Tag.Parameter.placeholder, latest = null
                    )
                }
            }
            parentsMap[definition.latest.name] = tags
        }
        if (definition.latest.addSubjectAsChild == true) {
            LinkTagHelper.linkParents(configFile.path, listOf(subject), tags, false, ctx)
        }
        if (definition.latest.addSubjectAsParent == true) {
            LinkTagHelper.linkParents(configFile.path, tags, listOf(subject), false, ctx)
        }
        linkedTags.link(tags, configFile.path, latest, implicitParents)
        registerTags(registry, configTags, tags, definition.template.descendantsRules ?: setOf(), false, mapOf())
    }

    fun getOrCreateTags(
        registry: TagRegistry,
        aliases: Sequence<String>,
        configFile: Inode0,
        parameter: Tag.Parameter,
        @Suppress("UNUSED_PARAMETER") vararg kwargs: Unit,
        latest: TagVersion1?,
    ): Sequence<Tag.Mutable> {
        var twin: Tag.Mutable? = null
        return aliases.map { name ->
            val tag = getOrCreateTag(registry, name, configFile.path, parameter, latest = latest).addConfigFile(configFile)
            twin?.let { LinkTagHelper.linkTwin(tag, it) }
            twin = tag
            tag
        }
    }

    fun getOrCreateTag(
        registry: TagRegistry,
        name: String,
        configPath: SafePath,
        parameter: Tag.Parameter,
        @Suppress("UNUSED_PARAMETER") vararg kwargs: Unit,
        latest: TagVersion1?,
    ): Tag.Mutable {
        var wasCreated = false
        val tag = registry.getOrCreateTag(name, parameter.copy(latest), ctx, suggestMinimumLength = latest?.suggestMinimumLength) { wasCreated = true }
        if (!wasCreated && tag.parameter.isExclusive) {
            ctx.onError("""${configPath.absoluteString}: "$name" is an exclusive tag.""")
        }
        if (wasCreated && latest?.exists == true) {
            ctx.onError("""${configPath.absoluteString}: tag "$name" did not exist before.""")
        }
        if (!wasCreated && latest?.first == true) {
            ctx.onError("""${configPath.absoluteString}: tag "$name" existed before.""")
        }
        return tag
    }
}
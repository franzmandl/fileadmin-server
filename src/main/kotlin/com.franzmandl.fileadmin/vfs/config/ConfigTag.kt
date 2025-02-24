package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.dto.config.Mapper
import com.franzmandl.fileadmin.dto.config.TagVersion1
import com.franzmandl.fileadmin.dto.config.TagVersioned
import com.franzmandl.fileadmin.vfs.Inode0

class ConfigTag(
    val children: List<ConfigTag>,
    val configFile: Inode0,
    val latest: TagVersion1,
    val relationships: List<ConfigRelationship>,
) {
    companion object {
        fun create(configFile: Inode0, mapper: Mapper, versioned: TagVersioned?): ConfigTag? {
            val latest = mapper.fromVersioned(versioned ?: return null)
            if (latest.enabled == false) {
                return null
            }
            val relationships = latest.relationships?.entries?.map { (name, tags) -> ConfigRelationship(name, mapVersionedTags(configFile, mapper, tags)) } ?: listOf()
            return ConfigTag(mapVersionedTags(configFile, mapper, latest.children), configFile, latest, relationships)
        }

        private fun mapVersionedTags(configFile: Inode0, mapper: Mapper, tags: List<TagVersioned?>?) =
            tags?.mapNotNull { create(configFile, mapper, it) } ?: listOf()
    }
}
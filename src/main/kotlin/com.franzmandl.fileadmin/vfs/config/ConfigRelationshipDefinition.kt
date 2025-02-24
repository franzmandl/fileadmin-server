package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.dto.config.Mapper
import com.franzmandl.fileadmin.dto.config.RelationshipDefinitionVersion1
import com.franzmandl.fileadmin.dto.config.RelationshipDefinitionVersioned
import com.franzmandl.fileadmin.dto.config.TagVersion1
import com.franzmandl.fileadmin.vfs.Inode0

class ConfigRelationshipDefinition(
    val configFile: Inode0,
    val latest: RelationshipDefinitionVersion1,
    val template: TagVersion1,
) {
    companion object {
        fun create(configFile: Inode0, mapper: Mapper, versioned: RelationshipDefinitionVersioned): ConfigRelationshipDefinition {
            val latest = mapper.fromVersioned(versioned)
            return ConfigRelationshipDefinition(configFile, latest, mapper.fromVersioned(latest.template))
        }
    }
}
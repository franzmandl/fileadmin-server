package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.dto.config.Mapper
import com.franzmandl.fileadmin.dto.config.OperationVersion1
import com.franzmandl.fileadmin.dto.config.OperationVersioned
import com.franzmandl.fileadmin.vfs.Inode0

class ConfigOperation(
    val configFile: Inode0,
    val latest: OperationVersion1,
) {
    companion object {
        fun create(configFile: Inode0, mapper: Mapper, versioned: OperationVersioned?): ConfigOperation? {
            val latest = mapper.fromVersioned(versioned ?: return null)
            return if (latest.enabled == false) null else ConfigOperation(configFile, latest)
        }
    }
}
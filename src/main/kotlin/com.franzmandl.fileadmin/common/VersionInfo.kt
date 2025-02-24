package com.franzmandl.fileadmin.common

import com.franzmandl.fileadmin.generated.GitInfo

object VersionInfo {
    private val version = "branch=${GitInfo.branch} shortHash=${GitInfo.shortHash} tags=[${GitInfo.tags.joinToString(",")}]"
    val banner = "FileAdmin $version"
}
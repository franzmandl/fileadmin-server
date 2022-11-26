package com.franzmandl.fileadmin.model.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RunLastVersioned

@Serializable
@SerialName("RunLastVersion1")
object RunLastVersion1 : RunLastVersioned()
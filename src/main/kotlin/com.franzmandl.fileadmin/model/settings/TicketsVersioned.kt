package com.franzmandl.fileadmin.model.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class TicketsVersioned

@Serializable
@SerialName("TicketsVersion1")
object TicketsVersion1 : TicketsVersioned()
package com.franzmandl.fileadmin.model.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SettingsVersioned {
    abstract val basenameCursorPosition: Int?
    abstract val newInodeTemplate: NewInodeVersioned?
    abstract val runLast: RunLastVersioned?
    abstract val tickets: TicketsVersioned?
    abstract val children: SettingsVersioned?
}

@Serializable
@SerialName("SettingsVersion1")
data class SettingsVersion1(
    override val basenameCursorPosition: Int? = null,
    override val newInodeTemplate: NewInodeVersioned? = null,
    override val runLast: RunLastVersioned? = null,
    override val tickets: TicketsVersioned? = null,
    override val children: SettingsVersioned? = null,
) : SettingsVersioned()
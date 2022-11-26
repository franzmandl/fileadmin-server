package com.franzmandl.fileadmin.model

import com.franzmandl.fileadmin.Config
import com.franzmandl.fileadmin.model.settings.SettingsVersioned
import com.franzmandl.fileadmin.util.JsonFormat
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Settings(
    val basenameCursorPosition: Int,
    val newInodeTemplate: NewInode,
    val isRunLast: Boolean,
    val isTickets: Boolean,
) {
    companion object {
        fun create(config: Config, file: File?): Settings {
            val parent = readSettingsFile(file?.parent, config.settingsFileName)?.children
            val self = readSettingsFile(file?.path, config.settingsFileName)
            return Settings(
                self?.basenameCursorPosition ?: parent?.basenameCursorPosition ?: 13,  // = "0000-00-00 - ".length
                NewInode(
                    self?.newInodeTemplate?.basename ?: parent?.newInodeTemplate?.basename ?: "<now> - .txt",
                    self?.newInodeTemplate?.isFile ?: parent?.newInodeTemplate?.isFile ?: true,
                ),
                self?.runLast != null || parent?.runLast != null,
                self?.tickets != null || parent?.tickets != null
            )
        }

        private fun readSettingsFile(path: String?, settingsFileName: String): SettingsVersioned? {
            val file = if (path != null) File("$path/$settingsFileName") else return null
            return if (file.isFile && file.canRead()) JsonFormat.decodeFromString(file.readText()) else null
        }
    }
}
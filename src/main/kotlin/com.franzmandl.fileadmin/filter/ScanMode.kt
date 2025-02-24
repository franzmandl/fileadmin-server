package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.dto.config.CommandId
import com.franzmandl.fileadmin.dto.config.ScanModeVersion1

class ScanMode(
    @Suppress("UNUSED_PARAMETER") vararg kwargs: Unit,
    /** If clearUnknown is true, then all other flags except markDirty should also be true in order to repopulate unknown tags. */
    val clearUnknown: Boolean,
    val enabled: Boolean,
    val onlyIfDirty: Boolean,
    val ignoreLastModified: Boolean,
    val ignoreScannedInputs: Boolean,
    val lastModifiedEnabled: Boolean,
    val markDirty: Boolean,
    val quickBypass: Boolean,
    val recomputeItems: Boolean,
) {
    companion object {
        fun create(commandId: CommandId, latest: ScanModeVersion1?): ScanMode =
            when (commandId) {
                CommandId.Add, CommandId.Delete, CommandId.Move, CommandId.Rename, CommandId.ToDirectory, CommandId.ToFile -> ScanMode(
                    clearUnknown = latest?.clearUnknown ?: false,
                    enabled = latest?.enabled ?: true,
                    onlyIfDirty = latest?.onlyIfDirty ?: false,
                    ignoreLastModified = latest?.ignoreLastModified ?: false,
                    ignoreScannedInputs = latest?.ignoreScannedInputs ?: true,
                    lastModifiedEnabled = latest?.lastModifiedEnabled ?: true,
                    markDirty = latest?.markDirty ?: true,
                    quickBypass = latest?.quickBypass ?: false,
                    recomputeItems = latest?.recomputeItems ?: true,
                )

                CommandId.FilterItems, CommandId.FirstScanItems, CommandId.ForceScanItems -> ScanMode(
                    clearUnknown = latest?.clearUnknown ?: false,
                    enabled = latest?.enabled ?: true,
                    onlyIfDirty = latest?.onlyIfDirty ?: false,
                    ignoreLastModified = latest?.ignoreLastModified ?: false,
                    ignoreScannedInputs = latest?.ignoreScannedInputs ?: false,
                    lastModifiedEnabled = latest?.lastModifiedEnabled ?: true,
                    markDirty = latest?.markDirty ?: false,
                    quickBypass = latest?.quickBypass ?: false,
                    recomputeItems = latest?.recomputeItems ?: true,
                )

                CommandId.GetAllTags, CommandId.GetSystemRoot, CommandId.GetUnknownTags, CommandId.GetUnusedTags, CommandId.RequiresAction -> ScanMode(
                    clearUnknown = latest?.clearUnknown ?: latest?.recomputeItems ?: true,
                    enabled = latest?.enabled ?: true,
                    onlyIfDirty = latest?.onlyIfDirty ?: false,
                    ignoreLastModified = latest?.ignoreLastModified ?: true,
                    ignoreScannedInputs = latest?.ignoreScannedInputs ?: true,
                    lastModifiedEnabled = latest?.lastModifiedEnabled ?: true,
                    markDirty = latest?.markDirty ?: false,
                    quickBypass = latest?.quickBypass ?: false,
                    recomputeItems = latest?.recomputeItems ?: true,
                )

                CommandId.GetDirectory, CommandId.GetInode -> ScanMode(
                    clearUnknown = latest?.clearUnknown ?: false,
                    enabled = latest?.enabled ?: true,
                    onlyIfDirty = latest?.onlyIfDirty ?: true,
                    ignoreLastModified = latest?.ignoreLastModified ?: false,
                    ignoreScannedInputs = latest?.ignoreScannedInputs ?: false,
                    lastModifiedEnabled = latest?.lastModifiedEnabled ?: true,
                    markDirty = latest?.markDirty ?: false,
                    quickBypass = latest?.quickBypass ?: false,
                    recomputeItems = latest?.recomputeItems ?: true,
                )

                CommandId.GetSuggestion -> ScanMode(
                    clearUnknown = latest?.clearUnknown ?: false,
                    enabled = latest?.enabled ?: false,
                    onlyIfDirty = latest?.onlyIfDirty ?: false,
                    ignoreLastModified = latest?.ignoreLastModified ?: false,
                    ignoreScannedInputs = latest?.ignoreScannedInputs ?: false,
                    lastModifiedEnabled = latest?.lastModifiedEnabled ?: true,
                    markDirty = latest?.markDirty ?: false,
                    quickBypass = latest?.quickBypass ?: false,
                    recomputeItems = latest?.recomputeItems ?: true,
                )

                CommandId.MoveTag -> ScanMode(
                    clearUnknown = false,
                    enabled = true,
                    onlyIfDirty = false,
                    ignoreLastModified = true,
                    ignoreScannedInputs = true,
                    lastModifiedEnabled = true,
                    markDirty = false,
                    quickBypass = false,
                    recomputeItems = true,
                )
            }
    }
}
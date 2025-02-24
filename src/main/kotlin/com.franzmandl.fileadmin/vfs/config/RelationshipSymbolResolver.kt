package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.vfs.SafePath

class RelationshipSymbolResolver(
    private val definition: ConfigRelationshipDefinition,
    private val subjectName: String,
    private val symbolResolver: TagNameHelper.SymbolResolver,
) : TagNameHelper.SymbolResolver {
    override fun resolveSymbol(errorHandler: ErrorHandler, configPath: SafePath, key: String, indices: List<Int>): List<String> =
        when (key) {
            "", "subject" -> {
                if (indices.isNotEmpty()) {
                    errorHandler.onError("""${configPath.absoluteString}: Indices specified for key "$key" in name "${definition.template.name}" which will be ignored.""")
                }
                listOf(subjectName)
            }

            else -> symbolResolver.resolveSymbol(errorHandler, configPath, key, indices)
        }
}
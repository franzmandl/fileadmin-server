package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.filter.Tag
import com.franzmandl.fileadmin.vfs.SafePath

class TagSymbolResolver(
    private val explicitParentsList: List<List<Tag.Mutable>>,
    private val implicitParents: List<Tag.Mutable>,
    private val isATagsList: List<List<Tag.Mutable>>,
    private val name: String,
    private val overrideParentsList: List<List<Tag.Mutable>>?,
) : TagNameHelper.SymbolResolver {
    override fun resolveSymbol(errorHandler: ErrorHandler, configPath: SafePath, key: String, indices: List<Int>): List<String> =
        when (key) {
            "isA" -> resolveLevel2Symbol(errorHandler, configPath, key, isATagsList, indices)
            "parent" -> resolveLevel1Symbol(errorHandler, configPath, key, implicitParents, indices)
            "parents" -> resolveLevel2Symbol(errorHandler, configPath, key, explicitParentsList, indices)
            "overrideParents" -> resolveLevel2Symbol(errorHandler, configPath, key, overrideParentsList ?: listOf(), indices)
            else -> {
                errorHandler.onError("""${configPath.absoluteString}: Illegal key "$key" in name "$name".""")
                listOf()
            }
        }

    private val indexLevel0FriendlyName = "First level index"
    private val indexLevel1FriendlyName = "Second level index"

    private fun resolveLevel1Symbol(errorHandler: ErrorHandler, configPath: SafePath, key: String, list: List<Tag>, indices: List<Int>): List<String> {
        if (indices.size > 1) {
            errorHandler.onError("""${configPath.absoluteString}: More than one index specified for key "$key" in name "$name" which will be ignored.""")
        }
        return resolveSymbolIndex(errorHandler, configPath, indexLevel0FriendlyName, key, list, indices.getOrNull(0)).map { it.name }
    }

    private fun resolveLevel2Symbol(errorHandler: ErrorHandler, configPath: SafePath, key: String, listLevel0: List<List<Tag>>, indices: List<Int>): List<String> {
        if (indices.size > 2) {
            errorHandler.onError("""${configPath.absoluteString}: More than two indices specified for key "$key" in name "$name" which will be ignored.""")
        }
        val listLevel1 = resolveSymbolIndex(errorHandler, configPath, indexLevel0FriendlyName, key, listLevel0, indices.getOrNull(0))
        return resolveSymbolIndex(errorHandler, configPath, indexLevel1FriendlyName, key, listLevel1, indices.getOrNull(1)).flatten().map { it.name }
    }

    private fun <T> resolveSymbolIndex(errorHandler: ErrorHandler, configPath: SafePath, indexFriendlyName: String, key: String, list: List<T>, nullableIndex: Int?): List<T> {
        val index = nullableIndex ?: return list
        return if (index in list.indices) {
            listOf(list[index])
        } else {
            errorHandler.onError("""${configPath.absoluteString}: $indexFriendlyName "$index" for key "$key" out of bounds in name "$name".""")
            return listOf()
        }
    }
}
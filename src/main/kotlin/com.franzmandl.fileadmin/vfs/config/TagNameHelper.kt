package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.filter.FilterFileSystem
import com.franzmandl.fileadmin.vfs.SafePath
import com.franzmandl.fileadmin.vfs.config.TagNameHelper.NameResolver

object TagNameHelper {
    val nameResolverWithValidation = NameResolver { errorHandler, configPath, name -> listOfNotNull(checkValidName(errorHandler, configPath, name)) }
    val nameResolverWithoutValidation = NameResolver { _, _, name -> listOf(name) }

    fun getSequenceOfAliases(errorHandler: ErrorHandler, configPath: SafePath, aliases: String, nameResolver: NameResolver): Sequence<String> =
        sequence {
            for ((index, name) in aliases.split(FilterFileSystem.tagPrefixString).withIndex()) {
                if (index == 0) {
                    if (name != "") {
                        errorHandler.onError("""${configPath.absoluteString}: Missing ${FilterFileSystem.tagPrefix} before tag name in "$aliases".""")
                    }
                } else {
                    yieldAll(nameResolver.resolveName(errorHandler, configPath, name))
                }
            }
        }

    private val symbolRegex = Regex("""<(.*?)(?:\[ *(\d+(?: *, *\d+)*) *])?>""")

    fun createNameResolverWithSymbolResolver(symbolResolver: SymbolResolver): NameResolver =
        NameResolver { errorHandler, configPath, name ->
            val firstMatchResult = symbolRegex.find(name) ?: return@NameResolver listOfNotNull(checkValidName(errorHandler, configPath, name))
            var resolvedNames = listOf(name.substring(0, firstMatchResult.range.first))
            var matchResult: MatchResult? = firstMatchResult
            while (matchResult != null) {
                val nextMatchResult = matchResult.next()
                val suffix = name.substring(matchResult.range.last + 1, nextMatchResult?.range?.first ?: name.length)
                val key = matchResult.groups[1]!!.value
                val indices = matchResult.groups[2]?.value?.split(',')?.map { it.trim().toInt() } ?: listOf()
                resolvedNames = resolvedNames.flatMap { prefix ->
                    symbolResolver.resolveSymbol(errorHandler, configPath, key, indices).mapNotNull { resolvedSymbol ->
                        val resolvedName = prefix + resolvedSymbol + suffix
                        if (nextMatchResult == null) {
                            checkValidName(errorHandler, configPath, resolvedName)
                        } else resolvedName
                    }
                }
                matchResult = nextMatchResult
            }
            resolvedNames
        }

    private fun checkValidName(errorHandler: ErrorHandler, configPath: SafePath, name: String): String? =
        if (FilterFileSystem.isValidName(name)) {
            name
        } else {
            errorHandler.onError("""${configPath.absoluteString}: "$name" is no valid name for a tag.""")
            null
        }

    fun interface NameResolver {
        fun resolveName(errorHandler: ErrorHandler, configPath: SafePath, name: String): List<String>
    }

    fun interface SymbolResolver {
        fun resolveSymbol(errorHandler: ErrorHandler, configPath: SafePath, key: String, indices: List<Int>): List<String>
    }
}
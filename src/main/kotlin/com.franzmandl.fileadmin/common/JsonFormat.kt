package com.franzmandl.fileadmin.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object JsonFormat {
    private const val classDiscriminator = "_type"
    val jsonFormat = Json {
        classDiscriminator = JsonFormat.classDiscriminator
        encodeDefaults = true
        @OptIn(ExperimentalSerializationApi::class)
        explicitNulls = false
    }

    inline fun <reified T> decodeFromString(string: String) = jsonFormat.decodeFromString<T>(string)

    inline fun <reified T> encodeToString(value: T) = jsonFormat.encodeToString(value)
}
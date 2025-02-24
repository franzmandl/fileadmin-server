package com.franzmandl.fileadmin.dto

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val actions: Map<String, String>,
    val date: String,
    val isRepeating: Boolean,
    val isWaiting: Boolean,
    val priority: Int?,
    val usesExpression: Boolean,
    val fileEnding: String,
)
package com.franzmandl.fileadmin.task

import com.franzmandl.fileadmin.resource.RequestCtx
import java.time.LocalDate

interface TaskDateCtx {
    val request: RequestCtx
    fun getById(id: String, caller: TaskDate, callers: MutableSet<TaskDate>): LocalDate
    fun prepareBinary(binaryName: String, original: String): String
}
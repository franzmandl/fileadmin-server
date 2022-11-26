package com.franzmandl.fileadmin.util

import java.util.concurrent.TimeUnit

data class ProcessResult(val exitValue: Int, val stderr: String, val stdout: String) {
    companion object {
        fun create(process: Process, timeoutSeconds: Long = 5): ProcessResult? {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy()
                return null
            }
            return ProcessResult(
                process.exitValue(),
                process.errorStream.bufferedReader().readText(),
                process.inputStream.bufferedReader().readText()
            )
        }
    }
}
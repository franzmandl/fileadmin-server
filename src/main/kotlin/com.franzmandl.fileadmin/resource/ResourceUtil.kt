package com.franzmandl.fileadmin.resource

import java.io.File
import javax.servlet.http.HttpServletResponse

object ResourceUtil {
    fun createDirectories(directory: File) {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw HttpException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not create directory.")
            }
        }
    }
}

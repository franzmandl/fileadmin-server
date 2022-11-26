package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.Config
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody
import javax.servlet.RequestDispatcher
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


@Controller
class MiscResource : ErrorController {
    @RequestMapping(value = [Config.RequestMappingPaths.login], method = [RequestMethod.GET])
    @ResponseBody
    fun respondLoginPostMethod(response: HttpServletResponse): String {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        return "Authentication required."
    }

    @RequestMapping(value = [Config.RequestMappingPaths.error], method = [RequestMethod.GET])
    @ResponseBody
    fun respondErrorGetMethod(request: HttpServletRequest): String {
        val statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as Int? ?: 200
        val httpStatus = HttpStatus.resolve(statusCode)
        return httpStatus?.toString() ?: "$statusCode UNKNOWN_ERROR"
    }
}

package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.dto.ApplicationCtx
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class MiscResource : ErrorController {
    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.error], method = [RequestMethod.GET])
    @ResponseBody
    fun getError(request: HttpServletRequest): String {
        val statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as Int? ?: 200
        val httpStatus = HttpStatus.resolve(statusCode)
        return httpStatus?.toString() ?: "$statusCode UNKNOWN_ERROR"
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.login], method = [RequestMethod.GET])
    @ResponseBody
    fun getLogin(response: HttpServletResponse): String {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        return "Authentication required."
    }

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.readyz], method = [RequestMethod.GET])
    @ResponseBody
    fun getReadyz(): String {
        return "Ready!\n"
    }
}
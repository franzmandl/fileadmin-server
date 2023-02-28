package com.franzmandl.fileadmin.security

import com.franzmandl.fileadmin.common.CommonUtil
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class Handler : AuthenticationFailureHandler, AuthenticationSuccessHandler, LogoutSuccessHandler {
    private fun send(response: HttpServletResponse, message: String) {
        response.contentType = CommonUtil.contentTypeTextPlainUtf8
        response.writer.print(message)
    }

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        send(response, "Wrong username or password.")
    }

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        send(response, "Success!")
    }

    override fun onLogoutSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        send(response, "Success!")
    }
}
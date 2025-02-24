package com.franzmandl.fileadmin.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.stereotype.Component

@Component
class AuthenticationSuccessListener(
    @Autowired private val loginAttemptService: LoginAttemptService
) : ApplicationListener<AuthenticationSuccessEvent> {
    override fun onApplicationEvent(event: AuthenticationSuccessEvent) {
        loginAttemptService.loginSucceeded()
    }
}
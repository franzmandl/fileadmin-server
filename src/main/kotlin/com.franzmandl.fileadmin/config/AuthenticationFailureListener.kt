package com.franzmandl.fileadmin.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent
import org.springframework.security.web.authentication.WebAuthenticationDetails
import org.springframework.stereotype.Component
import java.util.*

// See https://stackoverflow.com/questions/2681401/spring-security-how-to-implement-brute-force-detection-bfd
@Component
class AuthenticationFailureListener(
    @Autowired private val loginAttemptService: LoginAttemptService,
    @Autowired private val shutdownService: ShutdownService,
) : ApplicationListener<AuthenticationFailureBadCredentialsEvent> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private fun logAttempt(event: AuthenticationFailureBadCredentialsEvent) {
        val list = LinkedList<String>()
        list += "authentication.name=${event.authentication.name}"
        val source = event.source
        if (source is UsernamePasswordAuthenticationToken) {
            val credentials = source.credentials
            list += if (credentials is String) {
                "source.credentials.length=${credentials.length}"
            } else {
                "source.credentials=${credentials.javaClass.name}"
            }
            val details = source.details
            list += if (details is WebAuthenticationDetails) {
                "source.remoteAddress=${details.remoteAddress}"
            } else {
                "source.details=${details.javaClass.name}"
            }
        } else {
            list += "source=${source.javaClass.name}"
        }
        logger.warn("FAILED AUTHENTICATION ATTEMPTS EXCEEDED " + list.joinToString("\n"))
    }

    override fun onApplicationEvent(event: AuthenticationFailureBadCredentialsEvent) {
        if (loginAttemptService.loginFailed()) {
            logAttempt(event)
            shutdownService.shutdown("security")
        }
    }
}
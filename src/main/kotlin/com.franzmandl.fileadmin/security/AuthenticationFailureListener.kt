package com.franzmandl.fileadmin.security

import org.slf4j.Logger
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
    private val logger: Logger = LoggerFactory.getLogger(AuthenticationFailureListener::class.java)

    private fun logAttempt(event: AuthenticationFailureBadCredentialsEvent) {
        val list = LinkedList<String>()
        list.add("authentication.name=${event.authentication.name}")
        val source = event.source
        if (source is UsernamePasswordAuthenticationToken) {
            val credentials = source.credentials
            if (credentials is String) {
                list.add("source.credentials.length=${credentials.length}")
            } else {
                list.add("source.credentials=${credentials.javaClass.name}")
            }
            val details = source.details
            if (details is WebAuthenticationDetails) {
                list.add("source.remoteAddress=${details.remoteAddress}")
            } else {
                list.add("source.details=${details.javaClass.name}")
            }
        } else {
            list.add("source=${source.javaClass.name}")
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
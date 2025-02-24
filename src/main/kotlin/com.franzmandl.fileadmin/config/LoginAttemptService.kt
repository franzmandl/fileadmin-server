package com.franzmandl.fileadmin.config

import com.franzmandl.fileadmin.dto.ApplicationCtx
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

@Service
class LoginAttemptService(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val attemptsCounter = AtomicInteger(0)

    fun loginSucceeded() {
        attemptsCounter.set(0)
    }

    fun loginFailed(): Boolean =
        attemptsCounter.incrementAndGet() > applicationCtx.security.maxLoginAttempts
}
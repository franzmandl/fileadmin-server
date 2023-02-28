package com.franzmandl.fileadmin.security

import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

@Service
class LoginAttemptService {
    private val maxAttempts = 5
    private var attemptsCounter = AtomicInteger(0)

    fun loginSucceeded() {
        attemptsCounter.set(0)
    }

    fun loginFailed(): Boolean {
        return attemptsCounter.incrementAndGet() > maxAttempts
    }
}
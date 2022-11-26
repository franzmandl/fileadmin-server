package com.franzmandl.fileadmin.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Service

@Service
class ShutdownService : ApplicationContextAware {
    var logger: Logger = LoggerFactory.getLogger(ShutdownService::class.java)
    private lateinit var context: ConfigurableApplicationContext

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.context = applicationContext as ConfigurableApplicationContext
    }

    fun shutdown(reasons: String) {
        logger.info("Shutting down for $reasons reasons")
        context.close()
    }
}

package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.model.ApplicationCtx
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableWebMvc
class MvcConfig(
    @Autowired private val config: ApplicationCtx
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if (config.paths.bookmarks != null) {
            registry
                .addResourceHandler("${ApplicationCtx.RequestMappingPaths.bookmarks}/**")
                .addResourceLocations("file:${config.paths.bookmarks}/")
        }
        if (config.paths.web != null) {
            registry
                .addResourceHandler("${ApplicationCtx.RequestMappingPaths.web}/**")
                .addResourceLocations("file:${config.paths.web}/")
        }
    }
}
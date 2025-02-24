package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.dto.ApplicationCtx
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableWebMvc
class MvcConfig(
    @Autowired private val applicationCtx: ApplicationCtx,
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if (applicationCtx.bookmarks.path != null) {
            registry
                .addResourceHandler("${ApplicationCtx.RequestMappingPaths.bookmarks}/**")
                .addResourceLocations("file:${applicationCtx.bookmarks.path}/")
        }
        if (applicationCtx.web.path != null) {
            registry
                .addResourceHandler("${ApplicationCtx.RequestMappingPaths.web}/**")
                .addResourceLocations("file:${applicationCtx.web.path}/")
        }
    }
}
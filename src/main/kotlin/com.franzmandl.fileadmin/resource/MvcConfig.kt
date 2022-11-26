package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer


@Configuration
@EnableWebMvc
class MvcConfig(
    @Autowired private val config: Config
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("${Config.RequestMappingPaths.bookmarks}/**")
            .addResourceLocations("file:${config.paths.bookmarks}/")
        registry
            .addResourceHandler("${Config.RequestMappingPaths.web}/**")
            .addResourceLocations("file:${config.paths.web}/")
    }
}

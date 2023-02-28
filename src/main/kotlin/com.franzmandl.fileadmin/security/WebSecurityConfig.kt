package com.franzmandl.fileadmin.security

import com.franzmandl.fileadmin.model.ApplicationCtx
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class WebSecurityConfig(
    @Autowired private val config: ApplicationCtx,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return PasswordEncrypter.passwordEncoder()
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val handler = Handler()
        http
            .let { if (config.security.enableCors) http.cors(Customizer.withDefaults()) else it }
            .csrf()
            .disable()
            .authorizeRequests()
            .antMatchers(
                "${ApplicationCtx.RequestMappingPaths.bookmarksPrivate}/**",
                "${ApplicationCtx.RequestMappingPaths.authenticated}/**"
            ).authenticated()
            .antMatchers(
                "${ApplicationCtx.RequestMappingPaths.local}/**"
            ).hasIpAddress("127.0.0.1/32")
            .and()
            .exceptionHandling()
            .authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .and()
            .formLogin()
            .loginPage(ApplicationCtx.RequestMappingPaths.login)
            .successHandler(handler)
            .failureHandler(handler)
            .and()
            .logout()
            .logoutUrl(ApplicationCtx.RequestMappingPaths.Authenticated.logout)
            .logoutSuccessHandler(handler)
        if (config.security.tokenCookieName.isNotEmpty()) {
            http.rememberMe()
                .tokenValiditySeconds(config.security.tokenMaxAgeSeconds)
                .rememberMeCookieName(config.security.tokenCookieName)
                .useSecureCookie(config.security.useOnlySecureCookies)
                .let { if (config.security.key.isNotEmpty()) it.key(config.security.key) else it }
        }
        return http.build()
    }

    @Bean
    fun userDetailsService(): UserDetailsService {
        return InMemoryUserDetailsManager(config.security.usernames.map { username ->
            User.builder()
                .username(username)
                .password(config.security.passwordHash)
                .roles("USER")
                .build()
        })
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        if (config.security.enableCors) {
            val configuration = CorsConfiguration()
            configuration.allowedOrigins = listOf(*config.security.allowedOrigins)
            configuration.allowedHeaders = listOf("*")
            // See https://stackoverflow.com/questions/37897523/axios-get-access-to-response-header-fields
            configuration.addExposedHeader(ApplicationCtx.Header.lastModified)
            configuration.allowedMethods = listOf("*")
            configuration.allowCredentials = true
            source.registerCorsConfiguration("/**", configuration)
        }
        return source
    }
}
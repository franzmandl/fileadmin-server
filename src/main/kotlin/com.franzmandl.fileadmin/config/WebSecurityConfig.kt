package com.franzmandl.fileadmin.config

import com.franzmandl.fileadmin.dto.ApplicationCtx
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
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class WebSecurityConfig(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        PasswordEncrypter.passwordEncoder

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val handler = Handler()
        http
            .let { if (applicationCtx.security.corsEnabled) it.cors(Customizer.withDefaults()) else it }
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "${ApplicationCtx.RequestMappingPaths.bookmarksPrivate}/**",
                    "${ApplicationCtx.RequestMappingPaths.authenticated}/**"
                ).authenticated()
                it.requestMatchers(
                    "${ApplicationCtx.RequestMappingPaths.local}/**"
                ).access(WebExpressionAuthorizationManager("hasIpAddress('127.0.0.1/32') or hasIpAddress('::1/128')"))
                it.anyRequest().permitAll()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .formLogin {
                it
                    .loginPage(ApplicationCtx.RequestMappingPaths.login)
                    .successHandler(handler)
                    .failureHandler(handler)
            }
            .logout {
                it
                    .logoutUrl(ApplicationCtx.RequestMappingPaths.Authenticated.logout)
                    .logoutSuccessHandler(handler)
            }
        if (applicationCtx.security.tokenCookieName.isNotEmpty()) {
            http.rememberMe {
                it.tokenValiditySeconds(applicationCtx.security.tokenMaxAgeSeconds)
                it.rememberMeCookieName(applicationCtx.security.tokenCookieName)
                it.useSecureCookie(applicationCtx.security.useOnlySecureCookies)
                if (applicationCtx.security.key.isNotEmpty()) it.key(applicationCtx.security.key)
            }
        }
        return http.build()
    }

    @Bean
    fun userDetailsService(): UserDetailsService =
        InMemoryUserDetailsManager(applicationCtx.security.usernames.map { username ->
            User.builder()
                .username(username)
                .password(applicationCtx.security.passwordHash)
                .roles("USER")
                .build()
        })

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        if (applicationCtx.security.corsEnabled) {
            val configuration = CorsConfiguration()
            configuration.allowedOrigins = listOf(*applicationCtx.security.allowedOrigins)
            configuration.allowedHeaders = listOf("*")
            // See https://stackoverflow.com/questions/37897523/axios-get-access-to-response-header-fields
            configuration.addExposedHeader(ApplicationCtx.Header.lastModifiedMilliseconds)
            configuration.allowedMethods = listOf("*")
            configuration.allowCredentials = true
            source.registerCorsConfiguration("/**", configuration)
        }
        return source
    }
}
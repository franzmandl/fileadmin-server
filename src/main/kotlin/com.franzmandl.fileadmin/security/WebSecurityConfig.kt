package com.franzmandl.fileadmin.security

import com.franzmandl.fileadmin.Config
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
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import javax.sql.DataSource


@Configuration
@EnableWebSecurity
class WebSecurityConfig(
    @Autowired private val config: Config,
    @Autowired private val dataSource: DataSource,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return PasswordEncrypter.passwordEncoder()
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        if (config.security.enableCors) {
            http.cors(Customizer.withDefaults())
        }
        val handler = Handler()
        http
            .csrf()
            .disable()
            .authorizeRequests()
            .antMatchers(
                "${Config.RequestMappingPaths.bookmarksPrivate}/**",
                "${Config.RequestMappingPaths.authenticated}/**"
            ).authenticated()
            .and()
            .exceptionHandling()
            .authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .and()
            .formLogin()
            .loginPage(Config.RequestMappingPaths.login)
            .successHandler(handler)
            .failureHandler(handler)
            .and()
            .logout()
            .logoutUrl(Config.RequestMappingPaths.Authenticated.logout)
            .logoutSuccessHandler(handler)
        if (config.security.tokenCookieName.isNotEmpty()) {
            http.rememberMe()
                .tokenRepository(persistentTokenRepository())
                .tokenValiditySeconds(config.security.tokenMaxAgeSeconds)
                .rememberMeCookieName(config.security.tokenCookieName)
                .useSecureCookie(config.security.useOnlySecureCookies)
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
            configuration.addExposedHeader(Config.Header.lastModified)
            configuration.allowedMethods = listOf("*")
            configuration.allowCredentials = true
            source.registerCorsConfiguration("/**", configuration)
        }
        return source
    }

    @Bean
    fun persistentTokenRepository(): PersistentTokenRepository {
        val jdbcTokenRepository = JdbcTokenRepositoryImpl()
        jdbcTokenRepository.setDataSource(dataSource)
        dataSource.connection.createStatement()
            .executeUpdate("create table if not exists persistent_logins (username varchar(64) not null, series varchar(64) primary key, token varchar(64) not null, last_used timestamp not null)")
        return jdbcTokenRepository
    }
}

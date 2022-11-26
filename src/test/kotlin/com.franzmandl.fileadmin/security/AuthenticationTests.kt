package com.franzmandl.fileadmin.security

import com.franzmandl.fileadmin.Config
import com.franzmandl.fileadmin.service.ShutdownService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthenticationTests(
    @Autowired val mockMvc: MockMvc,
) {
    @MockBean
    lateinit var shutdownService: ShutdownService

    private fun attemptLogin(username: String, password: String) =
        mockMvc.perform(
            MockMvcRequestBuilders.post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("username=$username&password=$password&remember-me=1")
        )

    @Test
    fun testMaxAttemptsExceeded() {
        repeat(5) {
            attemptLogin("wrongUser", "wrongPassword")
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
        Mockito.doNothing().`when`(shutdownService).shutdown("")
        attemptLogin("wrongUser", "wrongPassword")
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        Mockito.verify(shutdownService).shutdown("security")
    }

    @Test
    fun testMaxAttemptsReset() {
        repeat(5) {
            attemptLogin("wrongUser", "wrongPassword")
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
        attemptLogin("correctUser", "correctPassword")
            .andExpect(MockMvcResultMatchers.status().isOk)
        repeat(5) {
            attemptLogin("wrongUser", "wrongPassword")
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
        attemptLogin("correctUser", "correctPassword")
            .andExpect(MockMvcResultMatchers.status().isOk)
        Mockito.verify(shutdownService, Mockito.never()).shutdown("security")
    }

    val rememberMeCookieName = "rememberMeCookie"

    @Test
    fun testRememberMeCookie() {
        val response = attemptLogin("correctUser", "correctPassword")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response
        val rememberMeCookie = response.getCookie(rememberMeCookieName)!!
        Assertions.assertThat(rememberMeCookie.maxAge).isEqualTo(7 * 24 * 60 * 60)
        Assertions.assertThat(rememberMeCookie.secure).isEqualTo(true)
        Assertions.assertThat(rememberMeCookie.isHttpOnly).isEqualTo(true)
        mockMvc.perform(MockMvcRequestBuilders.get("${Config.RequestMappingPaths.authenticated}/directory?path=/"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        mockMvc.perform(
            MockMvcRequestBuilders.get("${Config.RequestMappingPaths.authenticated}/directory?path=/")
                .cookie(rememberMeCookie)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
    }
}

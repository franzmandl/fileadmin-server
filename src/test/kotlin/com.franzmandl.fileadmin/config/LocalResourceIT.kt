package com.franzmandl.fileadmin.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class LocalResourceIT(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun testGetPing() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/local/ping")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
    }
}
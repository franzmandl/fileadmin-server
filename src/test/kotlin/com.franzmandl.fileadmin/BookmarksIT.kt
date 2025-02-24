package com.franzmandl.fileadmin

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@ActiveProfiles("test", "jail6")
@AutoConfigureMockMvc
class BookmarksIT(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun testPublic() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/bookmarks/public/database.js")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test
    fun testPrivate() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/bookmarks/private/database.js")
        )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }
}
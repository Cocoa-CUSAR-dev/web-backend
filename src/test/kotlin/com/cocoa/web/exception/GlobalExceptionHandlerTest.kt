package com.cocoa.web.exception

import com.cocoa.web.WebApplicationTests
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Verifies the status-code mapping in [GlobalExceptionHandler] by re-using the
 * @SpringBootTest context from [WebApplicationTests] (inherited). The parent's
 * @MockBean for UserService is re-stubbed here to throw each exception type
 * and we assert the resulting HTTP status.
 */
class GlobalExceptionHandlerTest : WebApplicationTests() {
    @Test
    @WithMockUser(authorities = ["read:profile:own"])
    fun entityNotFound_mapsTo404() {
        whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
            throw EntityNotFoundException("missing user")
        }

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("missing user"))
    }

    @Test
    @WithMockUser(authorities = ["read:profile:own"])
    fun illegalArgument_mapsTo400() {
        whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
            throw IllegalArgumentException("bad input")
        }

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("bad input"))
    }

    @Test
    @WithMockUser(authorities = ["read:profile:own"])
    fun permissionDenied_mapsTo403() {
        whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
            throw PermissionDeniedException("nope")
        }

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("nope"))
    }

    @Test
    @WithMockUser(authorities = ["read:profile:own"])
    fun genericException_mapsTo500() {
        whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
            throw RuntimeException("boom")
        }

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("boom"))
    }

    @Test
    @WithMockUser
    fun typeMismatch_mapsTo400() {
        // TaskController accepts UUID for /tasks/{taskId}; a non-UUID triggers
        // MethodArgumentTypeMismatchException -> 400 via the global handler.
        mockMvc.perform(get("/tasks/{bad}", "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }
}

package com.swd392.aiviva.user.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.auth.security.SecurityConfig;
import com.swd392.aiviva.common.response.PageResponse;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.repository.UserRepository;
import com.swd392.aiviva.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private UserService users;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    @Test
    void accountAdministrationRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/users/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/admin").with(user("lecturer").roles("LE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSearchAccountsWithPagination() throws Exception {
        when(users.searchManageableUsers(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(20))).thenReturn(PageResponse.<UserResponse>builder()
                        .content(List.of()).page(0).size(20).totalElements(0).totalPages(0).build());

        mockMvc.perform(get("/api/users/admin").with(user("admin").roles("AD")))
                .andExpect(status().isOk());
    }
}

package com.swd392.aiviva.system.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.auth.security.SecurityConfig;
import com.swd392.aiviva.system.application.SubjectCatalogService;
import com.swd392.aiviva.system.application.SystemAdministrationService;
import com.swd392.aiviva.system.domain.LanguageDefaults;
import com.swd392.aiviva.system.enums.Language;
import com.swd392.aiviva.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemAdministrationController.class)
@Import(SecurityConfig.class)
class SystemAdministrationControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private SystemAdministrationService admin;
    @MockBean private SubjectCatalogService subjects;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    @Test
    void administrationApisRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/system/admin/language-defaults")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/system/admin/language-defaults").with(user("student").roles("ST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadPersistedLanguageDefaults() throws Exception {
        when(admin.languageDefaults()).thenReturn(new LanguageDefaults(Language.VI, Language.EN));
        mockMvc.perform(get("/api/system/admin/language-defaults").with(user("admin").roles("AD")))
                .andExpect(status().isOk());
    }

    @Test
    void subjectCatalogRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/system/admin/subjects")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/system/admin/subjects").with(user("student").roles("ST")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/system/admin/subjects").with(user("admin").roles("AD")))
                .andExpect(status().isOk());
    }
}

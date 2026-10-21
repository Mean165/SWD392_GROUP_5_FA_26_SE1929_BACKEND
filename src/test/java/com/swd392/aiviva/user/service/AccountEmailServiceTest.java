package com.swd392.aiviva.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.swd392.aiviva.common.exception.ExternalServiceException;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class AccountEmailServiceTest {

    @Test
    void sendsAccountCodeAndTemporaryPasswordToTheNewUser() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AccountEmailService service = new AccountEmailService(mailSender,
                "accounts@example.test", "app-password", "accounts@example.test");
        AppUser user = AppUser.builder()
                .fullName("New Student")
                .email("student@example.test")
                .studentOrStaffCode("ST12")
                .role(Role.builder().roleCode("ST").roleName("Student").build())
                .build();

        service.sendInitialCredentials(user, "one-time-password");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertEquals("accounts@example.test", message.getValue().getFrom());
        assertEquals("student@example.test", message.getValue().getTo()[0]);
        org.junit.jupiter.api.Assertions.assertTrue(message.getValue().getText().contains("ST12"));
        org.junit.jupiter.api.Assertions.assertTrue(message.getValue().getText().contains("one-time-password"));
    }

    @Test
    void missingSmtpCredentialsFailWithoutAttemptingDelivery() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AccountEmailService service = new AccountEmailService(mailSender, "", "", "");

        assertThrows(ExternalServiceException.class,
                () -> service.sendInitialCredentials(AppUser.builder().build(), "temporary"));
        verifyNoInteractions(mailSender);
    }
}

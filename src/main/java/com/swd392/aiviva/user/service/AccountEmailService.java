package com.swd392.aiviva.user.service;

import com.swd392.aiviva.common.exception.ExternalServiceException;
import com.swd392.aiviva.user.entity.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AccountEmailService {

    private final JavaMailSender mailSender;
    private final String username;
    private final String password;
    private final String from;

    public AccountEmailService(JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    public void sendInitialCredentials(AppUser user, String initialPassword) {
        if (username.isBlank() || password.isBlank() || from.isBlank()) {
            throw new ExternalServiceException(
                    "Email is not configured. Set MAIL_USERNAME, MAIL_PASSWORD, and MAIL_FROM.");
        }

        String roleName = user.getRole().getRoleName() != null
                ? user.getRole().getRoleName() : user.getRole().getRoleCode();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Thông tin tài khoản AiViva");
        message.setText("Xin chào " + user.getFullName() + ",\n\n"
                + "Tài khoản AiViva của bạn đã được tạo.\n"
                + "Email: " + user.getEmail() + "\n"
                + "Mã tài khoản: " + user.getStudentOrStaffCode() + "\n"
                + "Vai trò: " + roleName + "\n"
                + "Mật khẩu tạm thời: " + initialPassword + "\n\n"
                + "Vui lòng đăng nhập và đổi mật khẩu ngay trong lần đầu sử dụng.\n");

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new ExternalServiceException(
                    "Could not send the account email. Check Gmail SMTP settings and the app password.", exception);
        }
    }
}

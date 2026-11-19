package com.revo.backend.auth.service;


import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailOtpSender {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailOtpSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Revo - Xac minh tai khoan");
        message.setText(
                "Ma xac minh Revo cua ban la: " + otp
                        + "\nMa co hieu luc trong 5 phut."
        );

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new BusinessException(
                    ErrorCode.OTP_DELIVERY_UNAVAILABLE
            );
        }
    }
}

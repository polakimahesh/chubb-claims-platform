package com.chubb.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Simulated e-mail channel: logs the delivery with a masked address (no personal data in logs). */
@Slf4j
@Component
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public void send(String recipientEmail, String subject, String body) {
        log.info("Sending EMAIL to {}: {}", mask(recipientEmail), subject);
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}

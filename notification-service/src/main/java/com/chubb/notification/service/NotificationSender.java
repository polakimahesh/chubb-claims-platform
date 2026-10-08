package com.chubb.notification.service;

/**
 * Delivery channel. The shipped implementation logs the e-mail instead of sending it; an SMTP, SES or SMS
 * implementation replaces it without touching the rest of the service.
 */
public interface NotificationSender {

    String channel();

    /** Delivers the message; throws if delivery failed so the event is retried and eventually dead-lettered. */
    void send(String recipientEmail, String subject, String body);
}

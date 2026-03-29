package com.aiplatform.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import com.aiplatform.model.User;
import com.aiplatform.util.EmailTemplateUtils;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${mail.sender}")
    private String fromEmail;

    public void sendSimpleMessage(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    public void sendHtmlMessage(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "VaniAI Notifications");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            
            mailSender.send(message);
            log.info("\ud83d\udce7 [EmailService] HTML email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("\u274c [EmailService] Failed to send HTML email to {}: {}", to, e.getMessage());
        }
    }

    public void sendWelcomeEmail(User user) {
        String htmlBody = EmailTemplateUtils.getWelcomeEmailHtml(user);
        sendHtmlMessage(user.getEmail(), "\u2728 Welcome to VaniAI - System Identification Complete!", htmlBody);
    }

    public void sendLowCreditAlert(User user) {
        String htmlBody = EmailTemplateUtils.getLowCreditAlertHtml(user);
        sendHtmlMessage(user.getEmail(), "\u26a0\ufe0f Critical Capacity Alert: VaniAI Credits Running Low", htmlBody);
    }

    public void sendPlanExpiredEmail(User user) {
        String htmlBody = EmailTemplateUtils.getPlanExpiredHtml(user);
        sendHtmlMessage(user.getEmail(), "\u231b System Notice: Your VaniAI Subscription has Expired", htmlBody);
    }
}

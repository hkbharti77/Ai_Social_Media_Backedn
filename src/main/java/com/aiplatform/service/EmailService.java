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
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private EmailTemplateUtils emailTemplateUtils;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${mail.sender}")
    private String fromEmail;

    @Value("${mail.admin}")
    private String adminEmail;

    @Value("${app.security.owner-email}")
    private String ownerEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void sendSimpleMessage(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    public void sendHtmlMessage(String to, String subject, String htmlBody) {
        sendHtmlMessage(to, null, subject, htmlBody);
    }

    public void sendHtmlMessage(String to, String replyTo, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "GyanVaniAi Notifications");
            helper.setTo(to);
            if (replyTo != null && !replyTo.isEmpty()) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            
            mailSender.send(message);
            log.info("\ud83d\udce7 [EmailService] HTML email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("\u274c [EmailService] Failed to send HTML email to {}: {}", to, e.getMessage());
        }
    }

    public void sendTemplatedEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        sendTemplatedEmail(to, null, subject, templateName, variables);
    }

    public void sendTemplatedEmail(String to, String replyTo, String subject, String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            context.setVariable("subject", subject); // Often used in base template
            
            // Use default fragments if not provided
            String contentFragment = (String) variables.getOrDefault("contentFragment", "email/" + templateName + "::content");
            context.setVariable("contentFragment", contentFragment);

            String htmlBody = templateEngine.process("email/base", context);
            sendHtmlMessage(to, replyTo, subject, htmlBody);
        } catch (Exception e) {
            log.error("\u274c [EmailService] Failed to render and send templated email: {}", e.getMessage());
        }
    }

    public void sendWelcomeEmail(User user) {
        Map<String, Object> variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Learner",
            "credits", user.getMonthlyCredits(),
            "dashboardUrl", frontendUrl,
            "headerTitle", "Welcome to GyanVaniAi"
        );
        sendTemplatedEmail(user.getEmail(), "\u2728 Welcome to GyanVaniAi - Setup Complete!", "welcome", variables);
    }

    public void sendLowCreditAlert(User user) {
        Map<String, Object> variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Learner",
            "remainingCredits", user.getMonthlyCredits(),
            "pricingUrl", frontendUrl + "/pricing"
        );
        sendTemplatedEmail(user.getEmail(), "\u26a0\ufe0f Critical Capacity Alert: GyanVaniAi Credits Running Low", "low_credit_alert", variables);
    }

    public void sendPlanExpiredEmail(User user) {
        Map<String, Object> variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Learner",
            "pricingUrl", frontendUrl + "/pricing"
        );
        sendTemplatedEmail(user.getEmail(), "\u231b System Notice: Your GyanVaniAi Subscription has Expired", "plan_expired", variables);
    }

    public void sendVerificationEmail(User user, String token) {
        Map<String, Object> variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Learner",
            "verificationUrl", frontendUrl + "/api/v1/auth/verify?token=" + token // Assuming frontend handles this or redirects
        );
        // Note: The original used backendUrl for verification. Let's stick to what's in EmailTemplateUtils.
        String verificationUrl = frontendUrl.contains("localhost") ? "http://localhost:8080/api/v1/auth/verify?token=" + token : frontendUrl + "/api/v1/auth/verify?token=" + token;
        
        variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Learner",
            "verificationUrl", verificationUrl
        );
        sendTemplatedEmail(user.getEmail(), "Verify your GyanVaniAi Account", "verification", variables);
    }

    public void sendPasswordResetEmail(User user, String token) {
        Map<String, Object> variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Learner",
            "resetUrl", frontendUrl + "/reset-password?token=" + token
        );
        sendTemplatedEmail(user.getEmail(), "🔐 Reset Your GyanVaniAi Password", "password_reset", variables);
    }

    public void sendPasswordResetSuccessEmail(User user) {
        Map<String, Object> variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Learner",
            "loginUrl", frontendUrl + "/login"
        );
        sendTemplatedEmail(user.getEmail(), "✅ Your GyanVaniAi Password Has Been Reset", "password_reset_success", variables);
    }

    public void sendManualPasswordResetEmail(User user, String newPassword) {
        Map<String, Object> variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Learner",
            "newPassword", newPassword,
            "loginUrl", frontendUrl + "/login"
        );
        sendTemplatedEmail(user.getEmail(), "🔐 Security Notice: Your GyanVaniAi Password Has Been Reset by Admin", "manual_password_reset", variables);
    }

    public void sendAdminMagicLoginEmail(User user, String password) {
        Map<String, Object> variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Admin",
            "password", password,
            "loginUrl", frontendUrl + "/login",
            "headerTitle", "Admin Access Control"
        );
        sendTemplatedEmail(user.getEmail(), "🛡️ Your Admin Temporary Access Password", "admin_magic_login", variables);
    }

    public void sendOwnerRotationEmail(User user, String newPassword) {
        Map<String, Object> variables = Map.of(
            "name", user.getFullName() != null ? user.getFullName() : "Owner",
            "newPassword", newPassword,
            "loginUrl", frontendUrl + "/login",
            "headerTitle", "Owner Security Vault"
        );
        sendTemplatedEmail(user.getEmail(), "🔑 CRITICAL: Your GyanVaniAi Owner Access ID", "owner_rotation", variables);
    }

    public void sendPostPublishedEmail(User user, com.aiplatform.model.Post post, String platform) {
        try {
            String postType = Boolean.TRUE.equals(post.getIsReel()) ? "Reel" :
                              Boolean.TRUE.equals(post.getIsStory()) ? "Story" :
                              Boolean.TRUE.equals(post.getIsCarousel()) ? "Carousel" : "Post";
            String platformDisplay = platform.substring(0, 1).toUpperCase() + platform.substring(1).toLowerCase();
            String publishedAt = post.getPublishedAt() != null
                ? post.getPublishedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                : java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));

            Map<String, Object> variables = new java.util.HashMap<>(Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Creator",
                "postType", postType,
                "platformDisplay", platformDisplay,
                "platformEmoji", emailTemplateUtils.getPlatformEmoji(platform),
                "platformColor", emailTemplateUtils.getPlatformColor(platform),
                "publishedAt", publishedAt,
                "calendarUrl", frontendUrl + "/calendar"
            ));
            if (post.getImageUrl() != null) variables.put("imageUrl", post.getImageUrl());
            if (post.getExternalPostId() != null) variables.put("externalPostId", post.getExternalPostId());
            if (post.getCaption() != null) {
                String caption = post.getCaption();
                variables.put("captionPreview", caption.length() > 200 ? caption.substring(0, 200) + "..." : caption);
            }

            sendTemplatedEmail(user.getEmail(), "✅ Your " + postType + " is Live on " + platformDisplay + "!", "post_published_success", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send post-published email: {}", e.getMessage());
        }
    }

    public void sendPostFailedEmail(User user, com.aiplatform.model.Post post, String platform, String reason) {
        try {
            String postType = Boolean.TRUE.equals(post.getIsReel()) ? "Reel" :
                              Boolean.TRUE.equals(post.getIsStory()) ? "Story" :
                              Boolean.TRUE.equals(post.getIsCarousel()) ? "Carousel" : "Post";
            String platformDisplay = platform.substring(0, 1).toUpperCase() + platform.substring(1).toLowerCase();
            String scheduledAt = post.getScheduledAt() != null
                ? post.getScheduledAt().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                : "N/A";

            Map<String, Object> variables = Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Creator",
                "postType", postType,
                "platformDisplay", platformDisplay,
                "platformEmoji", emailTemplateUtils.getPlatformEmoji(platform),
                "scheduledAt", scheduledAt,
                "reason", reason != null ? reason : "Unknown error occurred.",
                "connectUrl", frontendUrl + "/connect",
                "calendarUrl", frontendUrl + "/calendar"
            );

            sendTemplatedEmail(user.getEmail(), "❌ Post Failed to Publish on " + platformDisplay, "post_published_failure", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send post-failed email: {}", e.getMessage());
        }
    }

    public void sendSocialAccountConnectedEmail(User user, String platform, String accountName) {
        try {
            String platformDisplay = platform.substring(0, 1).toUpperCase() + platform.substring(1).toLowerCase();
            String connectedAt = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));

            Map<String, Object> variables = new java.util.HashMap<>(Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Creator",
                "platformDisplay", platformDisplay,
                "platformEmoji", emailTemplateUtils.getPlatformEmoji(platform),
                "platformColor", emailTemplateUtils.getPlatformColor(platform),
                "connectedAt", connectedAt,
                "manageUrl", frontendUrl + "/connect"
            ));
            if (accountName != null) variables.put("accountName", accountName);

            sendTemplatedEmail(user.getEmail(), "✅ " + platformDisplay + " Account Connected to GyanVaniAi", "social_connected", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send social-connected email: {}", e.getMessage());
        }
    }

    public void sendSocialAccountDisconnectedEmail(User user, String platform, String accountName) {
        try {
            String platformDisplay = platform.substring(0, 1).toUpperCase() + platform.substring(1).toLowerCase();
            String disconnectedAt = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));

            Map<String, Object> variables = new java.util.HashMap<>(Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Creator",
                "platformDisplay", platformDisplay,
                "platformEmoji", emailTemplateUtils.getPlatformEmoji(platform),
                "disconnectedAt", disconnectedAt,
                "reconnectUrl", frontendUrl + "/connect",
                "securityUrl", frontendUrl + "/settings"
            ));
            if (accountName != null) variables.put("accountName", accountName);

            sendTemplatedEmail(user.getEmail(), "⚠️ Security Alert: " + platformDisplay + " Account Disconnected", "social_disconnected", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send social-disconnected email: {}", e.getMessage());
        }
    }

    public void sendWeeklyReportEmail(User user, int postsPublished, int postsFailed,
                                        double creditsUsed, double creditsRemaining, String weekRange) {
        try {
            Map<String, Object> variables = Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Creator",
                "postsPublished", postsPublished,
                "postsFailed", postsFailed,
                "creditsUsed", String.format("%.1f", creditsUsed),
                "creditsRemaining", String.format("%.1f", creditsRemaining),
                "weekRange", weekRange,
                "tier", user.getSubscriptionTier() != null ? user.getSubscriptionTier().name().replace("_", " ") : "FREE",
                "dashboardUrl", frontendUrl + "/dashboard"
            );
            sendTemplatedEmail(user.getEmail(), "📊 Your Weekly GyanVaniAi Activity Report", "weekly_report", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send weekly report email: {}", e.getMessage());
        }
    }

    public void sendRenewalReminderEmail(User user, int daysLeft) {
        try {
            boolean isFinal = daysLeft <= 1;
            String expiresAt = user.getSubscriptionExpiresAt() != null
                ? user.getSubscriptionExpiresAt().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                : "Soon";

            Map<String, Object> variables = Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Creator",
                "daysLeft", daysLeft,
                "isFinal", isFinal,
                "expiresAt", expiresAt,
                "tier", user.getSubscriptionTier() != null ? user.getSubscriptionTier().name().replace("_", " ") : "PREMIUM",
                "pricingUrl", frontendUrl + "/pricing"
            );

            String subject = isFinal
                ? "⚠️ Final Reminder: Your GyanVaniAi Plan Expires Tomorrow!"
                : "⏰ Your GyanVaniAi Plan Expires in " + daysLeft + " Days";
            sendTemplatedEmail(user.getEmail(), subject, "renewal_reminder", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send renewal reminder email: {}", e.getMessage());
        }
    }

    public void sendCreditWarningEmail(User user, double creditsRemaining, double totalCredits, int percentUsed) {
        try {
            boolean isCritical = percentUsed >= 80;
            String warningLabel = isCritical ? "🔴 CRITICAL: 80% Credits Used" : "🟡 HALFWAY: 50% Credits Used";
            String warningMsg = isCritical
                ? "Your AI credits are critically low. Upgrade now to avoid interruption to your scheduled posts."
                : "You've used half your monthly AI credits. Consider upgrading for more capacity.";

            Map<String, Object> variables = Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Creator",
                "creditsRemaining", String.format("%.1f", creditsRemaining),
                "totalCredits", String.format("%.1f", totalCredits),
                "percentUsed", percentUsed,
                "isCritical", isCritical,
                "warningLabel", warningLabel,
                "warningMsg", warningMsg,
                "pricingUrl", frontendUrl + "/pricing"
            );

            String subject = isCritical
                ? "🔴 Critical: 80% of Your GyanVaniAi Credits Used"
                : "🟡 Heads Up: 50% of Your GyanVaniAi Credits Used";
            sendTemplatedEmail(user.getEmail(), subject, "credit_warning", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send credit warning email: {}", e.getMessage());
        }
    }

    public void sendNewLoginAlertEmail(User user, String ipAddress,
                                        com.aiplatform.service.LoginSecurityService.DeviceInfo device,
                                        boolean isNewIp, String loginTime) {
        try {
            String alertLabel = isNewIp ? "⚠️ NEW DEVICE / LOCATION DETECTED" : "✅ SUCCESSFUL LOGIN";
            
            Map<String, Object> variables = Map.ofEntries(
                Map.entry("name", user.getFullName() != null ? user.getFullName() : "User"),
                Map.entry("ipAddress", ipAddress),
                Map.entry("deviceEmoji", device.getDeviceEmoji()),
                Map.entry("deviceType", device.deviceType),
                Map.entry("browser", device.browser),
                Map.entry("os", device.os),
                Map.entry("isNewIp", isNewIp),
                Map.entry("loginTime", loginTime),
                Map.entry("alertLabel", alertLabel),
                Map.entry("securityUrl", frontendUrl + "/settings"),
                Map.entry("manageUrl", frontendUrl + "/connect")
            );

            String subject = isNewIp
                ? "⚠️ New Login Detected on Your GyanVaniAi Account"
                : "✅ Successful Login to GyanVaniAi";
            sendTemplatedEmail(user.getEmail(), subject, "login_alert", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send login alert email: {}", e.getMessage());
        }
    }

    public void sendPostScheduledEmail(User user, com.aiplatform.model.Post post, String scheduledTime) {
        try {
            String postType = Boolean.TRUE.equals(post.getIsReel()) ? "Reel" :
                              Boolean.TRUE.equals(post.getIsStory()) ? "Story" :
                              Boolean.TRUE.equals(post.getIsCarousel()) ? "Carousel" : "Post";
            String platform = post.getPlatform() != null ? post.getPlatform() : "ALL";
            String platformDisplay = platform.equalsIgnoreCase("ALL") || platform.equalsIgnoreCase("BOTH")
                ? "All Platforms" : platform.substring(0, 1).toUpperCase() + platform.substring(1).toLowerCase();

            Map<String, Object> variables = new java.util.HashMap<>(Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Creator",
                "postType", postType,
                "platformDisplay", platformDisplay,
                "platformEmoji", emailTemplateUtils.getPlatformEmoji(platform),
                "scheduledTime", scheduledTime,
                "calendarUrl", frontendUrl + "/calendar"
            ));
            if (post.getImageUrl() != null) variables.put("imageUrl", post.getImageUrl());
            if (post.getCaption() != null) {
                String caption = post.getCaption();
                variables.put("captionPreview", caption.length() > 180 ? caption.substring(0, 180) + "..." : caption);
            }

            sendTemplatedEmail(user.getEmail(),
                "📅 Your " + postType + " is Scheduled on GyanVaniAi", "post_scheduled", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send post-scheduled email: {}", e.getMessage());
        }
    }

    public void sendAdminNewUserAlert(User newUser) {
        try {
            String registeredAt = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));

            Map<String, Object> variables = Map.of(
                "name", newUser.getFullName() != null ? newUser.getFullName() : "N/A",
                "email", newUser.getEmail(),
                "ipAddress", newUser.getRegistrationIp() != null ? newUser.getRegistrationIp() : "Unknown",
                "registeredAt", registeredAt,
                "adminUrl", frontendUrl + "/admin/users" // Assuming admin panel is on frontend
            );
            sendTemplatedEmail(adminEmail, "👤 New User Registered: " + newUser.getEmail(), "admin_new_user", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send admin new-user alert: {}", e.getMessage());
        }
    }

    public void sendAdminLargePaymentAlert(User payer, String orderId, double amountInr) {
        try {
            String paidAt = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));

            Map<String, Object> variables = Map.of(
                "email", payer.getEmail(),
                "orderId", orderId,
                "paidAt", paidAt,
                "amount", String.format("%.2f", amountInr),
                "adminUrl", frontendUrl + "/admin/payments"
            );
            sendTemplatedEmail(adminEmail,
                "💰 Large Payment ₹" + String.format("%.0f", amountInr) + " from " + payer.getEmail(),
                "admin_large_payment", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send admin payment alert: {}", e.getMessage());
        }
    }

    public void sendPaymentReceiptEmail(com.aiplatform.model.PaymentOrder order) {
        try {
            String date = order.getCompletedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
            
            Map<String, Object> variables = Map.of(
                "name", order.getUser().getFullName() != null ? order.getUser().getFullName() : "Valued Customer",
                "orderId", order.getRazorpayOrderId(),
                "paymentId", order.getRazorpayPaymentId(),
                "date", date,
                "amount", String.format("%.2f", order.getAmount() / 100.0),
                "targetTier", order.getTargetTier(),
                "receiptUrl", frontendUrl + "/api/v1/payments/receipt/" + order.getRazorpayOrderId(), // Assuming this is correct
                "billingUrl", frontendUrl + "/settings?tab=billing"
            );
            
            sendTemplatedEmail(order.getUser().getEmail(), "✅ Payment Successful - Your Receipt from GyanVaniAi", "payment_receipt", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send payment receipt email: {}", e.getMessage());
        }
    }

    public void sendBroadcastEmail(String to, String subject, String htmlContent) {
        try {
            Map<String, Object> variables = Map.of(
                "broadcastContent", htmlContent,
                "headerTitle", subject
            );
            sendTemplatedEmail(to, subject, "broadcast", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send broadcast email: {}", e.getMessage());
        }
    }

    public void sendTicketCreatedEmail(User user, com.aiplatform.model.SupportTicket ticket) {
        try {
            Map<String, Object> variables = Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Customer",
                "ticketId", ticket.getId(),
                "subject", ticket.getSubject(),
                "priority", ticket.getPriority().name(),
                "supportUrl", frontendUrl + "/support"
            );
            String subject = "Support Ticket #" + ticket.getId() + ": " + ticket.getSubject();
            sendTemplatedEmail(user.getEmail(), ownerEmail, subject, "ticket_created", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send ticket created email: {}", e.getMessage());
        }
    }

    public void sendTicketStatusUpdateEmail(User user, com.aiplatform.model.SupportTicket ticket) {
        try {
            Map<String, Object> variables = Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Customer",
                "ticketId", ticket.getId(),
                "subject", ticket.getSubject(),
                "status", ticket.getStatus().name().replace("_", " "),
                "supportUrl", frontendUrl + "/support"
            );
            String subject = "Support Ticket #" + ticket.getId() + ": " + ticket.getSubject();
            sendTemplatedEmail(user.getEmail(), ownerEmail, subject, "ticket_updated", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send ticket updated email: {}", e.getMessage());
        }
    }

    public void sendTicketReplyEmail(User user, com.aiplatform.model.SupportTicket ticket, String message) {
        try {
            Map<String, Object> variables = Map.of(
                "name", user.getFullName() != null ? user.getFullName() : "Customer",
                "ticketId", ticket.getId(),
                "subject", ticket.getSubject(),
                "replyMessage", message,
                "supportUrl", frontendUrl + "/support"
            );
            String subject = "Re: Support Ticket #" + ticket.getId() + " - " + ticket.getSubject();
            sendTemplatedEmail(user.getEmail(), ownerEmail, subject, "ticket_reply", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send ticket reply email: {}", e.getMessage());
        }
    }

    public void sendAdminNewTicketAlert(com.aiplatform.model.SupportTicket ticket) {
        try {
            Map<String, Object> variables = Map.of(
                "ticketId", ticket.getId(),
                "userEmail", ticket.getUser().getEmail(),
                "subject", ticket.getSubject(),
                "priority", ticket.getPriority().name(),
                "adminSupportUrl", frontendUrl + "/admin/support"
            );
            String subject = "Support Ticket #" + ticket.getId() + ": " + ticket.getSubject();
            sendTemplatedEmail(ownerEmail, ticket.getUser().getEmail(), subject, "admin_new_ticket", variables);
        } catch (Exception e) {
            log.warn("⚠️ [EmailService] Could not send admin new ticket alert: {}", e.getMessage());
        }
    }
}

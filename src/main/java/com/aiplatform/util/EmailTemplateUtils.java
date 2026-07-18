package com.aiplatform.util;

import com.aiplatform.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class EmailTemplateUtils {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url}")
    private String backendUrl;

    @Autowired
    private TemplateEngine templateEngine;

    /**
     * Wraps raw HTML content in the base email template.
     * Useful for legacy support or highly custom one-off emails.
     */
    public String wrapInBaseTemplate(String tagLine, String content) {
        Context context = new Context();
        context.setVariable("headerTitle", tagLine);
        context.setVariable("rawContent", content);
        return templateEngine.process("email/base", context);
    }

    /**
     * Get the emoji associated with a social platform.
     */
    public String getPlatformEmoji(String platform) {
        if (platform == null)
            return "📱";
        return switch (platform.toUpperCase()) {
            case "INSTAGRAM" -> "📸";
            case "FACEBOOK" -> "📘";
            case "LINKEDIN" -> "💼";
            case "X" -> "🐦";
            default -> "📱";
        };
    }

    /**
     * Get the brand color associated with a social platform.
     */
    public String getPlatformColor(String platform) {
        if (platform == null)
            return "#e2e8f0";
        return switch (platform.toUpperCase()) {
            case "INSTAGRAM" -> "#e1306c";
            case "FACEBOOK" -> "#1877f2";
            case "LINKEDIN" -> "#0a66c2";
            case "X" -> "#1da1f2";
            default -> "#e2e8f0";
        };
    }

    /**
     * Legacy method for broadcast emails - now just a wrapper for
     * wrapInBaseTemplate.
     */
    public String getBroadcastEmailHtml(String subject, String htmlContent) {
        String wrappedContent = "<div style=\"color:#e2e8f0;font-size:15px;line-height:1.7; text-align: left;\">" +
                htmlContent +
                "</div>" +
                "<p style=\"color:#94a3b8;font-size:14px;line-height:1.7;margin:24px 0 0 0;\">Regards,<br>GyanVaniAi Team</p>";

        return wrapInBaseTemplate(subject, wrappedContent);
    }
}

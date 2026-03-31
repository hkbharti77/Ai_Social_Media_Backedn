package com.aiplatform.util;

import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.User;
import java.time.format.DateTimeFormatter;

public class EmailTemplateUtils {

    private static final String PRIMARY_COLOR = "#3b82f6"; // Modern Blue
    private static final String GRADIENT_START = "#3b82f6";
    private static final String GRADIENT_END = "#7c3aed";
    private static final String BACKGROUND_DARK = "#020617";
    private static final String CARD_DARK = "#0f172a";
    private static final String BORDER_DARK = "#1e293b";
    private static final String TEXT_MAIN = "#f8fafc";
    private static final String TEXT_MUTED = "#94a3b8";

    private static String wrapInBaseTemplate(String title, String content) {
        return "<!DOCTYPE html>" +
               "<html>" +
               "<head>" +
               "<meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
               "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;700;900&display=swap' rel='stylesheet'>" +
               "<style>" +
               "body { font-family: 'Inter', -apple-system, sans-serif; line-height: 1.6; color: " + TEXT_MAIN + "; margin: 0; padding: 0; background-color: " + BACKGROUND_DARK + "; }" +
               ".wrapper { max-width: 600px; margin: 30px auto; background: " + CARD_DARK + "; border: 1px solid " + BORDER_DARK + "; border-radius: 32px; overflow: hidden; box-shadow: 0 40px 100px rgba(0,0,0,0.5); }" +
               ".header { background: linear-gradient(135deg, " + GRADIENT_START + ", " + GRADIENT_END + "); padding: 60px 40px; text-align: center; color: #ffffff; }" +
               ".header h1 { margin: 0; font-size: 42px; font-weight: 900; letter-spacing: -2px; text-transform: uppercase; font-style: italic; }" +
               ".content { padding: 50px 40px; }" +
               ".footer { text-align: center; padding: 40px; background: rgba(0,0,0,0.2); border-top: 1px solid " + BORDER_DARK + "; color: " + TEXT_MUTED + "; font-size: 12px; letter-spacing: 1px; text-transform: uppercase; }" +
               ".btn { display: inline-block; background: linear-gradient(to right, " + GRADIENT_START + ", " + GRADIENT_END + "); color: #ffffff !important; padding: 18px 36px; border-radius: 16px; text-decoration: none; font-weight: 900; font-size: 14px; text-transform: uppercase; letter-spacing: 2px; margin: 30px 0; box-shadow: 0 20px 40px rgba(59, 130, 246, 0.3); }" +
               ".card { background: rgba(30, 41, 59, 0.5); border: 1px solid " + BORDER_DARK + "; border-radius: 24px; padding: 30px; margin: 30px 0; }" +
               ".feature-list { margin: 0; padding: 0; list-style: none; }" +
               ".feature-item { display: flex; align-items: center; margin-bottom: 12px; color: " + TEXT_MAIN + "; font-weight: 500; font-size: 14px; }" +
               ".feature-icon { color: " + PRIMARY_COLOR + "; margin-right: 12px; font-weight: 900; }" +
               "h2 { font-weight: 900; letter-spacing: -1px; margin-top: 0; }" +
               "strong { color: " + PRIMARY_COLOR + "; }" +
               "</style>" +
               "</head>" +
               "<body>" +
               "<div class='wrapper'>" +
               "<div class='header'>" +
               "<h1>VaniAI</h1>" +
               "<div style='height: 2px; width: 40px; background: rgba(255,255,255,0.3); margin: 15px auto;'></div>" +
               "<p style='opacity: 0.9; font-size: 12px; font-weight: 900; letter-spacing: 3px; text-transform: uppercase;'>" + title + "</p>" +
               "</div>" +
               "<div class='content'>" +
               content +
               "</div>" +
               "<div class='footer'>" +
               "<p>&copy; 2026 VaniAI Intelligence Studio</p>" +
               "<p style='opacity: 0.5; margin-top: 5px;'>Neural Ops Center • San Francisco • CA</p>" +
               "</div>" +
               "</div>" +
               "</body>" +
               "</html>";
    }

    public static String getWelcomeEmailHtml(User user) {
        String content = "<h2>Welcome to the Studio, Architect! \u2728</h2>" +
                "<p>Your laboratory for AI-powered social media automation is officially operational. We're scaling your organic reach starting now.</p>" +
                "<div class='card'>" +
                "<h4 style='margin-top: 0; text-transform: uppercase; letter-spacing: 1px; color: " + PRIMARY_COLOR + ";'>Neural Core Activated:</h4>" +
                "<ul class='feature-list'>" +
                "<li class='feature-item'><span class='feature-icon'>\u27A1</span> " + user.getMonthlyCredits() + " Priority AI Credits</li>" +
                "<li class='feature-item'><span class='feature-icon'>\u27A1</span> Infinite Content Variations</li>" +
                "<li class='feature-item'><span class='feature-icon'>\u27A1</span> High-Performance Visual Pipeline</li>" +
                "</ul>" +
                "</div>" +
                "<p>Ready to deploy your first viral transmission?</p>" +
                "<center><a href='http://localhost:5173' class='btn'>Launch Dashboard</a></center>";
        
        return wrapInBaseTemplate("SYSTEM IDENTIFICATION SUCCESSFUL", content);
    }

    public static String getPaymentReceiptHtml(PaymentOrder order) {
        String name = order.getUser().getFullName() != null ? order.getUser().getFullName() : "Valued Customer";
        String date = order.getCompletedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        
        String content = "<h2>Transaction Authorized, " + name + "! \u2705</h2>" +
                "<p>Your subscription core has been updated. The <strong>" + order.getTargetTier() + "</strong> plan is now active on your account.</p>" +
                "<div class='card'>" +
                "<div style='display: flex; justify-content: space-between; margin-bottom: 8px;'><span style='color: " + TEXT_MUTED + ";'>Order ID:</span> <span style='font-weight: 700;'>" + order.getRazorpayOrderId() + "</span></div>" +
                "<div style='display: flex; justify-content: space-between; margin-bottom: 8px;'><span style='color: " + TEXT_MUTED + ";'>Payment ID:</span> <span style='font-weight: 700;'>" + order.getRazorpayPaymentId() + "</span></div>" +
                "<div style='display: flex; justify-content: space-between; margin-bottom: 8px;'><span style='color: " + TEXT_MUTED + ";'>Timestamp:</span> <span style='font-weight: 700;'>" + date + "</span></div>" +
                "<div style='border-top: 1px dashed #cbd5e1; margin-top: 15px; padding-top: 15px; display: flex; justify-content: space-between;'>" +
                "<span style='font-weight: 800; font-size: 18px;'>TOTAL PAID:</span> <span style='font-weight: 800; font-size: 22px; color: " + PRIMARY_COLOR + ";'>\u20B9 " + (order.getAmount() / 100.0) + "</span></div>" +
                "</div>" +
                "<p>Your credits have been reset and your daily limits increased for peak throughput.</p>" +
                "<center><a href='http://localhost:5173/settings' class='btn'>Manage Subscription</a></center>";
        
        return wrapInBaseTemplate("INVOICE GENERATED", content);
    }

    public static String getLowCreditAlertHtml(User user) {
        String content = "<h2>Neural Capacity Warning \u26A0\uFE0F</h2>" +
                "<p>Your VaniAI credit balance is critically low. To maintain your content deployment schedule without interruption, please recharge your neural engine.</p>" +
                "<div class='card' style='text-align: center; border-color: #ef4444;'>" +
                "<p style='margin: 0; color: " + TEXT_MUTED + "; text-transform: uppercase; font-size: 10px; letter-spacing: 2px;'>Remaining Capacity:</p>" +
                "<h1 style='font-size: 64px; margin: 10px 0; color: #ef4444; font-weight: 900;'>" + user.getMonthlyCredits() + "</h1>" +
                "<p style='font-size: 12px; font-weight: 700; color: #ef4444;'>CREDITS REMAINING</p>" +
                "</div>" +
                "<p>Upgrade to **Pro** for ultra-HD generation and priority GPU access.</p>" +
                "<center><a href='http://localhost:5173/pricing' class='btn'>Refill Pipeline</a></center>";
        
        return wrapInBaseTemplate("CRITICAL CAPACITY ALERT", content);
    }

    public static String getPlanExpiredHtml(User user) {
        String content = "<h2>Subscription Expired \u231b</h2>" +
                "<p>Your premium tier access has expired and your account has been automatically shifted to the <strong>FREE</strong> tier.</p>" +
                "<div class='card'>" +
                "<p>To restore your high-limit generating capacity and continue using premium features, please renew your subscription.</p>" +
                "</div>" +
                "<center><a href='http://localhost:5173/pricing' class='btn'>Renew Subscription</a></center>";
        
        return wrapInBaseTemplate("SYSTEM DOWNGRADE NOTICE", content);
    }
}
